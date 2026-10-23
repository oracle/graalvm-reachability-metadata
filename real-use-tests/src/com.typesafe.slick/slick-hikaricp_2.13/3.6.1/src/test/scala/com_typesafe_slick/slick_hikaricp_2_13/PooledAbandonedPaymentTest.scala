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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import slick.jdbc.hikaricp.HikariCPJdbcDataSource

import scala.util.Using

/** Exercises HikariCP rollback-on-close cleanup of abandoned payment transactions. */
class PooledAbandonedPaymentTest {
  @Test
  @Timeout(60)
  def anAbandonedDebitDoesNotLeakIntoTheNextPaymentRequest(): Unit = {
    val config: Config = ConfigFactory.parseString("""
      url = "jdbc:h2:mem:pooled_abandoned_payment"
      user = "sa"
      password = ""
      maxConnections = 1
      minConnections = 1
      autoCommit = false
      connectionTimeout = 10 seconds
      validationTimeout = 10 seconds
      initializationFailTimeout = 10 seconds
    """)
    val source: HikariCPJdbcDataSource = HikariCPJdbcDataSource.forConfig(
      config, new Driver, "abandoned-payment-pool", getClass.getClassLoader
    )

    try {
      Using.resource(source.createConnection()) { connection: Connection =>
        Using.resource(connection.createStatement()) { statement =>
          statement.setQueryTimeout(10)
          statement.executeUpdate("create table wallet (id integer primary key, cents integer not null)")
          statement.executeUpdate("create table receipts (reference varchar(32) primary key, cents integer not null)")
          assertEquals(1, statement.executeUpdate("insert into wallet values (1, 10000)"))
        }
        connection.commit()
      }

      val failure: IllegalStateException = assertThrows(classOf[IllegalStateException], () => {
        Using.resource(source.createConnection()) { connection: Connection =>
          assertFalse(connection.getAutoCommit)
          debit(connection, 2500, "abandoned")
          assertEquals(7500, balance(connection))
          // The request fails before committing. Its resource scope returns the lease;
          // transaction cleanup is the pool's responsibility, not a manual rollback here.
          throw new IllegalStateException("Payment authorization unavailable")
        }
        ()
      })
      assertEquals("Payment authorization unavailable", failure.getMessage)

      // With one pooled connection, the next request must receive a clean transaction,
      // not inherit the previous request's debit or accidentally commit its receipt.
      Using.resource(source.createConnection()) { connection: Connection =>
        assertFalse(connection.getAutoCommit)
        assertEquals(10000, balance(connection))
        assertEquals(Vector.empty[(String, Int)], receipts(connection))
        debit(connection, 1800, "accepted")
        connection.commit()
      }

      Using.resource(source.createConnection()) { connection: Connection =>
        assertEquals(8200, balance(connection))
        assertEquals(Vector(("accepted", 1800)), receipts(connection))
        connection.commit()
      }
    } finally {
      source.close()
    }
  }

  private def debit(connection: Connection, cents: Int, reference: String): Unit = {
    Using.resource(connection.prepareStatement("update wallet set cents = cents - ? where id = 1")) { update =>
      update.setQueryTimeout(10)
      update.setInt(1, cents)
      assertEquals(1, update.executeUpdate())
    }
    Using.resource(connection.prepareStatement("insert into receipts values (?, ?)")) { insert =>
      insert.setQueryTimeout(10)
      insert.setString(1, reference)
      insert.setInt(2, cents)
      assertEquals(1, insert.executeUpdate())
    }
  }

  private def balance(connection: Connection): Int = {
    Using.resource(connection.createStatement()) { statement =>
      statement.setQueryTimeout(10)
      Using.resource(statement.executeQuery("select cents from wallet where id = 1")) { rows =>
        assertTrue(rows.next())
        val cents: Int = rows.getInt(1)
        assertFalse(rows.next())
        cents
      }
    }
  }

  private def receipts(connection: Connection): Vector[(String, Int)] = {
    Using.resource(connection.createStatement()) { statement =>
      statement.setQueryTimeout(10)
      Using.resource(statement.executeQuery("select reference, cents from receipts order by reference")) { rows =>
        Iterator.continually(rows.next())
          .takeWhile(identity)
          .map(_ => (rows.getString(1), rows.getInt(2)))
          .toVector
      }
    }
  }
}
