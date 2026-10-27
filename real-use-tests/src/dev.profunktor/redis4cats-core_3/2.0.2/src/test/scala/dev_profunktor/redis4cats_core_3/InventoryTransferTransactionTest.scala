/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_profunktor.redis4cats_core_3

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.effect.Log.NoOp._
import dev.profunktor.redis4cats.tx.TxStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import redis.embedded.RedisServer

import java.net.ServerSocket
import scala.concurrent.duration._

/** Exercises transaction result storage by transferring inventory and appending an audit entry atomically. */
class InventoryTransferTransactionTest {
  @Test
  @Timeout(60)
  def committedTransferReturnsAReceiptAndPersistsBothStockLevels(): Unit = {
    val sourceKey: String = "inventory:north:coffee"
    val destinationKey: String = "inventory:south:coffee"
    val auditKey: String = "inventory:transfers"
    val auditEntry: String = "transfer-104:north:south:coffee:4"
    val program: IO[Unit] = serverResource.use { server =>
      val uri: String = s"redis://127.0.0.1:${server.ports().get(0)}?timeout=15s"
      Redis[IO].utf8(uri).use { inventory =>
        for {
          _ <- inventory.set(sourceKey, "20")
          _ <- inventory.set(destinationKey, "5")
          receipt <- inventory.transact[Long] { (store: TxStore[IO, String, Long]) =>
            List(
              inventory.decrBy(sourceKey, 4L).flatMap(store.set("sourceRemaining")),
              inventory.incrBy(destinationKey, 4L).flatMap(store.set("destinationAvailable")),
              inventory.rPush(auditKey, auditEntry).flatMap(store.set("auditEntries"))
            )
          }
          _ <- IO(assertEquals(
            Map("sourceRemaining" -> 16L, "destinationAvailable" -> 9L, "auditEntries" -> 1L),
            receipt
          ))
          _ <- Redis[IO].utf8(uri).use { reader =>
            for {
              source <- reader.get(sourceKey)
              destination <- reader.get(destinationKey)
              audit <- reader.lRange(auditKey, 0L, -1L)
              _ <- IO {
                assertEquals(Some("16"), source)
                assertEquals(Some("9"), destination)
                assertEquals(List(auditEntry), audit)
                assertEquals(25, source.get.toInt + destination.get.toInt)
              }
            } yield ()
          }
          _ <- inventory.set("inventory:transfer-104:status", "committed")
          status <- inventory.get("inventory:transfer-104:status")
          _ <- IO(assertEquals(Some("committed"), status))
        } yield ()
      }
    }
    program.timeout(45.seconds).unsafeRunSync()
  }

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
