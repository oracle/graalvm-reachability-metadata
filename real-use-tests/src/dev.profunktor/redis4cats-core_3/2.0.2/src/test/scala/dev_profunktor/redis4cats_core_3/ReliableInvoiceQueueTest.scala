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

/** Exercises list-backed reliable queues to acknowledge invoice jobs and recover interrupted work. */
class ReliableInvoiceQueueTest {
  @Test
  @Timeout(60)
  def acknowledgedJobsStayRemovedAndInterruptedJobsCanBeRequeued(): Unit = {
    val readyKey: String = "invoices:ready"
    val inFlightKey: String = "invoices:worker-1:in-flight"
    val program: IO[Unit] = serverResource.use { server =>
      val uri: String = s"redis://127.0.0.1:${server.ports().get(0)}?timeout=15s"
      for {
        _ <- Redis[IO].utf8(uri).use { worker =>
          for {
            queued <- worker.lPush(readyKey, "invoice-101", "invoice-102", "invoice-103")
            first <- worker.rPopLPush(readyKey, inFlightKey)
            beforeAck <- worker.lRange(inFlightKey, 0L, -1L)
            _ <- IO {
              assertEquals(3L, queued)
              assertEquals(Some("invoice-101"), first)
              assertEquals(List("invoice-101"), beforeAck)
            }
            acknowledged <- worker.lRem(inFlightKey, 1L, "invoice-101")
            second <- worker.rPopLPush(readyKey, inFlightKey)
            _ <- IO {
              assertEquals(1L, acknowledged)
              assertEquals(Some("invoice-102"), second)
            }
            // This worker closes before acknowledging the second invoice.
          } yield ()
        }
        _ <- Redis[IO].utf8(uri).use { replacement =>
          for {
            interrupted <- replacement.lRange(inFlightKey, 0L, -1L)
            waiting <- replacement.lRange(readyKey, 0L, -1L)
            _ <- IO {
              assertEquals(List("invoice-102"), interrupted)
              assertEquals(List("invoice-103"), waiting)
            }
            // An explicit recovery pass returns the interrupted job to the tail of the FIFO queue.
            recovered <- replacement.rPopLPush(inFlightKey, readyKey)
            next <- replacement.rPopLPush(readyKey, inFlightKey)
            _ <- IO {
              assertEquals(Some("invoice-102"), recovered)
              assertEquals(Some("invoice-103"), next)
            }
            nextAck <- replacement.lRem(inFlightKey, 1L, "invoice-103")
            retry <- replacement.rPopLPush(readyKey, inFlightKey)
            _ <- IO {
              assertEquals(1L, nextAck)
              assertEquals(Some("invoice-102"), retry)
            }
            retryAck <- replacement.lRem(inFlightKey, 1L, "invoice-102")
            noMoreJobs <- replacement.rPopLPush(readyKey, inFlightKey)
            pendingCount <- replacement.lLen(readyKey)
            unacknowledgedCount <- replacement.lLen(inFlightKey)
            _ <- IO {
              assertEquals(1L, retryAck)
              assertEquals(None, noMoreJobs)
              assertEquals(0L, pendingCount)
              assertEquals(0L, unacknowledgedCount)
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
