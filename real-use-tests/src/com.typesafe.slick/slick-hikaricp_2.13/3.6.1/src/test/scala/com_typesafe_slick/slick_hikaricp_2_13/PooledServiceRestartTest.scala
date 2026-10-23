/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_slick.slick_hikaricp_2_13

import java.sql.Connection
import java.sql.SQLException

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.h2.Driver
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import slick.jdbc.hikaricp.HikariCPJdbcDataSource

import scala.util.Using

/** Exercises HikariCP pool shutdown and replacement during an application service restart. */
class PooledServiceRestartTest {
  @Test
  @Timeout(60)
  def replacementServiceResumesWorkAndRetiredServiceRejectsNewRequests(): Unit = {
    val url: String = "jdbc:h2:mem:pooled_service_restart"
    val database: JdbcDataSource = new JdbcDataSource
    database.setURL(url)
    database.setUser("sa")
    database.setPassword("")
    val config: Config = ConfigFactory.parseString(s"""
      url = "$url"
      user = "sa"
      password = ""
      maxConnections = 1
      minConnections = 0
      connectionTimeout = 10 seconds
      validationTimeout = 10 seconds
      initializationFailTimeout = 10 seconds
    """)

    // A database outlives an application deployment. This connection keeps the
    // in-memory database alive across both pools, and releases it on test exit.
    Using.resource(database.getConnection()) { _: Connection =>
      val retiring: HikariCPJdbcDataSource = HikariCPJdbcDataSource.forConfig(
        config, new Driver, "retiring-dispatch-service", getClass.getClassLoader
      )
      try {
        Using.resource(retiring.createConnection()) { connection: Connection =>
          Using.resource(connection.createStatement()) { statement =>
            statement.setQueryTimeout(10)
            statement.executeUpdate("create table dispatch_jobs (id integer primary key, status varchar(24))")
            statement.executeUpdate("insert into dispatch_jobs values (1, 'sent'), (2, 'pending')")
          }
        }
      } finally {
        retiring.close()
      }

      // Stale request handlers must fail rather than silently reopening the pool.
      assertThrows(classOf[SQLException], () => {
        Using.resource(retiring.createConnection()) { _: Connection => () }
      })

      val replacement: HikariCPJdbcDataSource = HikariCPJdbcDataSource.forConfig(
        config, new Driver, "replacement-dispatch-service", getClass.getClassLoader
      )
      try {
        Using.resource(replacement.createConnection()) { connection: Connection =>
          Using.resource(connection.createStatement()) { statement =>
            statement.setQueryTimeout(10)
            Using.resource(statement.executeQuery("select id from dispatch_jobs where status = 'pending'")) { rows =>
              assertTrue(rows.next())
              assertEquals(2, rows.getInt(1))
              assertFalse(rows.next())
            }
            assertEquals(1, statement.executeUpdate("update dispatch_jobs set status = 'sent' where id = 2"))
          }
        }
        // Verify the resumed work through a fresh application lease.
        Using.resource(replacement.createConnection()) { connection: Connection =>
          Using.resource(connection.createStatement()) { statement =>
            statement.setQueryTimeout(10)
            Using.resource(statement.executeQuery("select count(*) from dispatch_jobs where status = 'sent'")) { rows =>
              assertTrue(rows.next())
              assertEquals(2, rows.getInt(1))
              assertFalse(rows.next())
            }
          }
        }
      } finally {
        replacement.close()
      }
    }
  }
}
