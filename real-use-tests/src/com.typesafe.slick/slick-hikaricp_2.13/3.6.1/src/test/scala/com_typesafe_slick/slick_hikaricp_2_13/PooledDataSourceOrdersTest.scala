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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import slick.jdbc.hikaricp.HikariCPJdbcDataSource

import scala.util.Using

/** Exercises HikariCP DataSource-class configuration and property forwarding for order processing. */
class PooledDataSourceOrdersTest {
  @Test
  @Timeout(60)
  def checkoutAndFulfillmentShareTheConfiguredDatabase(): Unit = {
    val config: Config = ConfigFactory.parseString("""
      dataSourceClassName = "org.h2.jdbcx.JdbcDataSource"
      properties {
        URL = "jdbc:h2:mem:datasource_orders"
        user = "sa"
        password = ""
      }
      maxConnections = 2
      minConnections = 1
      connectionTimeout = 10 seconds
      validationTimeout = 10 seconds
      initializationFailTimeout = 10 seconds
    """)
    val source: HikariCPJdbcDataSource = HikariCPJdbcDataSource.forConfig(
      config,
      new Driver,
      "datasource-orders-pool",
      getClass.getClassLoader
    )

    try {
      Using.resource(source.createConnection()) { checkout: Connection =>
        Using.resource(checkout.createStatement()) { statement =>
          statement.setQueryTimeout(10)
          statement.executeUpdate("""
            create table customer_orders (
              order_id integer primary key,
              customer varchar(80) not null,
              status varchar(20) not null
            )
          """)
        }
        Using.resource(checkout.prepareStatement(
          "insert into customer_orders (order_id, customer, status) values (?, ?, ?)"
        )) { insert =>
          insert.setQueryTimeout(10)
          insert.setInt(1, 1042)
          insert.setString(2, "Ada")
          insert.setString(3, "paid")
          assertEquals(1, insert.executeUpdate())
        }

        // Holding checkout forces fulfillment to use a second physical connection.
        Using.resource(source.createConnection()) { fulfillment: Connection =>
          assertEquals("Ada:paid", readOrder(fulfillment, 1042))
          Using.resource(fulfillment.prepareStatement(
            "update customer_orders set status = ? where order_id = ? and status = ?"
          )) { update =>
            update.setQueryTimeout(10)
            update.setString(1, "shipped")
            update.setInt(2, 1042)
            update.setString(3, "paid")
            assertEquals(1, update.executeUpdate())
          }
        }
        assertEquals("Ada:shipped", readOrder(checkout, 1042))
      }
    } finally {
      source.close()
    }
  }

  private def readOrder(connection: Connection, orderId: Int): String = {
    Using.resource(connection.prepareStatement(
      "select customer, status from customer_orders where order_id = ?"
    )) { query =>
      query.setQueryTimeout(10)
      query.setInt(1, orderId)
      Using.resource(query.executeQuery()) { rows =>
        assertTrue(rows.next())
        val order: String = s"${rows.getString(1)}:${rows.getString(2)}"
        assertFalse(rows.next())
        order
      }
    }
  }
}
