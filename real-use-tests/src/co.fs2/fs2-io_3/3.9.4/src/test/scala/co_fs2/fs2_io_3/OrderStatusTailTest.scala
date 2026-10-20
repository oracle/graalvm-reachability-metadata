/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_fs2.fs2_io_3

import cats.effect.{Deferred, IO}
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import fs2.{Chunk, Stream, text}
import fs2.io.file.{Files, Flags, Path}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

import java.nio.charset.StandardCharsets.UTF_8
import scala.concurrent.duration.*

/** Exercises file tailing by shipping appended order-status records from a saved byte checkpoint. */
class OrderStatusTailTest {
  private case class Status(order: String, state: String, location: String)

  private def append(files: Files[IO], log: Path, bytes: Array[Byte]): IO[Unit] =
    Stream.chunk(Chunk.array(bytes)).covary[IO]
      .through(files.writeAll(log, Flags.Append)).compile.drain

  private def parse(line: String): Status = {
    val fields: Array[String] = line.split(",", -1)
    require(fields.length == 3, s"Invalid order status: $line")
    Status(fields(0), fields(1), fields(2))
  }

  @Test
  @Timeout(60)
  def resumeShippingAndWaitForNewCompleteRecords(): Unit = {
    val files: Files[IO] = Files[IO]
    val historical: String = "ORD-40,delivered,Lyon\n"
    val first: String = "ORD-81,packed,Paris\n"
    val remainder: String = "ORD-81,shipped,Orl\u00e9ans\nORD-82,packed,Lyon\n"
    val remainingBytes: Array[Byte] = remainder.getBytes(UTF_8)
    // Split the producer's writes inside a UTF-8 character, not at a record boundary.
    val split: Int = "ORD-81,shipped,Orl".getBytes(UTF_8).length + 1
    val expected: List[Status] = List(
      Status("ORD-81", "packed", "Paris"),
      Status("ORD-81", "shipped", "Orl\u00e9ans"),
      Status("ORD-82", "packed", "Lyon")
    )

    files.tempDirectory.use { directory =>
      val log: Path = directory / "order-status.log"
      val checkpointFile: Path = directory / "shipper.offset"
      for {
        _ <- Stream.emit(historical).covary[IO].through(files.writeUtf8(log)).compile.drain
        previousOffset <- files.size(log)
        _ <- Stream.emit(previousOffset.toString).covary[IO]
          .through(files.writeUtf8(checkpointFile)).compile.drain
        savedOffset <- files.readUtf8(checkpointFile).compile.string.map(_.toLong)
        firstReceived <- Deferred[IO, Unit]
        consume: IO[List[Status]] = files.tail(log, chunkSize = 3, offset = savedOffset)
          .through(text.utf8.decode)
          .through(text.lines)
          .filter(_.nonEmpty)
          .map(parse)
          .evalTap { status =>
            if (status == expected.head) firstReceived.complete(()).void else IO.unit
          }
          .take(expected.size.toLong)
          .compile.toList
        produce: IO[Unit] = for {
          _ <- append(files, log, first.getBytes(UTF_8))
          // The consumer must observe the first append before the producer continues.
          _ <- firstReceived.get
          _ <- append(files, log, remainingBytes.take(split))
          _ <- append(files, log, remainingBytes.drop(split))
        } yield ()
        result <- (consume, produce).parTupled.timeout(20.seconds)
        finalOffset <- files.size(log)
        _ <- Stream.emit(finalOffset.toString).covary[IO]
          .through(files.writeUtf8(checkpointFile)).compile.drain
        committedOffset <- files.readUtf8(checkpointFile).compile.string.map(_.toLong)
        _ <- IO {
          assertEquals(expected, result._1,
            "Resume must omit historical records and deliver complete appended records in order")
          assertEquals(historical.getBytes(UTF_8).length.toLong, savedOffset)
          assertEquals((historical + first + remainder).getBytes(UTF_8).length.toLong, committedOffset,
            "The next restart must resume after all shipped UTF-8 bytes")
        }
      } yield ()
    }.timeout(30.seconds).unsafeRunSync()
  }
}
