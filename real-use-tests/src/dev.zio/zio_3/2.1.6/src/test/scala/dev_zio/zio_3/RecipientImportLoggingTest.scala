/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_3

import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import scala.jdk.CollectionConverters.*
import zio.{Cause, Duration, FiberId, FiberRefs, LogLevel, LogSpan, Runtime, Trace, UIO, Unsafe, ZIO, ZLogger}

/** Exercises ZIO structured logging by auditing recipient imports with scoped annotations and severity filtering. */
class RecipientImportLoggingTest {
  private case class Recipient(id: String, email: String)
  private case class AuditLine(level: LogLevel, message: String, annotations: Map[String, String])

  private class AuditLogger(output: ConcurrentLinkedQueue[AuditLine]) extends ZLogger[String, Unit] {
    override def apply(
        trace: Trace,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => String,
        cause: Cause[Any],
        context: FiberRefs,
        spans: List[LogSpan],
        annotations: Map[String, String]
    ): Unit = {
      output.add(AuditLine(logLevel, message(), annotations))
      ()
    }
  }

  private def importRecipients(batchId: String, rows: List[Recipient]): UIO[List[Recipient]] =
    ZIO.logAnnotate("batch", batchId) {
      for {
        outcomes <- ZIO.foreach(rows) { row =>
          ZIO.logAnnotate("recipient", row.id) {
            val normalized: String = row.email.trim.toLowerCase(Locale.ROOT)
            ZIO.logDebug("Normalizing recipient") *> (
              if (!normalized.contains("@"))
                ZIO.logWarning("Recipient rejected: invalid email").as(Option.empty[Recipient])
              else
                ZIO.logInfo("Recipient accepted").as(Some(row.copy(email = normalized)))
            )
          }
        }
        accepted = outcomes.flatten
        _ <- ZIO.logInfo(s"Import complete: ${accepted.size} accepted, ${rows.size - accepted.size} rejected")
      } yield accepted
    }

  @Test
  @Timeout(60)
  def auditRecordsCarryOnlyTheirOwnScopedContextAndExcludeDebugMessages(): Unit = {
    val output: ConcurrentLinkedQueue[AuditLine] = new ConcurrentLinkedQueue[AuditLine]()
    val logger: ZLogger[String, Option[Unit]] = new AuditLogger(output).filterLogLevel(_ >= LogLevel.Info)
    val program: UIO[List[Recipient]] = for {
      accepted <- importRecipients(
        "BATCH-42",
        List(
          Recipient("CUSTOMER-1", " Alice@Example.Test "),
          Recipient("CUSTOMER-2", "missing-address"),
          Recipient("CUSTOMER-3", "bob@example.test")
        )
      )
      // The worker's next activity must not retain either batch or recipient annotations.
      _ <- ZIO.logInfo("Import worker idle")
    } yield accepted

    val accepted: List[Recipient] = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(
        program.provide(Runtime.removeDefaultLoggers >>> Runtime.addLogger(logger))
          .timeoutFail(new IllegalStateException("Recipient import timed out"))(Duration.fromSeconds(30))
      ).getOrThrowFiberFailure()
    }

    assertEquals(
      List(Recipient("CUSTOMER-1", "alice@example.test"), Recipient("CUSTOMER-3", "bob@example.test")),
      accepted
    )
    assertEquals(
      Vector(
        AuditLine(LogLevel.Info, "Recipient accepted", Map("batch" -> "BATCH-42", "recipient" -> "CUSTOMER-1")),
        AuditLine(
          LogLevel.Warning,
          "Recipient rejected: invalid email",
          Map("batch" -> "BATCH-42", "recipient" -> "CUSTOMER-2")
        ),
        AuditLine(LogLevel.Info, "Recipient accepted", Map("batch" -> "BATCH-42", "recipient" -> "CUSTOMER-3")),
        AuditLine(LogLevel.Info, "Import complete: 2 accepted, 1 rejected", Map("batch" -> "BATCH-42")),
        AuditLine(LogLevel.Info, "Import worker idle", Map.empty[String, String])
      ),
      output.iterator().asScala.toVector
    )
  }
}
