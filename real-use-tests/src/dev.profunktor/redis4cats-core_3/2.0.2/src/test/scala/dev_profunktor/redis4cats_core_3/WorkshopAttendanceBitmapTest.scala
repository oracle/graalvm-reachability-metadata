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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import redis.embedded.RedisServer

import java.net.ServerSocket
import scala.concurrent.duration._

/** Exercises bitmap attendance tracking to deduplicate check-ins and report participation across sessions. */
class WorkshopAttendanceBitmapTest {
  @Test
  @Timeout(60)
  def correctedCheckInUpdatesCompletionWithoutLosingSingleSessionAttendees(): Unit = {
    val mondayKey: String = "workshop:attendance:monday"
    val tuesdayKey: String = "workshop:attendance:tuesday"
    val completedKey: String = "workshop:attendance:completed"
    val reachedKey: String = "workshop:attendance:reached"
    val participantIds: List[Long] = List(3L, 12L, 40L, 75L, 99L)
    val program: IO[Unit] = serverResource.use { server =>
      val uri: String = s"redis://127.0.0.1:${server.ports().get(0)}?timeout=15s"
      for {
        _ <- Redis[IO].utf8(uri).use { attendance =>
          for {
            _ <- List(3L, 12L, 40L).traverse_(id => attendance.setBit(mondayKey, id, 1))
            _ <- List(12L, 40L, 75L).traverse_(id => attendance.setBit(tuesdayKey, id, 1))
            // A scanner retries the same check-in; the participant still counts only once.
            previous <- attendance.setBit(tuesdayKey, 12L, 1)
            mondayCount <- attendance.bitCount(mondayKey)
            tuesdayCount <- attendance.bitCount(tuesdayKey)
            _ <- attendance.bitOpAnd(completedKey, mondayKey, tuesdayKey)
            _ <- attendance.bitOpOr(reachedKey, mondayKey, tuesdayKey)
            completed <- attendance.bitCount(completedKey)
            reached <- attendance.bitCount(reachedKey)
            _ <- IO {
              assertEquals(1L, previous)
              assertEquals(3L, mondayCount)
              assertEquals(3L, tuesdayCount)
              assertEquals(2L, completed)
              assertEquals(4L, reached)
            }
            // An organizer corrects an accidental Monday check-in and rebuilds both reports.
            corrected <- attendance.setBit(mondayKey, 40L, 0)
            _ <- IO(assertEquals(1L, corrected))
            _ <- attendance.bitOpAnd(completedKey, mondayKey, tuesdayKey)
            _ <- attendance.bitOpOr(reachedKey, mondayKey, tuesdayKey)
          } yield ()
        }
        _ <- Redis[IO].utf8(uri).use { reporting =>
          for {
            mondayCount <- reporting.bitCount(mondayKey)
            tuesdayCount <- reporting.bitCount(tuesdayKey)
            completionCount <- reporting.bitCount(completedKey)
            reachCount <- reporting.bitCount(reachedKey)
            monday <- participantIds.traverse(id => reporting.getBit(mondayKey, id))
            tuesday <- participantIds.traverse(id => reporting.getBit(tuesdayKey, id))
            completed <- participantIds.traverse(id => reporting.getBit(completedKey, id))
            reached <- participantIds.traverse(id => reporting.getBit(reachedKey, id))
            _ <- IO {
              assertEquals(2L, mondayCount)
              assertEquals(3L, tuesdayCount)
              assertEquals(1L, completionCount)
              assertEquals(4L, reachCount)
              assertEquals(List(1L, 1L, 0L, 0L, 0L).map(Some(_)), monday)
              assertEquals(List(0L, 1L, 1L, 1L, 0L).map(Some(_)), tuesday)
              assertEquals(List(0L, 1L, 0L, 0L, 0L).map(Some(_)), completed)
              assertEquals(List(1L, 1L, 1L, 1L, 0L).map(Some(_)), reached)
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
