/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_slick.slick_hikaricp_2_13

import java.sql.Connection

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.health.HealthCheck
import com.codahale.metrics.health.HealthCheckRegistry
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import slick.jdbc.hikaricp.HikariCPJdbcDataSource

import scala.util.Using

/** Exercises HikariCP Dropwizard connectivity health checks for application readiness. */
class PooledReadinessHealthCheckTest {
  @Test
  @Timeout(60)
  def readinessRecoversAfterAnImportReturnsTheOnlyConnection(): Unit = {
    val poolName: String = "import-readiness-pool"
    val healthChecks: HealthCheckRegistry = new HealthCheckRegistry
    val h2: JdbcDataSource = new JdbcDataSource
    h2.setURL("jdbc:h2:mem:pooled_readiness")
    h2.setUser("sa")
    h2.setPassword("")

    // Install the application's readiness registry before starting the pool.
    val config: HikariConfig = new HikariConfig
    config.setDataSource(h2)
    config.setPoolName(poolName)
    config.setMaximumPoolSize(1)
    config.setMinimumIdle(1)
    config.setConnectionTimeout(10000L)
    config.setValidationTimeout(10000L)
    config.setInitializationFailTimeout(10000L)
    config.setHealthCheckRegistry(healthChecks)
    val source: HikariCPJdbcDataSource = new HikariCPJdbcDataSource(new HikariDataSource(config), config)
    val connectivityCheck: String = MetricRegistry.name(poolName, "pool", "ConnectivityCheck")

    try {
      assertTrue(healthChecks.runHealthCheck(connectivityCheck).isHealthy)

      Using.resource(source.createConnection()) { importer: Connection =>
        Using.resource(importer.createStatement()) { statement =>
          statement.setQueryTimeout(10)
          statement.executeUpdate("create table imported_contacts (contact_id integer primary key, name varchar(80))")
          assertEquals(2, statement.executeUpdate("insert into imported_contacts values (1, 'Ada'), (2, 'Grace')"))
        }

        // Keep the sole connection checked out for the entire probe, without a timing race.
        val unavailable: HealthCheck.Result = healthChecks.runHealthCheck(connectivityCheck)
        assertFalse(unavailable.isHealthy)
        assertNotNull(unavailable.getError)
      }

      // Readiness must recover without recreating the application's data source.
      assertTrue(healthChecks.runHealthCheck(connectivityCheck).isHealthy)
      Using.resource(source.createConnection()) { connection: Connection =>
        Using.resource(connection.createStatement()) { statement =>
          statement.setQueryTimeout(10)
          Using.resource(statement.executeQuery("select name from imported_contacts order by contact_id")) { rows =>
            assertTrue(rows.next())
            assertEquals("Ada", rows.getString(1))
            assertTrue(rows.next())
            assertEquals("Grace", rows.getString(1))
            assertFalse(rows.next())
          }
        }
      }
    } finally {
      try source.close()
      finally healthChecks.shutdown()
    }
  }
}
