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
import dev.profunktor.redis4cats.connection.RedisURI
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.log4cats.log4CatsInstance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

/**
 * Connects a `RedisClient` built from a parsed `RedisURI` and drives plain string commands.
 *
 * A web application keeps login sessions in Redis: it stores each session
 * with an expiry, refuses to overwrite a session ID that is already taken,
 * counts page views per session, reads several sessions in one round trip
 * and deletes a session on logout. The client and the command connection are
 * resources, released in reverse order once the application is done.
 */
class SessionStoreStringCommandsTest {

  /** What the application reads back from the store after a few requests. */
  private final case class Observed(
      aliceSession: Option[String],
      duplicateAccepted: Boolean,
      aliceTtl: Option[FiniteDuration],
      aliceViews: Long,
      sessions: Map[String, String],
      deleted: Long,
      afterLogout: Option[String]
  )

  /** Stores sessions under `session:<id>` and counts views under `views:<id>`. */
  private final class SessionStore(redis: RedisCommands[IO, String, String]) {

    def login(id: String, user: String, ttl: FiniteDuration): IO[Boolean] =
      redis.setNx(s"session:$id", user).flatTap(created => if (created) redis.expire(s"session:$id", ttl) else IO.unit)

    def user(id: String): IO[Option[String]] = redis.get(s"session:$id")

    def remaining(id: String): IO[Option[FiniteDuration]] = redis.ttl(s"session:$id")

    def view(id: String): IO[Long] = redis.incr(s"views:$id")

    def users(ids: Set[String]): IO[Map[String, String]] =
      redis.mGet(ids.map(id => s"session:$id")).map(_.map((key, user) => key.stripPrefix("session:") -> user))

    def logout(id: String): IO[Long] = redis.del(s"session:$id", s"views:$id")
  }

  @Test
  def storesExpiringSessionsAndCountsViews(): Unit = {
    val server: RedisServer = RedisServer.newRedisServer().start()
    try {
      given Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("session-store")
      given Log[IO] = log4CatsInstance[IO]

      val connection: Resource[IO, RedisCommands[IO, String, String]] =
        for {
          uri <- Resource.eval(RedisURI.make[IO](s"redis://${server.getHost}:${server.getBindPort}"))
          client <- RedisClient[IO].fromUri(uri)
          redis <- Redis[IO].fromClient(client, RedisCodec.Utf8)
        } yield redis

      val observed: Observed = connection
        .use { redis =>
          val store: SessionStore = new SessionStore(redis)
          for {
            _ <- store.login("a1", "alice", 30.minutes)
            _ <- store.login("b2", "bob", 30.minutes)
            duplicate <- store.login("a1", "mallory", 30.minutes)
            alice <- store.user("a1")
            ttl <- store.remaining("a1")
            _ <- store.view("a1")
            _ <- store.view("a1")
            views <- store.view("a1")
            sessions <- store.users(Set("a1", "b2", "missing"))
            deleted <- store.logout("a1")
            afterLogout <- store.user("a1")
          } yield Observed(alice, duplicate, ttl, views, sessions, deleted, afterLogout)
        }
        .unsafeRunSync()

      assertEquals(Some("alice"), observed.aliceSession)
      assertEquals(false, observed.duplicateAccepted)
      assertTrue(observed.aliceTtl.exists(ttl => ttl > 29.minutes && ttl <= 30.minutes), observed.aliceTtl.toString)
      assertEquals(3L, observed.aliceViews)
      assertEquals(Map("a1" -> "alice", "b2" -> "bob"), observed.sessions)
      assertEquals(2L, observed.deleted)
      assertEquals(None, observed.afterLogout)
    } finally {
      server.stop()
    }
  }
}
