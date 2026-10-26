/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_slick.slick_hikaricp_2_13

import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.sql.SQLException
import java.util.Properties
import java.util.UUID
import java.util.concurrent.TimeUnit

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.postgresql.Driver
import slick.jdbc.hikaricp.HikariCPJdbcDataSource

import scala.concurrent.duration._
import scala.util.Using

/** Exercises HikariCP fatal SQL exception eviction and connection replacement after session loss. */
class PooledSessionFailureRecoveryTest {
  @Test
  @Timeout(60)
  def interruptedJobLookupDoesNotPreventTheNextRequestFromCompletingWork(): Unit = {
    val container: String = "slick-session-recovery-" + UUID.randomUUID().toString
    try {
      command("docker", "run", "--detach", "--name", container,
        "--publish", "127.0.0.1::5432",
        "--env", "POSTGRES_DB=dispatch",
        "--env", "POSTGRES_USER=dispatcher",
        "--env", "POSTGRES_PASSWORD=dispatch-secret", "postgres:18-alpine")
      awaitDatabase(container)
      val binding: String = command("docker", "port", container, "5432/tcp").trim
      val port: Int = binding.substring(binding.lastIndexOf(':') + 1).toInt
      val url: String = s"jdbc:postgresql://127.0.0.1:$port/dispatch?connectTimeout=10&socketTimeout=10"
      val config: Config = ConfigFactory.parseString(s"""
        url = "$url"
        user = "dispatcher"
        password = "dispatch-secret"
        maxConnections = 1
        minConnections = 1
        connectionTimeout = 10 seconds
        validationTimeout = 10 seconds
        initializationFailTimeout = 10 seconds
      """)
      val driver: Driver = new Driver
      val source: HikariCPJdbcDataSource = HikariCPJdbcDataSource.forConfig(
        config, driver, "dispatch-session-recovery-pool", getClass.getClassLoader
      )
      try {
        Using.resource(source.createConnection()) { request: Connection =>
          Using.resource(request.createStatement()) { statement =>
            statement.setQueryTimeout(10)
            statement.executeUpdate("create table jobs (id integer primary key, status varchar(24) not null)")
            assertEquals(1, statement.executeUpdate("insert into jobs values (42, 'pending')"))
            assertEquals("pending", readStatus(request))
            val backend: Int = Using.resource(statement.executeQuery("select pg_backend_pid()")) { rows =>
              assertTrue(rows.next())
              rows.getInt(1)
            }

            // Model an operator terminating a service session. PostgreSQL waits for
            // termination to finish, so the next query cannot race the shutdown.
            val credentials: Properties = new Properties
            credentials.setProperty("user", "dispatcher")
            credentials.setProperty("password", "dispatch-secret")
            Using.resource(driver.connect(url, credentials)) { administrator: Connection =>
              Using.resource(administrator.prepareStatement("select pg_terminate_backend(?, 10000)")) { terminate =>
                terminate.setQueryTimeout(10)
                terminate.setInt(1, backend)
                Using.resource(terminate.executeQuery()) { rows =>
                  assertTrue(rows.next())
                  assertTrue(rows.getBoolean(1), "The service session must have terminated")
                }
              }
            }

            // The in-flight read fails normally; the pooled statement classifies
            // the fatal SQL state and retires its connection without manual eviction.
            val failure: SQLException = assertThrows(classOf[SQLException], () => {
              Using.resource(statement.executeQuery("select status from jobs where id = 42")) { _ => () }
            })
            val state: String = failure.getSQLState
            assertTrue(state != null && (state == "57P01" || state.startsWith("08")),
              s"Expected a terminated-session SQL state, got $state")
          }
        }

        // Use the same one-connection pool, not a replacement data source or an
        // explicit eviction. Committed work survives the lost database session.
        Using.resource(source.createConnection()) { nextRequest: Connection =>
          assertEquals("pending", readStatus(nextRequest))
          Using.resource(nextRequest.createStatement()) { statement =>
            statement.setQueryTimeout(10)
            assertEquals(1, statement.executeUpdate("update jobs set status = 'sent' where id = 42"))
          }
        }
        Using.resource(source.createConnection()) { confirmation: Connection =>
          assertEquals("sent", readStatus(confirmation))
        }
      } finally {
        source.close()
      }
    } finally {
      command("docker", "rm", "-f", "-v", container)
    }
  }

  private def readStatus(connection: Connection): String = {
    Using.resource(connection.createStatement()) { statement =>
      statement.setQueryTimeout(10)
      Using.resource(statement.executeQuery("select status from jobs where id = 42")) { rows =>
        assertTrue(rows.next())
        val status: String = rows.getString(1)
        assertFalse(rows.next())
        status
      }
    }
  }

  private def awaitDatabase(container: String): Unit = {
    val deadline: Deadline = 20.seconds.fromNow
    var ready: Boolean = false
    while (!ready && deadline.hasTimeLeft()) {
      val probe: Process = new ProcessBuilder(
        "docker", "exec", container, "pg_isready", "-h", "127.0.0.1", "-U", "dispatcher", "-d", "dispatch"
      ).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()
      try {
        assertTrue(probe.waitFor(10, TimeUnit.SECONDS), "Database readiness probe timed out")
        ready = probe.exitValue() == 0
      } finally {
        if (probe.isAlive) probe.destroyForcibly()
      }
      if (!ready && deadline.hasTimeLeft()) Thread.sleep(100)
    }
    assertTrue(ready, "PostgreSQL did not become ready within 20 seconds")
  }

  private def command(arguments: String*): String = {
    val process: Process = new ProcessBuilder(arguments: _*).redirectErrorStream(true).start()
    try {
      assertTrue(process.waitFor(10, TimeUnit.SECONDS), s"Command timed out: ${arguments.mkString(" ")}")
      val output: String = Using.resource(process.getInputStream) { stream =>
        new String(stream.readAllBytes(), StandardCharsets.UTF_8)
      }
      assertEquals(0, process.exitValue(), output)
      output
    } finally {
      if (process.isAlive) process.destroyForcibly()
    }
  }
}
