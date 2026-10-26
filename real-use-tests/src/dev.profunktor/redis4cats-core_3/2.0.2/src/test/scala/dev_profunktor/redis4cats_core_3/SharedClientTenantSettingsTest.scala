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
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log.NoOp._
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import redis.embedded.RedisServer

import java.net.ServerSocket
import scala.concurrent.duration._

/** Exercises shared RedisClient connection lifecycles with isolated tenant settings databases. */
class SharedClientTenantSettingsTest {
  @Test
  @Timeout(60)
  def tenantConnectionsKeepTheirDatabaseAndSurviveSiblingClosure(): Unit = {
    val settingKey: String = "settings:invoice-prefix"
    val program: IO[Unit] = serverResource.use { server =>
      val uri: String = s"redis://127.0.0.1:${server.ports().get(0)}/2?timeout=15s"
      RedisClient[IO].from(uri).use { client =>
        Redis[IO].fromClient(client, RedisCodec.Utf8).use { north =>
          for {
            _ <- north.set(settingKey, "NORTH-2026")
            _ <- Redis[IO].fromClient(client, RedisCodec.Utf8).use { south =>
              for {
                _ <- south.select(3)
                absent <- south.get(settingKey)
                _ <- IO(assertEquals(None, absent))
                _ <- south.set(settingKey, "SOUTH-2026")
                northPrefix <- north.get(settingKey)
                southPrefix <- south.get(settingKey)
                _ <- IO {
                  assertEquals(Some("NORTH-2026"), northPrefix)
                  assertEquals(Some("SOUTH-2026"), southPrefix)
                }
              } yield ()
            }
            retained <- north.get(settingKey)
            _ <- IO(assertEquals(Some("NORTH-2026"), retained))
            _ <- north.set(settingKey, "NORTH-2027")
            _ <- Redis[IO].fromClient(client, RedisCodec.Utf8).use { replacement =>
              for {
                defaultDatabasePrefix <- replacement.get(settingKey)
                _ <- IO(assertEquals(Some("NORTH-2027"), defaultDatabasePrefix))
                _ <- replacement.select(3)
                persistedSouthPrefix <- replacement.get(settingKey)
                _ <- IO(assertEquals(Some("SOUTH-2026"), persistedSouthPrefix))
                _ <- replacement.set(settingKey, "SOUTH-2027")
              } yield ()
            }
            unaffected <- north.get(settingKey)
            _ <- IO(assertEquals(Some("NORTH-2027"), unaffected))
          } yield ()
        }
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
