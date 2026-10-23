/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_profunktor.redis4cats_core_3

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.effect.Log.NoOp._
import dev.profunktor.redis4cats.tx.TxStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import redis.embedded.RedisServer

import java.net.ServerSocket
import scala.concurrent.duration._

/** Exercises command pipelining to receive a shipment and return updated stock for each item. */
class PipelinedShipmentReceivingTest {
  final case class ShipmentLine(sku: String, openingStock: Long, delivered: Long)

  @Test
  @Timeout(60)
  def shipmentReceiptMatchesPersistedStockAndNormalOrdersCanResume(): Unit = {
    val shipment: List[ShipmentLine] = List(
      ShipmentLine("coffee", 10L, 5L),
      ShipmentLine("tea", 2L, 8L),
      ShipmentLine("cocoa", 0L, 3L)
    )
    val expectedReceipt: Map[String, Long] = Map("coffee" -> 15L, "tea" -> 10L, "cocoa" -> 3L)
    val program: IO[Unit] = serverResource.use { server =>
      val uri: String = s"redis://127.0.0.1:${server.ports().get(0)}?timeout=15s"
      for {
        _ <- Redis[IO].utf8(uri).use { inventory =>
          for {
            _ <- inventory.pipeline_(shipment.map { line =>
              inventory.set(stockKey(line.sku), line.openingStock.toString)
            })
            receipt <- inventory.pipeline[Long] { (store: TxStore[IO, String, Long]) =>
              shipment.map { line =>
                inventory.incrBy(stockKey(line.sku), line.delivered).flatMap(store.set(line.sku))
              }
            }
            _ <- IO(assertEquals(expectedReceipt, receipt))
            // Normal commands must flush again after the batch, so an order can consume new stock.
            remaining <- inventory.decrBy(stockKey("coffee"), 4L)
            _ <- inventory.set("shipment:104:status", "received")
            status <- inventory.get("shipment:104:status")
            _ <- IO {
              assertEquals(11L, remaining)
              assertEquals(Some("received"), status)
            }
          } yield ()
        }
        _ <- Redis[IO].utf8(uri).use { reader =>
          for {
            stock <- shipment.traverse { line =>
              reader.get(stockKey(line.sku)).map(value => line.sku -> value)
            }
            status <- reader.get("shipment:104:status")
            _ <- IO {
              assertEquals(
                Map("coffee" -> Some("11"), "tea" -> Some("10"), "cocoa" -> Some("3")),
                stock.toMap
              )
              assertEquals(Some("received"), status)
            }
          } yield ()
        }
      } yield ()
    }
    program.timeout(45.seconds).unsafeRunSync()
  }

  private def stockKey(sku: String): String = s"warehouse:north:stock:$sku"

  private def serverResource: Resource[IO, RedisServer] = {
    val create: IO[RedisServer] = IO.blocking {
      val socket: ServerSocket = new ServerSocket(0)
      val port: Int = try socket.getLocalPort finally socket.close()
      RedisServer.newRedisServer()
        .bind("127.0.0.1")
        .port(port)
        .setting("save \"\"")
        .setting("appendonly no")
        .build()
    }
    Resource.make(create)(server => IO.blocking(server.stop()).void)
      .evalTap(server => IO.blocking(server.start()).void)
  }
}
