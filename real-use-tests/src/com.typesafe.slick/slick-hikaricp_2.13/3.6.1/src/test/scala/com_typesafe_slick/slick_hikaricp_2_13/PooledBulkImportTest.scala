/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_slick.slick_hikaricp_2_13

import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.util.UUID
import java.util.concurrent.TimeUnit

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.postgresql.Driver
import org.postgresql.PGConnection
import slick.jdbc.hikaricp.HikariCPJdbcDataSource

import scala.concurrent.duration._
import scala.util.Using

/** Exercises HikariCP connection unwrapping for PostgreSQL COPY-based shipment imports. */
class PooledBulkImportTest {
  @Test
  @Timeout(60)
  def importsCsvThroughTheDriverAndReturnsTheLeaseForShipmentQueries(): Unit = {
    val container: String = "slick-shipment-import-" + UUID.randomUUID().toString
    try {
      command("docker", "run", "--detach", "--name", container,
        "--publish", "127.0.0.1::5432",
        "--env", "POSTGRES_DB=shipments",
        "--env", "POSTGRES_USER=importer",
        "--env", "POSTGRES_PASSWORD=import-secret", "postgres:18-alpine")
      awaitDatabase(container)
      val binding: String = command("docker", "port", container, "5432/tcp").trim
      val port: Int = binding.substring(binding.lastIndexOf(':') + 1).toInt
      val config: Config = ConfigFactory.parseString(s"""
        url = "jdbc:postgresql://127.0.0.1:$port/shipments?connectTimeout=10&socketTimeout=10"
        user = "importer"
        password = "import-secret"
        maxConnections = 1
        minConnections = 1
        connectionTimeout = 10 seconds
        validationTimeout = 10 seconds
        initializationFailTimeout = 10 seconds
      """)
      val source: HikariCPJdbcDataSource = HikariCPJdbcDataSource.forConfig(
        config, new Driver, "shipment-bulk-import-pool", getClass.getClassLoader
      )
      try {
        Using.resource(source.createConnection()) { connection: Connection =>
          Using.resource(connection.createStatement()) { statement =>
            statement.setQueryTimeout(10)
            statement.executeUpdate("""
              create table shipments (
                reference varchar(40) primary key,
                destination varchar(80) not null,
                parcels integer not null check (parcels > 0)
              )
            """)
          }
          connection.setAutoCommit(false)
          try {
            // COPY needs the driver's public extension, not a cast of the pooled proxy.
            val postgres: PGConnection = connection.unwrap(classOf[PGConnection])
            val imported: Long = Using.resource(new StringReader(
              "S-101,\"Portland, OR\",2\nS-102,Boston,3\nS-103,Austin,1\n"
            )) { csv: StringReader =>
              postgres.getCopyAPI.copyIn(
                "COPY shipments (reference, destination, parcels) FROM STDIN WITH (FORMAT csv)", csv
              )
            }
            assertEquals(3L, imported)
            connection.commit()
          } catch {
            case failure: Exception =>
              connection.rollback()
              throw failure
          }
          // Close only the application lease; the unwrapped connection belongs to the pool.
        }

        // A one-connection pool must remain usable after driver-specific bulk loading.
        Using.resource(source.createConnection()) { connection: Connection =>
          assertTrue(connection.getAutoCommit)
          Using.resource(connection.createStatement()) { statement =>
            statement.setQueryTimeout(10)
            Using.resource(statement.executeQuery(
              "select reference, destination, parcels from shipments order by reference"
            )) { rows =>
              val shipments: Vector[(String, String, Int)] = Iterator.continually(rows.next())
                .takeWhile(identity)
                .map(_ => (rows.getString(1), rows.getString(2), rows.getInt(3)))
                .toVector
              assertEquals(Vector(
                ("S-101", "Portland, OR", 2),
                ("S-102", "Boston", 3),
                ("S-103", "Austin", 1)
              ), shipments)
            }
          }
        }
      } finally {
        source.close()
      }
    } finally {
      command("docker", "rm", "-f", "-v", container)
    }
  }

  private def awaitDatabase(container: String): Unit = {
    val deadline: Deadline = 20.seconds.fromNow
    var ready: Boolean = false
    while (!ready && deadline.hasTimeLeft()) {
      val probe: Process = new ProcessBuilder(
        "docker", "exec", container, "pg_isready", "-h", "127.0.0.1", "-U", "importer", "-d", "shipments"
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
