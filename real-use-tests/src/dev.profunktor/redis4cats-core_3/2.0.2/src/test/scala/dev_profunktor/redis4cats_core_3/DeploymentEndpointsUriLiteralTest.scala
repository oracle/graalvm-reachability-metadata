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
import dev.profunktor.redis4cats.syntax.literals.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.net.InetAddress

/**
 * Builds connection URIs with the compile-time checked `redis"..."` literal from `syntax.literals`.
 *
 * A service keeps its fixed deployment endpoints as URI literals, so a typo in
 * them fails the build rather than the first request. Each literal names its
 * own logical database on the same server: database 1 caches rendered pages
 * and database 2 queues background jobs. The service writes to both, checks
 * that each database only sees its own keys, flushes the cache without
 * touching the job queue, and finally switches the cache connection to the
 * job database with SELECT to confirm the jobs are still there.
 */
class DeploymentEndpointsUriLiteralTest {

  /** Fixed port the deployment literals point at; the embedded server binds it. */
  private val Port: Int = 46379

  /** Page cache endpoint, validated by the compiler. */
  private val cacheUri: RedisURI = redis"redis://127.0.0.1:46379/1"

  /** Background job queue endpoint on the same server, validated by the compiler. */
  private val jobsUri: RedisURI = redis"redis://127.0.0.1:46379/2"

  /** What the service observes in its two databases. */
  private final case class Observed(
      cachedPage: Option[String],
      pageSeenFromJobs: Option[String],
      queuedJobs: List[String],
      cacheSizeBefore: Long,
      jobsSizeBefore: Long,
      cacheSizeAfterFlush: Long,
      jobsSizeAfterFlush: Long,
      jobsViaSelect: List[String]
  )

  @Test
  def literalUrisSelectSeparateDatabases(): Unit = {
    assertEquals("127.0.0.1", cacheUri.underlying.getHost)
    assertEquals(Port, cacheUri.underlying.getPort)
    assertEquals(1, cacheUri.underlying.getDatabase)
    assertEquals(2, jobsUri.underlying.getDatabase)

    val server: RedisServer = RedisServer.newRedisServer(Port, InetAddress.getByName("127.0.0.1")).start()
    try {
      given Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("deployment-endpoints")
      given Log[IO] = log4CatsInstance[IO]

      val connections: Resource[IO, (RedisCommands[IO, String, String], RedisCommands[IO, String, String])] =
        for {
          cacheClient <- RedisClient[IO].fromUri(cacheUri)
          jobsClient <- RedisClient[IO].fromUri(jobsUri)
          cache <- Redis[IO].fromClient(cacheClient, RedisCodec.Utf8)
          jobs <- Redis[IO].fromClient(jobsClient, RedisCodec.Utf8)
        } yield (cache, jobs)

      val observed: Observed = connections
        .use { (cache, jobs) =>
          for {
            _ <- cache.set("page:/home", "<h1>Welcome</h1>")
            _ <- cache.set("page:/about", "<h1>About us</h1>")
            _ <- jobs.rPush("jobs:email", "welcome:alice", "welcome:bob", "digest:carol")
            cachedPage <- cache.get("page:/home")
            pageSeenFromJobs <- jobs.get("page:/home")
            queuedJobs <- jobs.lRange("jobs:email", 0, -1)
            cacheSizeBefore <- cache.dbsize
            jobsSizeBefore <- jobs.dbsize
            _ <- cache.flushDb
            cacheSizeAfterFlush <- cache.dbsize
            jobsSizeAfterFlush <- jobs.dbsize
            _ <- cache.select(jobsUri.underlying.getDatabase)
            jobsViaSelect <- cache.lRange("jobs:email", 0, -1)
          } yield Observed(
            cachedPage,
            pageSeenFromJobs,
            queuedJobs,
            cacheSizeBefore,
            jobsSizeBefore,
            cacheSizeAfterFlush,
            jobsSizeAfterFlush,
            jobsViaSelect
          )
        }
        .unsafeRunSync()

      assertEquals(Some("<h1>Welcome</h1>"), observed.cachedPage)
      assertEquals(None, observed.pageSeenFromJobs)
      assertEquals(List("welcome:alice", "welcome:bob", "digest:carol"), observed.queuedJobs)
      assertEquals(2L, observed.cacheSizeBefore)
      assertEquals(1L, observed.jobsSizeBefore)
      assertEquals(0L, observed.cacheSizeAfterFlush)
      assertEquals(1L, observed.jobsSizeAfterFlush)
      assertEquals(observed.queuedJobs, observed.jobsViaSelect)
    } finally {
      server.stop()
    }
  }
}
