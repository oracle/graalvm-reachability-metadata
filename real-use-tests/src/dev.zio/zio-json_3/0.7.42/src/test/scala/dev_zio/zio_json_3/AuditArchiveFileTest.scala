/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_json_3

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import zio.{Chunk, Runtime, Unsafe, ZIO}
import zio.json.*
import zio.stream.ZStream

/** Exercises JSON file I/O helpers for exporting and restoring a security audit archive. */
class AuditArchiveFileTest {
  final case class AuditEvent(sequence: Long, actor: String, action: String, detail: String)

  given JsonCodec[AuditEvent] = DeriveJsonCodec.gen[AuditEvent]

  private def run[A](effect: ZIO[Any, Throwable, A]): A = {
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
  }

  @Test
  @Timeout(60)
  def exportArchiveAndRestoreSecurityHistory(@TempDir directory: Path): Unit = {
    val events: Chunk[AuditEvent] = Chunk(
      AuditEvent(101L, "Jos\u00e9", "login", "Caf\u00e9 workstation"),
      AuditEvent(102L, "operator", "revoke", "session \"guest\"\nrevoked"),
      AuditEvent(103L, "operator", "logout", "complete")
    )
    val archive: Path = directory.resolve("security-audit.jsonl")
    run(writeJsonLinesAs(archive, ZStream.fromChunk(events)))

    // The exported artifact is consumed by both line-oriented tools and the application.
    val document: String = Files.readString(archive, UTF_8)
    val lines: List[String] = document.linesIterator.toList
    assertEquals(3, lines.size, "An embedded newline must not split an audit event")
    assertEquals(events.toList.map(Right(_)), lines.map(_.fromJson[AuditEvent]))
    assertTrue(document.contains("Jos\u00e9"), "The archive must preserve UTF-8 actor names")

    val restored: Chunk[AuditEvent] = run(readJsonLinesAs[AuditEvent](archive).runCollect)
    assertEquals(events, restored, "Restore must retain event order and all message characters")
    val revocations: List[(Long, String)] = restored.toList
      .filter(_.action == "revoke").map(event => (event.sequence, event.detail))
    assertEquals(List((102L, "session \"guest\"\nrevoked")), revocations)

    // Re-export the restored history to a second archive, as a migration job would.
    val migrated: Path = directory.resolve("migrated-audit.jsonl")
    run(writeJsonLinesAs(migrated, ZStream.fromChunk(restored)))
    assertEquals(events, run(readJsonLinesAs[AuditEvent](migrated).runCollect))

    val corrupt: Path = directory.resolve("corrupt-audit.jsonl")
    Files.writeString(
      corrupt,
      lines.head + "\n" +
        """{"sequence":"lost","actor":"operator","action":"revoke","detail":"invalid"}""",
      UTF_8
    )
    val rejected: Either[Throwable, Chunk[AuditEvent]] =
      run(readJsonLinesAs[AuditEvent](corrupt).runCollect.either)
    assertTrue(rejected.isLeft, "A damaged archive must not be reported as a complete restore")
    val error: Throwable = rejected.swap.toOption.get
    assertTrue(error.getMessage.contains("sequence"), error.toString)
  }
}
