/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_fs2.fs2_core_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.{Chunk, Stream}
import fs2.compression.Compression
import org.junit.jupiter.api.Assertions.{assertArrayEquals, assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.zip.GZIPInputStream
import scala.concurrent.duration.*
import scala.util.Using

/** Exercises FS2 gzip compression to archive and restore a chunked application audit log. */
class AuditGzipArchiveTest {
  private case class RestoredArchive(
      fileName: Option[String],
      modificationTime: Option[Instant],
      comment: Option[String],
      content: Vector[Byte]
  )

  @Test
  @Timeout(60)
  def archivesAuditRecordsWithPortableGzipMetadata(): Unit = {
    val records: String = (1 to 200)
      .map(id => s"event=$id action=order-approved tenant=north\n")
      .mkString
    val original: Array[Byte] = records.getBytes(StandardCharsets.UTF_8)
    val modified: Instant = Instant.parse("2025-01-15T12:00:00Z")
    val fileName: String = "audit-2025-01-15.log"
    val comment: String = "Daily order audit export"

    val compressed: Vector[Byte] = Stream
      .emits(original.grouped(113).toVector)
      .covary[IO]
      .flatMap(bytes => Stream.chunk(Chunk.array(bytes)))
      .through(Compression[IO].gzip(
        bufferSize = 1024,
        modificationTime = Some(modified),
        fileName = Some(fileName),
        comment = Some(comment)
      ))
      .compile
      .toVector
      .timeout(20.seconds)
      .unsafeRunSync()

    assertTrue(compressed.length < original.length, "The repetitive audit log should compress")
    val portableContent: Array[Byte] = Using.resource(
      new GZIPInputStream(new ByteArrayInputStream(compressed.toArray))
    )(_.readAllBytes())
    assertArrayEquals(original, portableContent)

    val restored: Vector[RestoredArchive] = Stream
      .emits(compressed)
      .covary[IO]
      .chunkN(7)
      .unchunks
      .through(Compression[IO].gunzip())
      .evalMap { archive =>
        archive.content.compile.toVector.map { bytes =>
          RestoredArchive(archive.fileName, archive.modificationTime, archive.comment, bytes)
        }
      }
      .compile
      .toVector
      .timeout(20.seconds)
      .unsafeRunSync()

    assertEquals(1, restored.size)
    assertEquals(Some(fileName), restored.head.fileName)
    assertEquals(Some(modified), restored.head.modificationTime)
    assertEquals(Some(comment), restored.head.comment)
    assertArrayEquals(original, restored.head.content.toArray)
  }
}
