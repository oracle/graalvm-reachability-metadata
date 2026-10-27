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
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import slick.jdbc.hikaricp.HikariCPJdbcDataSource

import scala.util.Using

/** Exercises HikariCP targeted connection eviction for tenant-local temporary report workspaces. */
class PooledTenantWorkspaceEvictionTest {
  @Test
  @Timeout(60)
  def discardingATenantWorkspaceLetsTheNextTenantBuildAnIsolatedReport(): Unit = {
    val url: String = "jdbc:h2:mem:pooled_tenant_workspaces"
    val h2: JdbcDataSource = new JdbcDataSource
    h2.setURL(url)
    h2.setUser("sa")
    h2.setPassword("")
    val config: Config = ConfigFactory.parseString(s"""
      url = "$url"
      user = "sa"
      password = ""
      maxConnections = 1
      minConnections = 1
      connectionTimeout = 10 seconds
      validationTimeout = 10 seconds
      initializationFailTimeout = 10 seconds
    """)

    // The database must survive retirement of its only application connection.
    Using.resource(h2.getConnection()) { keeper: Connection =>
      Using.resource(keeper.createStatement()) { statement =>
        statement.setQueryTimeout(10)
        statement.executeUpdate("""
          create table payments (
            tenant_id integer not null,
            invoice_id integer primary key,
            cents integer not null
          )
        """)
        assertEquals(3, statement.executeUpdate("insert into payments values (1, 101, 1200), (1, 102, 2300), (2, 201, 900)"))
      }

      val source: HikariCPJdbcDataSource = HikariCPJdbcDataSource.forConfig(
        config, new Driver, "tenant-workspace-pool", getClass.getClassLoader
      )
      try {
        assertEquals(Vector("101:1200", "102:2300"), tenantReport(source, 1))
        // The same workspace name can be used only if the prior session was discarded.
        assertEquals(Vector("201:900"), tenantReport(source, 2))
        // Evicting report sessions must not discard the application's durable input.
        assertEquals(Vector("101:1200", "102:2300"), tenantReport(source, 1))
      } finally {
        source.close()
      }
    }
  }

  private def tenantReport(source: HikariCPJdbcDataSource, tenantId: Int): Vector[String] = {
    Using.resource(source.createConnection()) { connection: Connection =>
      try {
        Using.resource(connection.createStatement()) { statement =>
          statement.setQueryTimeout(10)
          statement.executeUpdate("create local temporary table report_items (invoice_id integer, cents integer)")
        }
        Using.resource(connection.prepareStatement(
          "insert into report_items select invoice_id, cents from payments where tenant_id = ?"
        )) { populate =>
          populate.setQueryTimeout(10)
          populate.setInt(1, tenantId)
          populate.executeUpdate()
        }
        Using.resource(connection.createStatement()) { statement =>
          statement.setQueryTimeout(10)
          Using.resource(statement.executeQuery("select invoice_id, cents from report_items order by invoice_id")) { rows =>
            Iterator.continually(rows.next())
              .takeWhile(identity)
              .map(_ => s"${rows.getInt(1)}:${rows.getInt(2)}")
              .toVector
          }
        }
      } finally {
        // JDBC property reset does not remove local temporary tables. Retire this
        // specific lease, even on a failed report, rather than recycle tenant state.
        source.ds.evictConnection(connection)
      }
    }
  }
}
