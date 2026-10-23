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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import slick.jdbc.hikaricp.HikariCPJdbcDataSource

import scala.util.Using

/** Exercises HikariCP connection initialization SQL for application audit attribution. */
class PooledAuditInitializationTest {
  @Test
  @Timeout(60)
  def growingThePoolInitializesEveryAuditWriter(): Unit = {
    val config: Config = ConfigFactory.parseString("""
      url = "jdbc:h2:mem:pooled_audit_initialization"
      user = "sa"
      password = ""
      maxConnections = 2
      minConnections = 1
      connectionInitSql = "SET @audit_origin = 'billing-service'"
      connectionTimeout = 10 seconds
      validationTimeout = 10 seconds
      initializationFailTimeout = 10 seconds
    """)
    val source: HikariCPJdbcDataSource = HikariCPJdbcDataSource.forConfig(
      config,
      new Driver,
      "audit-initialization-pool",
      getClass.getClassLoader
    )

    try {
      Using.resource(source.createConnection()) { checkout: Connection =>
        Using.resource(checkout.createStatement()) { statement =>
          statement.setQueryTimeout(10)
          statement.executeUpdate("""
            create table audit_events (
              event_name varchar(80) primary key,
              origin varchar(80) not null
            )
          """)
        }
        recordEvent(checkout, "invoice-created")

        // Keeping checkout leased forces the background writer onto a different connection.
        Using.resource(source.createConnection()) { background: Connection =>
          recordEvent(background, "receipt-sent")
        }

        // Audit attribution comes from each connection's initialized SQL session, not a parameter.
        Using.resource(checkout.createStatement()) { statement =>
          statement.setQueryTimeout(10)
          Using.resource(statement.executeQuery(
            "select event_name, origin from audit_events order by event_name"
          )) { rows =>
            val events: Vector[(String, String)] = Iterator.continually(rows.next())
              .takeWhile(identity)
              .map(_ => (rows.getString(1), rows.getString(2)))
              .toVector
            assertEquals(Vector(
              ("invoice-created", "billing-service"),
              ("receipt-sent", "billing-service")
            ), events)
          }
        }
      }
    } finally {
      source.close()
    }
  }

  private def recordEvent(connection: Connection, event: String): Unit = {
    Using.resource(connection.prepareStatement(
      "insert into audit_events (event_name, origin) values (?, @audit_origin)"
    )) { statement =>
      statement.setQueryTimeout(10)
      statement.setString(1, event)
      assertEquals(1, statement.executeUpdate())
    }
  }
}
