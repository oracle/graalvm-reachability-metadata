/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_fs2.fs2_io_3

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import fs2.{Chunk, Stream, text}
import fs2.io.file.{FileHandle, Files, Flag, Flags, Path, ReadCursor, WriteCursor}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

import java.nio.charset.StandardCharsets.UTF_8
import scala.concurrent.duration.*

/** Exercises advisory file locking by reserving stock in a ledger shared with cooperating warehouse tools. */
class LockedStockLedgerTest {
  private case class InsufficientStock(requested: Int, available: Int)
      extends RuntimeException(s"Requested $requested units but only $available remain")

  private def readBalance(file: FileHandle[IO]): IO[String] =
    ReadCursor(file, 0L).readAll(16).void.stream.through(text.utf8.decode).compile.string

  private def reserve(file: FileHandle[IO], quantity: Int): IO[Int] =
    // All cooperating tools hold the same whole-file lock across read, validation, and durable write.
    Resource.make(file.lock)(file.unlock).use { _ =>
      for {
        contents <- readBalance(file)
        available <- IO(contents.trim.toInt)
        remaining <- if (quantity > available) IO.raiseError[Int](InsufficientStock(quantity, available))
          else IO.pure(available - quantity)
        bytes = Chunk.array(s"$remaining\n".getBytes(UTF_8))
        _ <- WriteCursor(file, 0L).write(bytes)
        _ <- file.truncate(bytes.size.toLong)
        _ <- file.force(metaData = true)
      } yield remaining
    }

  @Test
  @Timeout(60)
  def rejectOverbookingAndReleaseLockForTheNextReservation(): Unit = {
    val files: Files[IO] = Files[IO]

    files.tempDirectory.use { directory =>
      val ledger: Path = directory / "SKU-42.stock"
      for {
        _ <- Stream.emit("120\n").covary[IO].through(files.writeUtf8(ledger)).compile.drain
        _ <- files.open(ledger, Flags(Flag.Read, Flag.Write)).use { file =>
          for {
            first <- reserve(file, 25)
            afterFirst <- readBalance(file)
            rejected <- reserve(file, 100).attempt
            afterRejection <- readBalance(file)
            _ <- IO {
              assertEquals(95, first, "An accepted reservation must return the remaining stock")
              assertEquals("95\n", afterFirst, "Replacing a longer balance must leave no stale digits")
              assertEquals(Left(InsufficientStock(100, 95)), rejected,
                "Overbooking must fail with the available quantity, not mutate the ledger")
              assertEquals("95\n", afterRejection, "A rejected reservation must leave stock unchanged")
            }
            // Keep the channel open: only the reservation's finalizer can release its lock.
            next <- reserve(file, 90)
            _ <- IO(assertEquals(5, next,
              "A business-rule failure must release the lock so the next request can proceed"))
          } yield ()
        }
        // A later warehouse session must see the committed balance, not an in-memory result.
        saved <- files.readUtf8(ledger).compile.string
        size <- files.size(ledger)
        _ <- IO {
          assertEquals("5\n", saved, "Accepted reservations must persist across closing the ledger")
          assertEquals(2L, size, "The ledger must contain exactly the latest balance and newline")
        }
      } yield ()
    }.timeout(30.seconds).unsafeRunSync()
  }
}
