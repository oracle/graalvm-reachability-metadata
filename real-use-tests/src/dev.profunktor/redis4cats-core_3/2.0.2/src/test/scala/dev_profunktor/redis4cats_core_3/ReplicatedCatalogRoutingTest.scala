/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_profunktor.redis4cats_core_3

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import dev.profunktor.redis4cats.{Redis, RedisCommands}
import dev.profunktor.redis4cats.connection.{RedisMasterReplica, RedisURI}
import dev.profunktor.redis4cats.data.{ReadFrom, RedisCodec}
import dev.profunktor.redis4cats.effect.Log.NoOp._
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import redis.embedded.RedisServer

import java.net.ServerSocket
import scala.concurrent.duration._

/** Exercises master/replica routing by publishing catalog updates with replica-only reads. */
class ReplicatedCatalogRoutingTest {
  @Test
  @Timeout(60)
  def catalogWritesReachThePrimaryAndUpdatesBecomeVisibleOnTheReplica(): Unit = {
    val catalogKey: String = "catalog:coffee:description"
    val program: IO[Unit] = serverResource(None).use { primary =>
      val primaryPort: Int = primary.ports().get(0)
      serverResource(Some(primaryPort)).use { replica =>
        val primaryAddress: String = s"redis://127.0.0.1:$primaryPort?timeout=15s"
        val replicaAddress: String = s"redis://127.0.0.1:${replica.ports().get(0)}?timeout=15s"
        val connections: Resource[IO, RedisCommands[IO, String, String]] = for {
          primaryUri <- Resource.eval(RedisURI.make[IO](primaryAddress))
          replicaUri <- Resource.eval(RedisURI.make[IO](replicaAddress))
          connection <- RedisMasterReplica[IO].make(RedisCodec.Utf8, primaryUri, replicaUri)(
            Some(ReadFrom.Replica)
          )
          commands <- Redis[IO].masterReplica(connection)
        } yield commands

        connections.use { catalog =>
          Redis[IO].utf8(primaryAddress).use { primaryReader =>
            Redis[IO].utf8(replicaAddress).use { replicaReader =>
              for {
                // A strict replica read policy must still send writes to the primary.
                _ <- catalog.set(catalogKey, "Coffee beans, 250g")
                original <- primaryReader.get(catalogKey)
                _ <- IO(assertEquals(Some("Coffee beans, 250g"), original))
                _ <- awaitValue(replicaReader, catalogKey, Some("Coffee beans, 250g"))
                visible <- catalog.get(catalogKey)
                _ <- IO(assertEquals(Some("Coffee beans, 250g"), visible))
                _ <- catalog.set(catalogKey, "Coffee beans, 500g")
                _ <- awaitValue(catalog, catalogKey, Some("Coffee beans, 500g"))
                updatedPrimary <- primaryReader.get(catalogKey)
                _ <- IO(assertEquals(Some("Coffee beans, 500g"), updatedPrimary))
                removed <- catalog.del(catalogKey)
                _ <- IO(assertEquals(1L, removed))
                _ <- awaitValue(catalog, catalogKey, None)
                missingPrimary <- primaryReader.get(catalogKey)
                missingReplica <- replicaReader.get(catalogKey)
                _ <- IO {
                  assertEquals(None, missingPrimary)
                  assertEquals(None, missingReplica)
                }
              } yield ()
            }
          }
        }
      }
    }
    program.timeout(50.seconds).unsafeRunSync()
  }

  private def awaitValue(
      reader: RedisCommands[IO, String, String],
      key: String,
      expected: Option[String]
  ): IO[Unit] = {
    def poll: IO[Unit] = reader.get(key).flatMap { value =>
      if (value == expected) IO.unit
      else IO.sleep(50.millis) *> IO.defer(poll)
    }
    poll.timeout(15.seconds)
  }

  private def serverResource(primaryPort: Option[Int]): Resource[IO, RedisServer] = {
    val create: IO[RedisServer] = IO.blocking {
      val socket: ServerSocket = new ServerSocket(0)
      val port: Int = try socket.getLocalPort finally socket.close()
      val builder = RedisServer.newRedisServer()
        .bind("127.0.0.1")
        .port(port)
        .setting("save \"\"")
        .setting("appendonly no")
        .setting("repl-diskless-sync-delay 0")
      primaryPort.foreach(upstream => builder.setting(s"replicaof 127.0.0.1 $upstream"))
      builder.build()
    }
    Resource.make(create)(server => IO.blocking(server.stop()).void)
      .evalTap(server => IO.blocking(server.start()).void)
  }
}
