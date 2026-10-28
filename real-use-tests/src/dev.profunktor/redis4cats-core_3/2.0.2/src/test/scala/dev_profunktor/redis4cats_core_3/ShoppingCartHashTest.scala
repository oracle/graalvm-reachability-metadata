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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import redis.embedded.RedisServer

import java.net.ServerSocket
import scala.concurrent.duration._

/** Exercises hash-backed shopping carts by adjusting quantities and restoring the cart after reconnection. */
class ShoppingCartHashTest {
  @Test
  @Timeout(60)
  def cartEditsPersistWithoutChangingAnotherCustomersCart(): Unit = {
    val cartKey: String = "cart:customer-104"
    val otherCartKey: String = "cart:customer-105"
    val program: IO[Unit] = serverResource.use { server =>
      val uri: String = s"redis://127.0.0.1:${server.ports().get(0)}?timeout=15s"
      for {
        _ <- Redis[IO].utf8(uri).use { carts =>
          for {
            _ <- carts.hSet(cartKey, Map("coffee" -> "2", "tea" -> "1", "cocoa" -> "3"))
            _ <- carts.hSet(otherCartKey, Map("tea" -> "5"))
            coffeeQuantity <- carts.hIncrBy(cartKey, "coffee", 2L)
            cocoaQuantity <- carts.hIncrBy(cartKey, "cocoa", -1L)
            removed <- carts.hDel(cartKey, "tea")
            _ <- IO {
              assertEquals(4L, coffeeQuantity)
              assertEquals(2L, cocoaQuantity)
              assertEquals(1L, removed)
            }
          } yield ()
        }
        _ <- Redis[IO].utf8(uri).use { checkout =>
          for {
            stored <- checkout.hGetAll(cartKey)
            distinctItems <- checkout.hLen(cartKey)
            removedItem <- checkout.hGet(cartKey, "tea")
            otherCart <- checkout.hGetAll(otherCartKey)
            _ <- IO {
              val quantities: Map[String, Int] = stored.map { case (sku, quantity) =>
                sku -> quantity.toInt
              }
              assertEquals(Map("coffee" -> 4, "cocoa" -> 2), quantities)
              assertEquals(6, quantities.values.sum)
              assertEquals(2L, distinctItems)
              assertEquals(None, removedItem)
              assertEquals(Map("tea" -> "5"), otherCart)
            }
          } yield ()
        }
      } yield ()
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
