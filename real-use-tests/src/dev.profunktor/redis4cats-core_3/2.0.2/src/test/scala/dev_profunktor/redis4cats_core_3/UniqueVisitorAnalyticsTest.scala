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
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import redis.embedded.RedisServer

import java.net.ServerSocket
import scala.concurrent.duration._

/** Exercises HyperLogLog cardinality aggregation to report unique visitors across overlapping daily traffic. */
class UniqueVisitorAnalyticsTest {
  @Test
  @Timeout(60)
  def replayedTrafficDoesNotInflateDailyOrCombinedVisitorEstimates(): Unit = {
    val mondayKey: String = "analytics:shop:visitors:monday"
    val tuesdayKey: String = "analytics:shop:visitors:tuesday"
    val weeklyKey: String = "analytics:shop:visitors:week"
    val mondayVisitors: List[String] = (0 until 1000).map(id => s"visitor-$id").toList
    val tuesdayVisitors: List[String] = (500 until 1500).map(id => s"visitor-$id").toList
    val program: IO[Unit] = serverResource.use { server =>
      val uri: String = s"redis://127.0.0.1:${server.ports().get(0)}?timeout=15s"
      for {
        estimates <- Redis[IO].utf8(uri).use { analytics =>
          for {
            _ <- analytics.pfAdd(mondayKey, mondayVisitors*)
            _ <- analytics.pfAdd(tuesdayKey, tuesdayVisitors*)
            monday <- analytics.pfCount(mondayKey)
            tuesday <- analytics.pfCount(tuesdayKey)
            _ <- analytics.pfMerge(weeklyKey, mondayKey, tuesdayKey)
            weekly <- analytics.pfCount(weeklyKey)
            _ <- IO {
              // These are estimates, not exact set sizes; use a fixed corpus and a 3% error budget.
              assertEstimate(1000L, monday)
              assertEstimate(1000L, tuesday)
              assertEstimate(1500L, weekly)
              assertTrue(weekly < monday + tuesday, "Cross-day visitors must not be counted twice")
            }
            // A collector replays its batches after a restart, then rebuilds the weekly report.
            _ <- analytics.pfAdd(mondayKey, mondayVisitors*)
            _ <- analytics.pfAdd(tuesdayKey, tuesdayVisitors*)
            _ <- analytics.pfMerge(weeklyKey, mondayKey, tuesdayKey)
          } yield (monday, tuesday, weekly)
        }
        _ <- Redis[IO].utf8(uri).use { reporting =>
          for {
            monday <- reporting.pfCount(mondayKey)
            tuesday <- reporting.pfCount(tuesdayKey)
            weekly <- reporting.pfCount(weeklyKey)
            noTraffic <- reporting.pfCount("analytics:shop:visitors:wednesday")
            _ <- IO {
              // Replays and merges must preserve both source estimates and the stored aggregate.
              assertEquals(estimates, (monday, tuesday, weekly))
              assertEquals(0L, noTraffic)
            }
          } yield ()
        }
      } yield ()
    }
    program.timeout(45.seconds).unsafeRunSync()
  }

  private def assertEstimate(expected: Long, actual: Long): Unit = {
    val tolerance: Long = expected * 3L / 100L
    assertTrue(
      math.abs(actual - expected) <= tolerance,
      s"Expected approximately $expected unique visitors, got $actual"
    )
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
