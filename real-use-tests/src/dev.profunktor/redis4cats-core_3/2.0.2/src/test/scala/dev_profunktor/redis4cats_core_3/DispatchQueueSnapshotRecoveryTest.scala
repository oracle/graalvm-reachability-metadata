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

/** Exercises key snapshot backup and recovery to restore a dispatch queue on a replacement Redis server. */
class DispatchQueueSnapshotRecoveryTest {
  @Test
  @Timeout(60)
  def recoveredQueuePreservesSnapshotOrderAndCanResumeDispatch(): Unit = {
    val queueKey: String = "dispatch:north:pending"
    val jobs: List[String] = List("parcel-104|locker-3", "parcel-105|shop-8", "parcel-106|locker-3")
    val program: IO[Unit] = for {
      snapshot <- serverResource.use { source =>
        val uri: String = s"redis://127.0.0.1:${source.ports().get(0)}?timeout=15s"
        Redis[IO].utf8(uri).use { dispatch =>
          for {
            _ <- dispatch.rPush(queueKey, jobs*)
            _ <- dispatch.set("dispatch:north:worker", "old-worker")
            saved <- dispatch.dump(queueKey)
            payload <- IO.fromOption(saved)(new IllegalStateException("Dispatch queue backup is missing"))
            // Work arriving after the backup must not appear in the point-in-time recovery.
            _ <- dispatch.rPush(queueKey, "parcel-107|depot-2")
            liveQueue <- dispatch.lRange(queueKey, 0L, -1L)
            _ <- IO(assertEquals(jobs :+ "parcel-107|depot-2", liveQueue))
          } yield payload
        }
      }
      // The original server and its connections are closed before recovery starts.
      _ <- serverResource.use { replacement =>
        val uri: String = s"redis://127.0.0.1:${replacement.ports().get(0)}?timeout=15s"
        for {
          _ <- Redis[IO].utf8(uri).use { recovery =>
            for {
              absent <- recovery.dump(queueKey)
              _ <- IO(assertEquals(None, absent))
              _ <- recovery.restore(queueKey, snapshot)
              restored <- recovery.lRange(queueKey, 0L, -1L)
              unbackedWorker <- recovery.get("dispatch:north:worker")
              _ <- IO {
                assertEquals(jobs, restored)
                assertEquals(None, unbackedWorker)
              }
            } yield ()
          }
          _ <- Redis[IO].utf8(uri).use { worker =>
            for {
              first <- worker.lPop(queueKey)
              remaining <- worker.lRange(queueKey, 0L, -1L)
              _ <- worker.rPush(queueKey, "parcel-108|shop-4")
              resumed <- worker.lRange(queueKey, 0L, -1L)
              _ <- IO {
                assertEquals(Some(jobs.head), first)
                assertEquals(jobs.tail, remaining)
                assertEquals(jobs.tail :+ "parcel-108|shop-4", resumed)
              }
            } yield ()
          }
        } yield ()
      }
    } yield ()
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
