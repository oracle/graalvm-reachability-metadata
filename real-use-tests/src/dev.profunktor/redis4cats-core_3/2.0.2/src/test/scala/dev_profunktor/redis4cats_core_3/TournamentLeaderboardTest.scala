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
import dev.profunktor.redis4cats.effects.{Score, ScoreWithValue}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import redis.embedded.RedisServer

import java.net.ServerSocket
import scala.concurrent.duration._

/** Exercises sorted-set leaderboards by updating tournament scores and serving ranked standings. */
class TournamentLeaderboardTest {
  @Test
  @Timeout(60)
  def standingsReflectMatchPointsAndPlayerWithdrawalAfterReconnection(): Unit = {
    val leaderboardKey: String = "tournament:autumn:standings"
    val program: IO[Unit] = serverResource.use { server =>
      val uri: String = s"redis://127.0.0.1:${server.ports().get(0)}?timeout=15s"
      for {
        _ <- Redis[IO].utf8(uri).use { leaderboard =>
          for {
            registered <- leaderboard.zAdd(
              leaderboardKey,
              None,
              ScoreWithValue(Score(120.0), "alice"),
              ScoreWithValue(Score(90.0), "bob"),
              ScoreWithValue(Score(75.0), "carol"),
              ScoreWithValue(Score(40.0), "dave")
            )
            openingPodium <- leaderboard.zRevRange(leaderboardKey, 0L, 2L)
            updatedScore <- leaderboard.zIncrBy(leaderboardKey, "carol", 60.0)
            promotedRank <- leaderboard.zRevRank(leaderboardKey, "carol")
            displacedRank <- leaderboard.zRevRank(leaderboardKey, "alice")
            // Dave withdraws; a disconnected reader must not show him in the final table.
            withdrawn <- leaderboard.zRem(leaderboardKey, "dave")
            _ <- IO {
              assertEquals(4L, registered)
              assertEquals(List("alice", "bob", "carol"), openingPodium)
              assertEquals(135.0, updatedScore)
              assertEquals(Some(0L), promotedRank)
              assertEquals(Some(1L), displacedRank)
              assertEquals(1L, withdrawn)
            }
          } yield ()
        }
        _ <- Redis[IO].utf8(uri).use { standings =>
          for {
            topTwo <- standings.zRevRangeWithScores(leaderboardKey, 0L, 1L)
            nextPage <- standings.zRevRange(leaderboardKey, 2L, 3L)
            participantCount <- standings.zCard(leaderboardKey)
            winnerScore <- standings.zScore(leaderboardKey, "carol")
            withdrawnRank <- standings.zRevRank(leaderboardKey, "dave")
            withdrawnScore <- standings.zScore(leaderboardKey, "dave")
            _ <- IO {
              assertEquals(
                List(
                  ScoreWithValue(Score(135.0), "carol"),
                  ScoreWithValue(Score(120.0), "alice")
                ),
                topTwo
              )
              assertEquals(List("bob"), nextPage)
              assertEquals(3L, participantCount)
              assertEquals(Some(135.0), winnerScore)
              assertEquals(None, withdrawnRank)
              assertEquals(None, withdrawnScore)
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
