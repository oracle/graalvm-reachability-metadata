/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_slick.slick_hikaricp_2_13

import java.sql.Connection
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import slick.jdbc.hikaricp.HikariCPJdbcDataSource

import scala.util.Using

/** Exercises HikariCP pool suspension and resumption during shipping-rate maintenance. */
class PooledMaintenanceSuspensionTest {
  @Test
  @Timeout(60)
  def queuedReportSeesCommittedRatesAfterMaintenanceResumesThePool(): Unit = {
    val h2: JdbcDataSource = new JdbcDataSource
    h2.setURL("jdbc:h2:mem:pooled_maintenance_suspension")
    h2.setUser("sa")
    h2.setPassword("")

    val config: HikariConfig = new HikariConfig
    config.setDataSource(h2)
    config.setPoolName("shipping-maintenance-pool")
    config.setMaximumPoolSize(2)
    config.setMinimumIdle(2)
    config.setAllowPoolSuspension(true)
    config.setConnectionTimeout(10000L)
    config.setValidationTimeout(10000L)
    config.setInitializationFailTimeout(10000L)
    val source: HikariCPJdbcDataSource = new HikariCPJdbcDataSource(new HikariDataSource(config), config)
    val reports: ExecutorService = Executors.newSingleThreadExecutor()

    try {
      val report: Future[Vector[String]] = Using.resource(source.createConnection()) { maintenance: Connection =>
        Using.resource(maintenance.createStatement()) { statement =>
          statement.setQueryTimeout(10)
          statement.executeUpdate("create table shipping_rates (zone varchar(32) primary key, cents integer not null)")
          assertEquals(2, statement.executeUpdate("insert into shipping_rates values ('local', 500), ('regional', 900)"))
        }

        source.ds.getHikariPoolMXBean.suspendPool()
        try {
          val requestStarted: CountDownLatch = new CountDownLatch(1)
          val pending: Future[Vector[String]] = reports.submit(new Callable[Vector[String]] {
            override def call(): Vector[String] = {
              requestStarted.countDown()
              Using.resource(source.createConnection()) { connection: Connection =>
                readRates(connection)
              }
            }
          })
          assertTrue(requestStarted.await(10, TimeUnit.SECONDS), "Report worker did not start")

          // The existing lease remains usable, but new requests wait at pool acquisition.
          // Capacity is two, so it is suspension rather than an exhausted pool that gates reports.
          assertEquals(Vector("local:500", "regional:900"), readRates(maintenance))
          maintenance.setAutoCommit(false)
          Using.resource(maintenance.createStatement()) { statement =>
            statement.setQueryTimeout(10)
            assertEquals(2, statement.executeUpdate("update shipping_rates set cents = cents + 100"))
          }
          maintenance.commit()
          pending
        } finally {
          // A failed migration must also release waiting requests rather than strand their threads.
          source.ds.getHikariPoolMXBean.resumePool()
        }
      }

      assertEquals(Vector("local:600", "regional:1000"), report.get(15, TimeUnit.SECONDS))
      Using.resource(source.createConnection()) { connection: Connection =>
        assertEquals(Vector("local:600", "regional:1000"), readRates(connection))
      }
    } finally {
      reports.shutdownNow()
      try {
        assertTrue(reports.awaitTermination(10, TimeUnit.SECONDS), "Report worker did not terminate")
      } finally {
        source.close()
      }
    }
  }

  private def readRates(connection: Connection): Vector[String] = {
    Using.resource(connection.createStatement()) { statement =>
      statement.setQueryTimeout(10)
      Using.resource(statement.executeQuery("select zone, cents from shipping_rates order by zone")) { rows =>
        Iterator.continually(rows.next())
          .takeWhile(identity)
          .map(_ => s"${rows.getString(1)}:${rows.getInt(2)}")
          .toVector
      }
    }
  }
}
