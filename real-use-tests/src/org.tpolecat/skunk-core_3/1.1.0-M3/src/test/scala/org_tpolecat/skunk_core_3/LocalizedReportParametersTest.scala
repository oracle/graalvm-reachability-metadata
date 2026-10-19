/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_tpolecat.skunk_core_3

import cats.effect.IO
import cats.effect.Resource
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.typelevel.otel4s.trace.Tracer
import skunk.Query
import skunk.Session
import skunk.Void
import skunk.codec.all.*
import skunk.implicits.*

import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.*

/** Exercises Skunk server-parameter streams to synchronize localized report labels with session settings. */
class LocalizedReportParametersTest {
  private given Tracer[IO] = Tracer.noop[IO]

  private final case class Report(timeZone: String, localTime: String)

  private val reportTime: Query[Void, String] =
    sql"""SELECT to_char(TIMESTAMPTZ '2025-01-15 12:00:00+00',
                        'YYYY-MM-DD HH24:MI')""".query(text)

  private def readReport(session: Session[IO], expectedZone: String): IO[Report] =
    for {
      // ParameterStatus messages arrive asynchronously; wait for the requested setting.
      zone <- session.parameter("TimeZone").filter(_ == expectedZone)
        .take(1).compile.lastOrError.timeout(10.seconds)
      localTime <- session.unique(reportTime)
    } yield Report(zone, localTime)

  @Test
  @Timeout(60)
  def labelsReportsWithTheActiveTimeZoneAndRestoresLocalOverrides(): Unit = {
    val postgres: Postgres = new Postgres
    val database: Resource[IO, Postgres] = Resource.make(
      IO.blocking(postgres.start()).as(postgres)
    )(_ => IO.blocking(postgres.stop()))

    val scenario: IO[Unit] = database.use { server =>
      Session.single[IO](
        host = "127.0.0.1",
        port = server.port,
        user = "postgres",
        database = "postgres",
        password = Some("reports-secret"),
        parameters = Session.DefaultConnectionParameters ++ Map("TimeZone" -> "UTC")
      ).use { session =>
        for {
          initial <- readReport(session, "UTC")
          _ <- IO(assertEquals(Report("UTC", "2025-01-15 12:00"), initial))
          _ <- session.execute(sql"SET TIME ZONE 'Europe/Berlin'".command)
          regional <- readReport(session, "Europe/Berlin")
          _ <- IO(assertEquals(Report("Europe/Berlin", "2025-01-15 13:00"), regional))
          _ <- session.transaction.use { _ =>
            for {
              // One exported report uses a customer override without changing the session default.
              _ <- session.execute(sql"SET LOCAL TIME ZONE 'America/New_York'".command)
              customer <- readReport(session, "America/New_York")
              _ <- IO(assertEquals(Report("America/New_York", "2025-01-15 07:00"), customer))
            } yield ()
          }
          restored <- readReport(session, "Europe/Berlin")
          _ <- IO(assertEquals(regional, restored))
          _ <- session.execute(sql"SET TIME ZONE 'UTC'".command)
          reset <- readReport(session, "UTC")
          _ <- IO(assertEquals(initial, reset))
        } yield ()
      }
    }
    scenario.timeout(45.seconds).unsafeRunSync()
  }

  private final class Postgres {
    private val name: String = "skunk-report-parameters-" + UUID.randomUUID().toString
    val port: Int = {
      val socket: ServerSocket = new ServerSocket(0)
      try socket.getLocalPort finally socket.close()
    }

    def start(): Unit = {
      try {
        val (exit, output) = docker(
          "run", "--rm", "--pull=never", "-d", "--name", name,
          "-e", "POSTGRES_PASSWORD=reports-secret",
          "-p", s"127.0.0.1:$port:5432", "postgres:18-alpine"
        )
        require(exit == 0, output)
        val deadline: Long = System.nanoTime() + 20.seconds.toNanos
        var ready: Boolean = false
        while (!ready && System.nanoTime() < deadline) {
          ready = docker("exec", name, "pg_isready", "-h", "127.0.0.1", "-U", "postgres")._1 == 0
          if (!ready) Thread.sleep(200)
        }
        require(ready, "PostgreSQL did not become ready")
      } catch {
        case failure: Exception =>
          stop()
          throw failure
      }
    }

    def stop(): Unit = {
      val (exit, output) = docker("rm", "-f", name)
      require(exit == 0, output)
    }

    private def docker(arguments: String*): (Int, String) = {
      val process: Process = new ProcessBuilder((Seq("docker") ++ arguments)*)
        .redirectErrorStream(true).start()
      if (!process.waitFor(10, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        throw new IllegalStateException("Docker command timed out")
      }
      val output: String = new String(process.getInputStream.readAllBytes(), StandardCharsets.US_ASCII)
      (process.exitValue(), output)
    }
  }
}
