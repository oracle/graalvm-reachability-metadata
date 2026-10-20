/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_fs2.fs2_io_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Stream
import fs2.io.file.{Files, Path, Watcher}
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse}
import org.junit.jupiter.api.{Test, Timeout}

import scala.concurrent.duration.*

/** Exercises directory watching by importing and archiving a newly delivered order batch. */
class OrderInboxWatcherTest {
  private case class Order(id: String, sku: String, quantity: Int)

  @Test
  @Timeout(60)
  def importOrderBatchWhenItArrives(): Unit = {
    val files: Files[IO] = Files[IO]
    val rows: List[String] = List("ORD-81,SKU-42,4", "ORD-82,SKU-7,9")
    val expected: List[Order] = List(Order("ORD-81", "SKU-42", 4), Order("ORD-82", "SKU-7", 9))

    files.tempDirectory.use { directory =>
      val inbox: Path = directory / "inbox"
      val processed: Path = directory / "processed"
      val staging: Path = directory / "orders.part"
      val delivery: Path = inbox / "orders.csv"
      val archived: Path = processed / "orders.csv"

      for {
        _ <- files.createDirectory(inbox)
        _ <- files.createDirectory(processed)
        _ <- Stream.emits(rows).covary[IO].through(files.writeUtf8Lines(staging)).compile.drain
        _ <- Watcher.default[IO].use { watcher =>
          for {
            // Registration completes before publication; events remain queued until consumed.
            _ <- watcher.register(inbox, types = Seq(Watcher.EventType.Created))
            // Publish only the completed file, never a file still being written.
            _ <- files.move(staging, delivery)
            imported <- watcher.events(10.seconds)
              .collect { case Watcher.Event.Created(path, _) if path == delivery => path }
              .take(1)
              .evalMap { path =>
                for {
                  orders <- files.readUtf8Lines(path).filter(_.nonEmpty).map { row =>
                    val fields: Array[String] = row.split(",")
                    Order(fields(0), fields(1), fields(2).toInt)
                  }.compile.toList
                  _ <- files.move(path, archived)
                } yield (path, orders)
              }.compile.toList.timeout(20.seconds)
            archivedRows <- files.readUtf8Lines(archived).filter(_.nonEmpty).compile.toList
            pending <- files.exists(delivery)
            _ <- IO {
              assertEquals(List(delivery -> expected), imported, "The arrival must trigger a complete batch import")
              assertEquals(13, imported.flatMap(_._2).map(_.quantity).sum)
              assertEquals(rows, archivedRows, "The processed batch must remain available for audit")
              assertFalse(pending, "An imported batch must no longer be pending in the inbox")
            }
          } yield ()
        }
      } yield ()
    }.timeout(30.seconds).unsafeRunSync()
  }
}
