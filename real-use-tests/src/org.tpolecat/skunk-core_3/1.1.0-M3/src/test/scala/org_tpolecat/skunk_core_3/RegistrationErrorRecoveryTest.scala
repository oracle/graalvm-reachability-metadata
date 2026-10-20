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
import skunk.Command
import skunk.Query
import skunk.Session
import skunk.SqlState
import skunk.Void
import skunk.codec.all.*
import skunk.implicits.*

import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.*

/** Exercises Skunk SQL-state error handling to reject invalid registrations and recover the session. */
class RegistrationErrorRecoveryTest {
  private given Tracer[IO] = Tracer.noop[IO]

  private enum Registration {
    case Accepted, EmailTaken, Underage
  }

  private val insertAccount: Command[(String, Int)] =
    sql"INSERT INTO accounts (email, age) VALUES ($text, $int4)".command

  private val readAccounts: Query[Void, (String, Int)] =
    sql"SELECT email, age FROM accounts ORDER BY email".query(text ~ int4)

  @Test
  @Timeout(60)
  def translatesConstraintErrorsAndContinuesAcceptingRegistrations(): Unit = {
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
        password = Some("registration-secret")
      ).use { session =>
        for {
          _ <- session.execute(sql"""CREATE TEMP TABLE accounts (
            email text CONSTRAINT accounts_email_key PRIMARY KEY,
            age integer NOT NULL CONSTRAINT accounts_adult CHECK (age >= 18))""".command)
          save <- session.prepare(insertAccount)
          outcomes <- {
            // Match only constraints owned by this registration workflow.
            // All other database failures remain errors rather than user rejections.
            def register(email: String, age: Int): IO[Registration] =
              save.execute((email, age)).as[Registration](Registration.Accepted).recover {
                case SqlState.UniqueViolation(error)
                    if error.constraintName.contains("accounts_email_key") => Registration.EmailTaken
                case SqlState.CheckViolation(error)
                    if error.constraintName.contains("accounts_adult") => Registration.Underage
              }

            for {
              accepted <- register("alice@example.test", 29)
              duplicate <- register("alice@example.test", 41)
              underage <- register("bob@example.test", 12)
              corrected <- register("bob@example.test", 18)
            } yield List(accepted, duplicate, underage, corrected)
          }
          stored <- session.execute(readAccounts)
          _ <- IO {
            assertEquals(
              List(Registration.Accepted, Registration.EmailTaken, Registration.Underage, Registration.Accepted),
              outcomes
            )
            assertEquals(List(("alice@example.test", 29), ("bob@example.test", 18)), stored)
          }
        } yield ()
      }
    }
    scenario.timeout(45.seconds).unsafeRunSync()
  }

  private final class Postgres {
    private val name: String = "skunk-registration-" + UUID.randomUUID().toString
    def port: Int = {
      val (exit, output) = docker("port", name, "5432/tcp")
      require(exit == 0, output)
      output.trim.split(":").last.toInt
    }

    def start(): Unit = {
      try {
        val (exit, output) = docker(
          "run", "--rm", "--pull=never", "-d", "--name", name,
          "-e", "POSTGRES_PASSWORD=registration-secret",
          "-p", "127.0.0.1::5432", "postgres:18-alpine"
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
