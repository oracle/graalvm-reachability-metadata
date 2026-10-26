/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_slick.slick_hikaricp_2_13

import java.sql.Connection

import com.codahale.metrics.Histogram
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.Timer
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.h2.Driver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import slick.jdbc.hikaricp.HikariCPJdbcDataSource

import scala.util.Using

/** Exercises HikariCP Dropwizard metrics integration for monitoring a pooled sales import. */
class PooledImportMetricsTest {
  @Test
  @Timeout(60)
  def reportsAcquiredAndReturnedConnectionsForImportAndSummary(): Unit = {
    val poolName: String = "sales-import-pool"
    val registry: MetricRegistry = new MetricRegistry
    val config: Config = ConfigFactory.parseString("""
      url = "jdbc:h2:mem:pooled_import_metrics"
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
      poolName,
      getClass.getClassLoader
    )

    try {
      source.ds.setMetricRegistry(registry)
      val acquisitions: Timer = registry.getTimers.get(MetricRegistry.name(poolName, "pool", "Wait"))
      val returns: Histogram = registry.getHistograms.get(MetricRegistry.name(poolName, "pool", "Usage"))
      assertNotNull(acquisitions)
      assertNotNull(returns)
      assertEquals(0L, acquisitions.getCount)
      assertEquals(0L, returns.getCount)

      Using.resource(source.createConnection()) { connection: Connection =>
        Using.resource(connection.createStatement()) { statement =>
          statement.setQueryTimeout(10)
          statement.executeUpdate("create table imported_sales (sale_id integer primary key, cents integer not null)")
          assertEquals(2, statement.executeUpdate("insert into imported_sales values (101, 1250), (102, 2750)"))
        }
        assertEquals(1L, acquisitions.getCount)
        assertEquals(0L, returns.getCount)
      }
      assertEquals(1L, returns.getCount)

      Using.resource(source.createConnection()) { connection: Connection =>
        Using.resource(connection.createStatement()) { statement =>
          statement.setQueryTimeout(10)
          Using.resource(statement.executeQuery("select count(*), sum(cents) from imported_sales")) { rows =>
            assertTrue(rows.next())
            assertEquals(2, rows.getInt(1))
            assertEquals(4000, rows.getInt(2))
          }
        }
        assertEquals(2L, acquisitions.getCount)
        assertEquals(1L, returns.getCount)
      }

      // Count completed operations, not elapsed durations or asynchronously refreshed gauges.
      assertEquals(2L, acquisitions.getCount)
      assertEquals(2L, returns.getCount)
    } finally {
      source.close()
    }

    // A stopped pool must no longer appear in the application's monitoring registry.
    assertTrue(registry.getMetrics.isEmpty)
  }
}
