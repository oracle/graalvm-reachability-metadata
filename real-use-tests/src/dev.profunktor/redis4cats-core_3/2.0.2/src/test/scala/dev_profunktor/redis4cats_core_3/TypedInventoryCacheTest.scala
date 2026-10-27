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
import dev.profunktor.redis4cats.codecs.Codecs
import dev.profunktor.redis4cats.codecs.splits.SplitEpi
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log.NoOp._
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import redis.embedded.RedisServer

import java.net.ServerSocket
import scala.concurrent.duration._

/** Exercises derived Redis codecs by persisting a typed warehouse inventory cache. */
class TypedInventoryCacheTest {
  final case class StockKey(warehouse: String, sku: String)
  final case class StockSnapshot(available: Int, reserved: Int)

  @Test
  @Timeout(60)
  def inventorySurvivesReconnectionWithDomainKeysAndValues(): Unit = {
    val key: StockKey = StockKey("north", "coffee")
    val initial: StockSnapshot = StockSnapshot(12, 2)
    val updated: StockSnapshot = StockSnapshot(9, 5)
    val keyMapping: SplitEpi[String, StockKey] = SplitEpi[String, StockKey](
      encoded => {
        val parts: Array[String] = encoded.split(":", 3)
        StockKey(parts(1), parts(2))
      },
      stockKey => s"stock:${stockKey.warehouse}:${stockKey.sku}"
    )
    val valueMapping: SplitEpi[String, StockSnapshot] = SplitEpi[String, StockSnapshot](
      encoded => {
        val parts: Array[String] = encoded.split("\\|", 2)
        StockSnapshot(parts(0).toInt, parts(1).toInt)
      },
      snapshot => s"${snapshot.available}|${snapshot.reserved}"
    )
    val codec: RedisCodec[StockKey, StockSnapshot] =
      Codecs.derive(RedisCodec.Utf8, keyMapping, valueMapping)

    val program: IO[Unit] = serverResource.use { server =>
      val uri: String = s"redis://127.0.0.1:${server.ports().get(0)}?timeout=15s"
      for {
        _ <- Redis[IO].simple(uri, codec).use { cache =>
          for {
            missing <- cache.get(key)
            _ <- IO(assertEquals(None, missing))
            _ <- cache.set(key, initial)
            loaded <- cache.get(key)
            _ <- IO(assertEquals(Some(initial), loaded))
            _ <- cache.set(key, updated)
          } yield ()
        }
        _ <- Redis[IO].utf8(uri).use { strings =>
          for {
            stored <- strings.get("stock:north:coffee")
            _ <- IO(assertEquals(Some("9|5"), stored))
          } yield ()
        }
        _ <- Redis[IO].simple(uri, codec).use { cache =>
          for {
            reloaded <- cache.get(key)
            _ <- IO(assertEquals(Some(updated), reloaded))
            _ <- cache.del(key)
            removed <- cache.get(key)
            _ <- IO(assertEquals(None, removed))
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
