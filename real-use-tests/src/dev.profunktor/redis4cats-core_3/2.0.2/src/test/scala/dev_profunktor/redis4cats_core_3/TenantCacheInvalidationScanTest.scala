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
import dev.profunktor.redis4cats.{Redis, RedisCommands}
import dev.profunktor.redis4cats.data.KeyScanCursor
import dev.profunktor.redis4cats.effect.Log.NoOp._
import dev.profunktor.redis4cats.effects.KeyScanArgs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import redis.embedded.RedisServer

import java.net.ServerSocket
import scala.concurrent.duration._

/** Exercises cursor-based key scanning to invalidate one tenant's cached catalog without affecting another. */
class TenantCacheInvalidationScanTest {
  @Test
  @Timeout(60)
  def catalogRefreshRemovesOnlyTheSelectedTenantCache(): Unit = {
    val staleKeys: List[String] = (1 to 48).map(id => s"cache:north:catalog:$id").toList
    val retainedKeys: List[String] = (1 to 16).map(id => s"cache:south:catalog:$id").toList
    val scanArgs: KeyScanArgs = KeyScanArgs("cache:north:catalog:*", 3L)
    val program: IO[Unit] = serverResource.use { server =>
      val uri: String = s"redis://127.0.0.1:${server.ports().get(0)}?timeout=15s"
      Redis[IO].utf8(uri).use { cache =>
        for {
          _ <- (staleKeys ++ retainedKeys).traverse_(key => cache.set(key, s"snapshot:$key"))
          _ <- cache.set("settings:north:catalog-version", "v2")
          discovered <- matchingKeys(cache, scanArgs)
          _ <- IO(assertEquals(staleKeys.toSet, discovered))
          // Finish enumeration before deleting: SCAN can revisit keys and does not provide a snapshot.
          removed <- discovered.toList.traverse(key => cache.del(key))
          _ <- IO(assertEquals(48L, removed.sum))
          _ <- Redis[IO].utf8(uri).use { reader =>
            for {
              staleValues <- staleKeys.traverse(reader.get)
              retainedValues <- retainedKeys.traverse(reader.get)
              version <- reader.get("settings:north:catalog-version")
              remaining <- matchingKeys(reader, scanArgs)
              _ <- IO {
                assertEquals(List.fill(48)(None), staleValues)
                assertEquals(retainedKeys.map(key => Some(s"snapshot:$key")), retainedValues)
                assertEquals(Some("v2"), version)
                assertEquals(Set.empty[String], remaining)
              }
            } yield ()
          }
        } yield ()
      }
    }
    program.timeout(45.seconds).unsafeRunSync()
  }

  private def matchingKeys(
      cache: RedisCommands[IO, String, String],
      args: KeyScanArgs
  ): IO[Set[String]] = {
    def collect(page: KeyScanCursor[String], found: Set[String]): IO[Set[String]] = {
      val accumulated: Set[String] = found ++ page.keys
      if (page.isFinished) IO.pure(accumulated)
      else cache.scan(page, args).flatMap(next => collect(next, accumulated))
    }
    cache.scan(args).flatMap(first => collect(first, Set.empty[String]))
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
