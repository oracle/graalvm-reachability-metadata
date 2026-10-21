/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_fs2.fs2_core_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.{Chunk, Pull, Stream}
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}

import java.io.EOFException
import java.nio.charset.StandardCharsets
import scala.concurrent.duration.*

/** Exercises FS2 Pull binary framing to import length-prefixed warehouse scanner messages. */
class ScannerFrameImportTest {
  private case class Scan(parcel: String, dock: String, quantity: Int)

  // The scanner protocol uses a two-byte, unsigned big-endian payload length.
  private def frame(payload: String): Vector[Byte] = {
    val bytes: Vector[Byte] = payload.getBytes(StandardCharsets.UTF_8).toVector
    Vector((bytes.size >>> 8).toByte, bytes.size.toByte) ++ bytes
  }

  private def fragmented(bytes: Vector[Byte]): Stream[IO, Byte] = Stream
    .emits(bytes.grouped(3).toVector)
    .covary[IO]
    .flatMap(fragment => Stream.chunk(Chunk.vector(fragment)))

  private def decode(input: Stream[IO, Byte]): Pull[IO, Scan, Unit] =
    input.pull.unconsN(2, allowFewer = true).flatMap {
      case None => Pull.done
      case Some((header, _)) if header.size != 2 =>
        Pull.raiseError[IO](new EOFException("Incomplete scanner header"))
      case Some((header, remainder)) =>
        val length: Int = ((header(0) & 0xff) << 8) | (header(1) & 0xff)
        if (length == 0) {
          Pull.raiseError[IO](new IllegalArgumentException("Empty scanner message"))
        } else {
          remainder.pull.unconsN(length, allowFewer = true).flatMap {
            case Some((payload, tail)) if payload.size == length =>
              val fields: Array[String] =
                new String(payload.toArray, StandardCharsets.UTF_8).split("\\|", -1)
              val scan: Scan = Scan(fields(0), fields(1), fields(2).toInt)
              Pull.output1(scan) >> decode(tail)
            case _ =>
              Pull.raiseError[IO](new EOFException("Incomplete scanner payload"))
          }
        }
    }

  @Test
  @Timeout(60)
  def importsCompleteFramesAndRejectsAnInterruptedTransfer(): Unit = {
    val messages: Vector[String] = Vector(
      "box-100|north|4",
      "box-101|south|2",
      "caf\u00e9-102|north|3"
    )
    val wire: Vector[Byte] = messages.flatMap(frame)
    val expected: Vector[Scan] = Vector(
      Scan("box-100", "north", 4),
      Scan("box-101", "south", 2),
      Scan("caf\u00e9-102", "north", 3)
    )

    val scans: Vector[Scan] = decode(fragmented(wire))
      .stream.compile.toVector.timeout(20.seconds).unsafeRunSync()

    assertEquals(expected, scans)
    assertEquals(Map("north" -> 7, "south" -> 2),
      scans.groupMapReduce(_.dock)(_.quantity)(_ + _))

    // Completed frames remain visible, but an interrupted final frame must not be imported.
    val interrupted: Vector[Either[Throwable, Scan]] = decode(fragmented(wire.dropRight(1)))
      .stream.attempt.compile.toVector.timeout(20.seconds).unsafeRunSync()

    assertEquals(expected.take(2).map(scan => Right(scan)), interrupted.dropRight(1))
    assertTrue(interrupted.last.left.exists(error =>
      error.isInstanceOf[EOFException] && error.getMessage == "Incomplete scanner payload"))
  }
}
