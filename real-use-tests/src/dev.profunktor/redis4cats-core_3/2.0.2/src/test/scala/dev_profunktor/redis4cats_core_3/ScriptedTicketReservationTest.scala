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
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log.NoOp._
import dev.profunktor.redis4cats.effects.ScriptOutputType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import redis.embedded.RedisServer

import java.net.ServerSocket
import scala.concurrent.duration._

/** Exercises cached Lua scripting to reserve tickets atomically with idempotent booking retries. */
class ScriptedTicketReservationTest {
  @Test
  @Timeout(60)
  def retriesDoNotConsumeCapacityAndRejectedBookingsLeaveNoReceipt(): Unit = {
    val capacityKey: String = "event:concert:remaining"
    val firstReceipt: String = "event:concert:booking:order-104"
    val rejectedReceipt: String = "event:concert:booking:order-105"
    val lastReceipt: String = "event:concert:booking:order-106"
    val script: String =
      """local receipt = redis.call('GET', KEYS[2])
        |if receipt then
        |  return tonumber(receipt)
        |end
        |local available = tonumber(redis.call('GET', KEYS[1]) or '0')
        |local requested = tonumber(ARGV[1])
        |if available < requested then
        |  return -1
        |end
        |local remaining = redis.call('DECRBY', KEYS[1], requested)
        |redis.call('SET', KEYS[2], tostring(remaining))
        |return remaining
        |""".stripMargin
    val output: ScriptOutputType.Aux[String, Long] = ScriptOutputType.Integer[String]
    val program: IO[Unit] = serverResource.use { server =>
      val uri: String = s"redis://127.0.0.1:${server.ports().get(0)}?timeout=15s"
      for {
        digest <- Redis[IO].simple(uri, RedisCodec.Utf8).use { setup =>
          for {
            _ <- setup.set(capacityKey, "10")
            sha <- setup.scriptLoad(script)
          } yield sha
        }
        _ <- Redis[IO].simple(uri, RedisCodec.Utf8).use { bookings =>
          for {
            booked <- bookings.evalSha(digest, output, List(capacityKey, firstReceipt), List("3"))
            retried <- bookings.evalSha(digest, output, List(capacityKey, firstReceipt), List("3"))
            rejected <- bookings.evalSha(digest, output, List(capacityKey, rejectedReceipt), List("8"))
            remaining <- bookings.get(capacityKey)
            absentReceipt <- bookings.get(rejectedReceipt)
            soldOut <- bookings.evalSha(digest, output, List(capacityKey, lastReceipt), List("7"))
            soldOutRetry <- bookings.evalSha(digest, output, List(capacityKey, lastReceipt), List("7"))
            rejectedAgain <- bookings.evalSha(digest, output, List(capacityKey, rejectedReceipt), List("1"))
            _ <- IO {
              assertEquals(7L, booked)
              assertEquals(7L, retried)
              assertEquals(-1L, rejected)
              assertEquals(Some("7"), remaining)
              assertEquals(None, absentReceipt)
              assertEquals(0L, soldOut)
              assertEquals(0L, soldOutRetry)
              assertEquals(-1L, rejectedAgain)
            }
          } yield ()
        }
        _ <- Redis[IO].simple(uri, RedisCodec.Utf8).use { reader =>
          for {
            remaining <- reader.get(capacityKey)
            first <- reader.get(firstReceipt)
            last <- reader.get(lastReceipt)
            rejected <- reader.get(rejectedReceipt)
            _ <- IO {
              assertEquals(Some("0"), remaining)
              assertEquals(Some("7"), first)
              assertEquals(Some("0"), last)
              assertEquals(None, rejected)
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
