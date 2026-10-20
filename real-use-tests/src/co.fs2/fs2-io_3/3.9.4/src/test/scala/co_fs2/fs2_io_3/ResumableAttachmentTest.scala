/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_fs2.fs2_io_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import fs2.Chunk
import fs2.io.file.{FileHandle, Files, Flag, Flags, Path, ReadCursor, WriteCursor}
import fs2.text
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

import java.nio.charset.StandardCharsets.UTF_8
import scala.concurrent.duration.*

/** Exercises positional file handles by resuming an attachment upload with out-of-order and retried parts. */
class ResumableAttachmentTest {
  private case class UploadPart(offset: Long, bytes: Chunk[Byte]) {
    def end: Long = offset + bytes.size.toLong
  }

  private def store(file: FileHandle[IO], part: UploadPart): IO[Unit] =
    WriteCursor(file, part.offset).write(part.bytes).void

  private def readPart(file: FileHandle[IO], part: UploadPart): IO[String] =
    ReadCursor(file, part.offset).readUntil(5, part.end).void.stream
      .through(text.utf8.decode).compile.string

  @Test
  @Timeout(60)
  def resumeAndPublishAttachmentWithoutDuplicatingRetriedBytes(): Unit = {
    val prefix: String = "Delivery receipt: shipment-81\n"
    val body: String = "SKU-42: 12 cartons\nSKU-7: 5 cartons\n"
    val suffix: String = "Signed by: warehouse-north\n"
    val first: UploadPart = UploadPart(0L, Chunk.array(prefix.getBytes(UTF_8)))
    val middle: UploadPart = UploadPart(first.end, Chunk.array(body.getBytes(UTF_8)))
    val last: UploadPart = UploadPart(middle.end, Chunk.array(suffix.getBytes(UTF_8)))
    val files: Files[IO] = Files[IO]

    files.tempDirectory.use { directory =>
      val pending: Path = directory / "receipt.upload"
      val published: Path = directory / "receipt.txt"
      for {
        // Parts arrive out of order, then the upload session closes before the middle arrives.
        _ <- files.open(pending, Flags(Flag.Read, Flag.Write, Flag.CreateNew)).use { file =>
          for {
            _ <- store(file, last)
            _ <- store(file, first)
            _ <- file.force(metaData = true)
          } yield ()
        }
        // Reopening without truncation preserves the parts acknowledged by the earlier session.
        _ <- files.open(pending, Flags(Flag.Read, Flag.Write)).use { file =>
          for {
            savedPrefix <- readPart(file, first)
            savedSuffix <- readPart(file, last)
            _ <- IO {
              assertEquals(prefix, savedPrefix, "The first acknowledged part must survive reconnection")
              assertEquals(suffix, savedSuffix, "The out-of-order final part must survive reconnection")
            }
            _ <- store(file, middle)
            // The client retries after losing an acknowledgment; the same offset must be overwritten.
            _ <- store(file, middle)
            _ <- file.force(metaData = true)
            savedMiddle <- readPart(file, middle)
            size <- file.size
            _ <- IO {
              assertEquals(body, savedMiddle, "The completed part must be available for verification")
              assertEquals(last.end, size, "Retrying a part must not append duplicate bytes")
            }
          } yield ()
        }
        _ <- files.move(pending, published)
        downloaded <- files.readAll(published, 7, Flags.Read)
          .through(text.utf8.decode).compile.string
        _ <- IO(assertEquals(prefix + body + suffix, downloaded,
          "Publishing must expose the exact attachment with no gaps or duplicated parts"))
      } yield ()
    }.timeout(30.seconds).unsafeRunSync()
  }
}
