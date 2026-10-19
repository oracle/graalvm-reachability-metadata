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
import com.github.fppt.jedismock.datastructures.Slice
import com.github.fppt.jedismock.operations.server.MockExecutor
import com.github.fppt.jedismock.server.RedisCommandInterceptor
import com.github.fppt.jedismock.server.Response
import com.github.fppt.jedismock.server.ServiceOptions
import com.github.fppt.jedismock.storage.OperationExecutorState
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.connection.RedisMasterReplica
import dev.profunktor.redis4cats.connection.RedisURI
import dev.profunktor.redis4cats.data.ReadFrom
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.log4cats.log4CatsInstance
import io.lettuce.core.ReadFrom as JReadFrom
import io.lettuce.core.RedisException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.util.List as JList

/**
 * Opens master/replica connections through `RedisMasterReplica` with `ReadFrom` read routing.
 *
 * A feature-flag service writes flags through a master/replica connection
 * that prefers replicas for reads. The deployment discovered from the seed
 * URI has a master and no replica yet, so preferred-replica reads fall back to
 * the master, while a reporting job pinned to `ReadFrom.Replica` sees its
 * reads refused until a replica joins. Writes always go to the master. The
 * in-memory server answers `ROLE` the way a standalone Redis master does, so
 * Lettuce can discover the topology.
 */
class FeatureFlagsMasterReplicaTest {

  /** A master/replica connection together with the commands issued over it. */
  private type Connected = (RedisMasterReplica[String, String], RedisCommands[IO, String, String])

  /** What the feature-flag service and the reporting job observe. */
  private final case class Observed(
      serviceReadFrom: JReadFrom,
      checkoutFlag: Option[String],
      enabledFlags: Map[String, String],
      role: Option[String],
      reportingReadFrom: JReadFrom,
      reportingWriteOnMaster: Option[String],
      reportingRead: Either[Throwable, Option[String]]
  )

  /** Keeps every flag of the service as a field of the `flags` hash. */
  private final class FeatureFlags(redis: RedisCommands[IO, String, String]) {

    def enable(flag: String, rollout: String): IO[Boolean] = redis.hSet("flags", flag, rollout)

    def rollout(flag: String): IO[Option[String]] = redis.hGet("flags", flag)

    def all: IO[Map[String, String]] = redis.hGetAll("flags")
  }

  /** Answers `ROLE` as a master without replicas and runs every other command normally. */
  private final class StandaloneMasterRole extends RedisCommandInterceptor {

    override def execCommand(state: OperationExecutorState, name: String, params: JList[Slice]): Slice =
      if (name.equalsIgnoreCase("role"))
        Response.array(Response.bulkString(Slice.create("master")), Response.integer(0L), Response.EMPTY_ARRAY)
      else MockExecutor.proceed(state, name, params)
  }

  @Test
  def routesReadsAccordingToReadFrom(): Unit = {
    val server: RedisServer =
      RedisServer.newRedisServer().setOptions(ServiceOptions.withInterceptor(new StandaloneMasterRole)).start()
    try {
      given Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("feature-flags")
      given Log[IO] = log4CatsInstance[IO]

      def connect(uri: RedisURI, readFrom: JReadFrom): Resource[IO, Connected] =
        for {
          connection <- RedisMasterReplica[IO].make(RedisCodec.Utf8, uri)(Some(readFrom))
          redis <- Redis[IO].masterReplica(connection)
        } yield (connection, redis)

      val connections: Resource[IO, (Connected, Connected)] =
        for {
          uri <- Resource.eval(RedisURI.make[IO](s"redis://${server.getHost}:${server.getBindPort}"))
          service <- connect(uri, ReadFrom.ReplicaPreferred)
          reporting <- connect(uri, ReadFrom.Replica)
        } yield (service, reporting)

      val observed: Observed = connections
        .use { case ((serviceConnection, service), (reportingConnection, reporting)) =>
          val flags: FeatureFlags = new FeatureFlags(service)
          for {
            _ <- flags.enable("checkout-v2", "25%")
            _ <- flags.enable("dark-mode", "100%")
            checkout <- flags.rollout("checkout-v2")
            enabled <- flags.all
            info <- service.info("replication")
            _ <- reporting.set("report:last-run", "2026-10-19")
            written <- service.get("report:last-run")
            reportingRead <- reporting.get("report:last-run").attempt
          } yield Observed(
            serviceConnection.underlying.getReadFrom,
            checkout,
            enabled,
            info.get("role"),
            reportingConnection.underlying.getReadFrom,
            written,
            reportingRead
          )
        }
        .unsafeRunSync()

      assertEquals(ReadFrom.ReplicaPreferred, observed.serviceReadFrom)
      assertEquals(Some("25%"), observed.checkoutFlag)
      assertEquals(Map("checkout-v2" -> "25%", "dark-mode" -> "100%"), observed.enabledFlags)
      assertEquals(Some("master"), observed.role)
      assertEquals(ReadFrom.Replica, observed.reportingReadFrom)
      assertEquals(Some("2026-10-19"), observed.reportingWriteOnMaster)
      assertTrue(
        observed.reportingRead.left.exists(_.isInstanceOf[RedisException]),
        s"a replica-only read without replicas must be refused, got ${observed.reportingRead}"
      )
    } finally {
      server.stop()
    }
  }
}
