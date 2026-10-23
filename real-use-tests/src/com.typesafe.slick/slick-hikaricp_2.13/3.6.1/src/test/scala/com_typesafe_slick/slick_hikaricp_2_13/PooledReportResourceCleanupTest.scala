/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_slick.slick_hikaricp_2_13

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.h2.Driver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import slick.jdbc.hikaricp.HikariCPJdbcDataSource

import scala.util.Using

/** Exercises HikariCP statement and cursor cleanup when a report request returns its connection. */
class PooledReportResourceCleanupTest {
  @Test
  @Timeout(60)
  def unfinishedInvoicePreviewReleasesResourcesBeforeTheNextRequest(): Unit = {
    val config: Config = ConfigFactory.parseString("""
      url = "jdbc:h2:mem:pooled_report_resource_cleanup"
      user = "sa"
      password = ""
      maxConnections = 1
      minConnections = 1
      connectionTimeout = 10 seconds
      validationTimeout = 10 seconds
      initializationFailTimeout = 10 seconds
    """)
    val source: HikariCPJdbcDataSource = HikariCPJdbcDataSource.forConfig(
      config,
      new Driver,
      "report-resource-cleanup-pool",
      getClass.getClassLoader
    )

    try {
      val unfinished: (PreparedStatement, ResultSet) = Using.resource(source.createConnection()) {
        connection: Connection =>
          Using.resource(connection.createStatement()) { setup =>
            setup.setQueryTimeout(10)
            setup.executeUpdate("create table invoices (id integer primary key, cents integer not null)")
            assertEquals(3, setup.executeUpdate("insert into invoices values (1, 1200), (2, 2300), (3, 3400)"))
          }

          // This legacy report scopes its resources to the connection lease. The preview
          // stops after one row rather than exhausting or explicitly closing the cursor.
          val query: PreparedStatement = connection.prepareStatement("select id, cents from invoices order by id")
          query.setQueryTimeout(10)
          val rows: ResultSet = query.executeQuery()
          assertTrue(rows.next())
          assertEquals(1, rows.getInt(1))
          assertEquals(1200, rows.getInt(2))
          assertFalse(query.isClosed)
          assertFalse(rows.isClosed)
          (query, rows)
      }

      // Closing a Hikari lease must release outstanding JDBC resources, not merely
      // put a connection with an unfinished report back into circulation.
      assertTrue(unfinished._1.isClosed, "The preview statement must be closed on lease return")
      assertTrue(unfinished._2.isClosed, "The preview cursor must be closed with its statement")

      // The same single-connection pool can immediately serve the invoice correction
      // and a complete report after the earlier preview has stopped consuming rows.
      Using.resource(source.createConnection()) { connection: Connection =>
        Using.resource(connection.createStatement()) { statement =>
          statement.setQueryTimeout(10)
          assertEquals(1, statement.executeUpdate("update invoices set cents = 2500 where id = 2"))
          Using.resource(statement.executeQuery("select count(*), sum(cents) from invoices")) { totals =>
            assertTrue(totals.next())
            assertEquals(3, totals.getInt(1))
            assertEquals(7100, totals.getInt(2))
            assertFalse(totals.next())
          }
        }
      }
    } finally {
      source.close()
    }
  }
}
