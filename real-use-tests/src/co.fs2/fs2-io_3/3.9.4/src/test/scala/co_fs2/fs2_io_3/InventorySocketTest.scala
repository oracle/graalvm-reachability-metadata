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
import com.comcast.ip4s.*
import fs2.Stream
import fs2.io.net.Network
import fs2.text
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

import scala.concurrent.duration.*

/** Exercises TCP sockets by querying an inventory service over a line-oriented connection. */
class InventorySocketTest {
  @Test
  @Timeout(60)
  def queryStockOverTcp(): Unit = {
    val stock: Map[String, Int] = Map("SKU-42" -> 17, "SKU-7" -> 3)
    val requested: List[String] = List("SKU-42", "SKU-7", "SKU-MISSING")
    val network: Network[IO] = Network[IO]

    network.serverResource(address = Some(ip"127.0.0.1")).use { case (address, connections) =>
      val serve: IO[List[String]] = connections.take(1).flatMap { socket =>
        socket.reads
          .through(text.utf8.decode)
          .through(text.lines)
          .filter(_.nonEmpty)
          .evalMap { sku =>
            val response: String = s"$sku,${stock.getOrElse(sku, 0)}\n"
            Stream.emit(response)
              .through(text.utf8.encode)
              .through(socket.writes)
              .compile.drain.as(sku)
          }
      }.compile.toList

      val lookup: IO[List[String]] = network.client(address).use { socket =>
        for {
          _ <- Stream.emits(requested).map(sku => s"$sku\n")
            .through(text.utf8.encode)
            .through(socket.writes)
            .compile.drain
          _ <- socket.endOfOutput
          responses <- socket.reads
            .through(text.utf8.decode)
            .through(text.lines)
            .filter(_.nonEmpty)
            .compile.toList
        } yield responses
      }

      serve.both(lookup).timeout(20.seconds).flatMap { case (received, responses) =>
        IO {
          assertEquals(requested, received, "The service must receive every requested product in order")
          assertEquals(List("SKU-42,17", "SKU-7,3", "SKU-MISSING,0"), responses)
        }
      }
    }.unsafeRunSync()
  }
}
