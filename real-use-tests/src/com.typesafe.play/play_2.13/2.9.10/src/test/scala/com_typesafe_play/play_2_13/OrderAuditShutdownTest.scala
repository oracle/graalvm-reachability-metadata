/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_2_13

import org.junit.jupiter.api.Assertions.{assertEquals, assertThrows}
import org.junit.jupiter.api.{Test, Timeout}
import play.api.inject.{ApplicationLifecycle, DefaultApplicationLifecycle}

import java.io.{BufferedWriter, IOException}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

/** Exercises Play application lifecycle hooks to drain buffered order audits before closing their journal. */
@Timeout(60)
class OrderAuditShutdownTest {
  class AuditJournal(path: Path, lifecycle: ApplicationLifecycle)(implicit executionContext: ExecutionContext)
      extends AutoCloseable {
    private val writer: BufferedWriter = Files.newBufferedWriter(path, UTF_8)

    // Registered first: the resource stays available until its dependent service has stopped.
    lifecycle.addStopHook(() => Future { close() })

    def append(line: String): Unit = {
      writer.write(line)
      writer.write("\n")
    }

    override def close(): Unit = writer.close()
  }

  class BufferedOrderAudit(journal: AuditJournal, lifecycle: ApplicationLifecycle)(
      implicit executionContext: ExecutionContext) {
    private val pending: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue[String]()

    lifecycle.addStopHook(() => Future {
      Iterator.continually(pending.poll()).takeWhile(_ != null).foreach(journal.append)
      journal.append("END OF AUDIT")
    })

    def record(order: String, state: String): Unit = {
      pending.add(s"$order: $state")
    }
  }

  @Test
  def awaitsTheAuditDrainBeforeClosingTheJournal(): Unit = {
    implicit val executionContext: ExecutionContext = ExecutionContext.global
    val lifecycle: DefaultApplicationLifecycle = new DefaultApplicationLifecycle()
    val path: Path = Files.createTempFile("order-audit-", ".log")
    val journal: AuditJournal = new AuditJournal(path, lifecycle)
    try {
      val audit: BufferedOrderAudit = new BufferedOrderAudit(journal, lifecycle)
      audit.record("ORD-101", "accepted")
      audit.record("ORD-102", "accepted")
      audit.record("ORD-101", "dispatched")

      // These records are still queued, not yet persisted by the application's audit service.
      assertEquals("", Files.readString(path, UTF_8))
      Await.result(lifecycle.stop(), 10.seconds)

      // Completion includes both the asynchronous drain and the journal's final close/flush.
      assertEquals(
        "ORD-101: accepted\nORD-102: accepted\nORD-101: dispatched\nEND OF AUDIT\n",
        Files.readString(path, UTF_8)
      )
      assertThrows(classOf[IOException], () => journal.append("late record"))
      assertEquals(
        "ORD-101: accepted\nORD-102: accepted\nORD-101: dispatched\nEND OF AUDIT\n",
        Files.readString(path, UTF_8)
      )
    } finally {
      try journal.close()
      finally Files.deleteIfExists(path)
    }
  }
}
