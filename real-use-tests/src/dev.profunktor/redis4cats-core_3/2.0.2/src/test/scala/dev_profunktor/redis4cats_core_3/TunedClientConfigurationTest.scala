/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_profunktor.redis4cats_core_3

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.unsafe.implicits.global
import com.github.fppt.jedismock.RedisServer
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.config.Redis4CatsConfig
import dev.profunktor.redis4cats.config.ShutdownConfig
import dev.profunktor.redis4cats.connection.InvalidRedisURI
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.connection.RedisURI
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log
import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisCommandTimeoutException
import io.lettuce.core.TimeoutOptions
import io.lettuce.core.resource.ClientResources
import io.lettuce.core.resource.DefaultClientResources
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.*

/**
 * Configures clients through `RedisClient.custom` with Lettuce `ClientOptions` and a `Redis4CatsConfig`.
 *
 * An inventory worker validates its configured Redis URI, then builds its
 * clients with a command timeout, a short shutdown period and Netty client
 * resources that the application owns and shares between clients. Waiting
 * for a job on an empty queue runs into the command timeout instead of
 * hanging the worker. After the first client is released, a second client on
 * the same resources still reaches the data, and the application's own `Log`
 * records every client and connection release.
 */
class TunedClientConfigurationTest {

  /** What the worker observes across both clients. */
  private final case class Observed(
      firstRead: Option[String],
      waitForJob: Either[Throwable, Option[(String, String)]],
      secondRead: Option[String]
  )

  /** Application logger that keeps the messages redis4cats emits. */
  private final class RecordingLog extends Log[IO] {
    private val messages: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue[String]()

    def debug(msg: => String): IO[Unit] = IO(messages.add(s"DEBUG $msg")).void
    def error(msg: => String): IO[Unit] = IO(messages.add(s"ERROR $msg")).void
    def info(msg: => String): IO[Unit] = IO(messages.add(s"INFO $msg")).void

    def recorded: List[String] = messages.asScala.toList
  }

  /** Netty resources owned by the application rather than by any single client. */
  private val sharedResources: Resource[IO, ClientResources] =
    Resource.make(IO(DefaultClientResources.builder().ioThreadPoolSize(2).computationThreadPoolSize(2).build())) {
      resources => IO.blocking(resources.shutdown(0L, 2L, TimeUnit.SECONDS).get()).void
    }

  @Test
  def appliesClientOptionsAndSharedResources(): Unit = {
    val server: RedisServer = RedisServer.newRedisServer().start()
    try {
      val log: RecordingLog = new RecordingLog
      given Log[IO] = log

      val address: String = s"redis://${server.getHost}:${server.getBindPort}"
      val rejected: Either[InvalidRedisURI, RedisURI] = RedisURI.fromString("http://inventory.example.com")
      val uri: RedisURI = RedisURI.fromString(address).fold(error => throw error, identity)

      val options: ClientOptions = ClientOptions
        .builder()
        .timeoutOptions(TimeoutOptions.enabled(Duration.ofSeconds(10L)))
        .build()

      def client(resources: ClientResources): Resource[IO, RedisClient] = {
        val config: Redis4CatsConfig = Redis4CatsConfig()
          .withShutdown(ShutdownConfig(quietPeriod = 0.seconds, timeout = 1.second))
          .withClientResources(resources)
        RedisClient[IO].custom(uri, options, config)
      }

      def commands(client: RedisClient): Resource[IO, RedisCommands[IO, String, String]] =
        Redis[IO].fromClient(client, RedisCodec.Utf8)

      val observed: Observed = sharedResources
        .use { resources =>
          for {
            first <- client(resources).use { redis =>
              for {
                firstRead <- commands(redis).use { inventory =>
                  inventory.set("stock:widget", "42") *> inventory.get("stock:widget")
                }
                waitForJob <- commands(redis).use { queue =>
                  queue.blPop(30.seconds, NonEmptyList.one("jobs:restock")).attempt
                }
              } yield (firstRead, waitForJob)
            }
            secondRead <- client(resources).use(redis => commands(redis).use(_.get("stock:widget")))
          } yield Observed(first._1, first._2, secondRead)
        }
        .unsafeRunSync()

      assertTrue(rejected.isLeft, s"expected an invalid URI, got $rejected")
      assertEquals(Some("42"), observed.firstRead)
      assertTrue(
        observed.waitForJob.left.exists(_.isInstanceOf[RedisCommandTimeoutException]),
        s"expected a command timeout, got ${observed.waitForJob}"
      )
      assertEquals(Some("42"), observed.secondRead)

      val recorded: List[String] = log.recorded
      assertEquals(2, recorded.count(_.startsWith("INFO Releasing Redis connection: ")), recorded.mkString("\n"))
      assertEquals(
        List.fill(3)(s"INFO Releasing Commands connection: $address"),
        recorded.filter(_.startsWith("INFO Releasing Commands connection: ")),
        recorded.mkString("\n")
      )
    } finally {
      server.stop()
    }
  }
}
