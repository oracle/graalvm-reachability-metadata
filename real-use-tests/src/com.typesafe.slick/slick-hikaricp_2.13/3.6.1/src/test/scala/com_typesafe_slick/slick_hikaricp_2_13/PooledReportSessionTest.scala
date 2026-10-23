/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_slick.slick_hikaricp_2_13

import java.sql.Connection

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.h2.Driver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import slick.jdbc.hikaricp.HikariCPJdbcDataSource

import scala.util.Using

/** Exercises HikariCP session defaults and schema isolation between report requests. */
class PooledReportSessionTest {
  @Test
  @Timeout(60)
  def restoresReportSchemaAndIsolationAfterArchiveRequest(): Unit = {
    val config: Config = ConfigFactory.parseString("""
      url = "jdbc:h2:mem:pooled_report_sessions"
      user = "sa"
      password = ""
      maxConnections = 1
      minConnections = 1
      schema = "PUBLIC"
      transactionIsolation = "SERIALIZABLE"
      connectionTimeout = 10 seconds
      validationTimeout = 10 seconds
      initializationFailTimeout = 10 seconds
    """)
    val source: HikariCPJdbcDataSource = HikariCPJdbcDataSource.forConfig(
      config,
      new Driver,
      "report-session-pool",
      getClass.getClassLoader
    )

    try {
      Using.resource(source.createConnection()) { connection: Connection =>
        assertEquals("PUBLIC", connection.getSchema)
        assertEquals(Connection.TRANSACTION_SERIALIZABLE, connection.getTransactionIsolation)
        Using.resource(connection.createStatement()) { statement =>
          statement.executeUpdate("create schema archive")
          statement.executeUpdate("create table public.report_totals (summary varchar(80) not null)")
          statement.executeUpdate("create table archive.report_totals (summary varchar(80) not null)")
          statement.executeUpdate("insert into public.report_totals values ('Current orders: 42')")
          statement.executeUpdate("insert into archive.report_totals values ('Archived orders: 17')")
        }

        // An archive request temporarily changes the context of unqualified report queries.
        connection.setSchema("ARCHIVE")
        connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED)
        assertEquals("Archived orders: 17", readSummary(connection))
      }

      // Returning a connection must not leak the archive request's context to a new request.
      Using.resource(source.createConnection()) { connection: Connection =>
        assertEquals("PUBLIC", connection.getSchema)
        assertEquals(Connection.TRANSACTION_SERIALIZABLE, connection.getTransactionIsolation)
        assertEquals("Current orders: 42", readSummary(connection))
      }
    } finally {
      source.close()
    }
  }

  private def readSummary(connection: Connection): String = {
    Using.resource(connection.createStatement()) { statement =>
      statement.setQueryTimeout(10)
      Using.resource(statement.executeQuery("select summary from report_totals")) { rows =>
        assertTrue(rows.next())
        rows.getString(1)
      }
    }
  }
}
