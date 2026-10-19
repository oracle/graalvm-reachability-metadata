/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_profunktor.redis4cats_core_3

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.unsafe.implicits.global
import com.github.fppt.jedismock.RedisServer
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.effects.RangeLimit
import dev.profunktor.redis4cats.effects.Score
import dev.profunktor.redis4cats.effects.ScoreWithValue
import dev.profunktor.redis4cats.effects.ZRange
import dev.profunktor.redis4cats.log4cats.log4CatsInstance
import io.lettuce.core.ZAddArgs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

/**
 * Ranks players with the sorted-set commands of a core `RedisClient` connection.
 *
 * A game backend keeps one leaderboard per week as a sorted set: match
 * results are added with `ZADD` and `ZINCRBY`, a late re-submission is
 * ignored through `ZADD NX`, and the lobby shows the podium, a player's
 * rank and the players in a score band. At the end of the season the
 * weekly boards are merged with `ZUNIONSTORE`, the season board is trimmed
 * to its top three and the champion is popped off for the prize payout.
 */
class GameLeaderboardSortedSetsTest {

  /** One row of a rendered leaderboard. */
  private final case class Standing(player: String, points: Double)

  /** What the lobby and the season job observe. */
  private final case class Observed(
      added: Long,
      bobAfterMatch: Double,
      resubmittedAdded: Long,
      aliceScore: Option[Double],
      podium: List[Standing],
      carolRank: Option[Long],
      carolRevRank: Option[Long],
      midTier: List[String],
      midTierCount: Long,
      seasonSize: Long,
      seasonTop: List[Standing],
      trimmed: Long,
      champion: List[Standing],
      remaining: List[String]
  )

  private val week1: String = "leaderboard:week:1"
  private val week2: String = "leaderboard:week:2"
  private val season: String = "leaderboard:season"

  private def entry(player: String, points: Double): ScoreWithValue[String] =
    ScoreWithValue(Score(points), player)

  private def standings(rows: List[ScoreWithValue[String]]): List[Standing] =
    rows.map(row => Standing(row.value, row.score.value))

  @Test
  def ranksPlayersAcrossWeeklyLeaderboards(): Unit = {
    val server: RedisServer = RedisServer.newRedisServer().start()
    try {
      given Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("game-leaderboard")
      given Log[IO] = log4CatsInstance[IO]

      val connection: Resource[IO, RedisCommands[IO, String, String]] =
        for {
          client <- RedisClient[IO].from(s"redis://${server.getHost}:${server.getBindPort}")
          redis <- Redis[IO].fromClient(client, RedisCodec.Utf8)
        } yield redis

      val observed: Observed = connection
        .use { redis =>
          for {
            added <- redis.zAdd(
              week1,
              None,
              entry("alice", 120), entry("bob", 95), entry("carol", 150), entry("dave", 60), entry("erin", 180)
            )
            bobAfterMatch <- redis.zIncrBy(week1, "bob", 40)
            resubmittedAdded <- redis.zAdd(week1, Some(ZAddArgs.Builder.nx()), entry("alice", 999))
            aliceScore <- redis.zScore(week1, "alice")
            podium <- redis.zRevRangeWithScores(week1, 0, 2)
            carolRank <- redis.zRank(week1, "carol")
            carolRevRank <- redis.zRevRank(week1, "carol")
            midTier <- redis.zRangeByScore(week1, ZRange(100, 150), Some(RangeLimit(0, 10)))
            midTierCount <- redis.zCount(week1, ZRange(100, 150))
            _ <- redis.zAdd(week2, None, entry("alice", 70), entry("dave", 200), entry("frank", 90))
            seasonSize <- redis.zUnionStore(season, None, week1, week2)
            seasonTop <- redis.zRevRangeWithScores(season, 0, -1)
            trimmed <- redis.zRemRangeByRank(season, 0, -4)
            champion <- redis.zPopMax(season, 1)
            remaining <- redis.zRevRange(season, 0, -1)
          } yield Observed(
            added,
            bobAfterMatch,
            resubmittedAdded,
            aliceScore,
            standings(podium),
            carolRank,
            carolRevRank,
            midTier,
            midTierCount,
            seasonSize,
            standings(seasonTop),
            trimmed,
            standings(champion),
            remaining
          )
        }
        .unsafeRunSync()

      assertEquals(5L, observed.added)
      assertEquals(135.0, observed.bobAfterMatch)
      assertEquals(0L, observed.resubmittedAdded)
      assertEquals(Some(120.0), observed.aliceScore)
      assertEquals(List(Standing("erin", 180), Standing("carol", 150), Standing("bob", 135)), observed.podium)
      assertEquals(Some(3L), observed.carolRank)
      assertEquals(Some(1L), observed.carolRevRank)
      assertEquals(List("alice", "bob", "carol"), observed.midTier)
      assertEquals(3L, observed.midTierCount)
      assertEquals(6L, observed.seasonSize)
      assertEquals(
        List(
          Standing("dave", 260),
          Standing("alice", 190),
          Standing("erin", 180),
          Standing("carol", 150),
          Standing("bob", 135),
          Standing("frank", 90)
        ),
        observed.seasonTop
      )
      assertEquals(3L, observed.trimmed)
      assertEquals(List(Standing("dave", 260)), observed.champion)
      assertEquals(List("alice", "erin"), observed.remaining)
    } finally {
      server.stop()
    }
  }
}
