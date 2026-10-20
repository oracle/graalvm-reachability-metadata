/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_tpolecat.doobie_hikari_3

import cats.effect.Blocker
import cats.effect.ContextShift
import cats.effect.IO
import cats.effect.Resource
import com.codahale.metrics.MetricRegistry
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import doobie.util.ExecutionContexts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import scala.concurrent.ExecutionContext

/** Exercises Dropwizard pool monitoring through the Hikari transactor.
  * A checkout service records connection acquisitions and returns for its database transactions.
  */
class MonitoredCheckoutPoolTest {
  private implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  @Test
  @Timeout(60)
  def checkoutTransactionsAreReportedAndMetricsAreRemovedOnShutdown(): Unit = {
    val registry: MetricRegistry = new MetricRegistry()
    val poolName: String = "checkout"
    val pool: Resource[IO, HikariTransactor[IO]] = for {
      connectExecutionContext <- ExecutionContexts.fixedThreadPool[IO](2)
      blocker <- Blocker[IO]
      transactor <- HikariTransactor.newHikariTransactor[IO](
        "org.h2.Driver",
        "jdbc:h2:mem:monitored_checkout",
        "sa",
        "",
        connectExecutionContext,
        blocker
      )
    } yield transactor

    pool.use { transactor =>
      for {
        _ <- transactor.configure { dataSource => IO {
          dataSource.setPoolName(poolName)
          dataSource.setMaximumPoolSize(1)
          dataSource.setMinimumIdle(1)
          dataSource.setConnectionTimeout(10000)
          dataSource.setMetricRegistry(registry)
          ()
        }}
        _ <- sql"""CREATE TABLE checkouts (
          order_id INTEGER PRIMARY KEY,
          total_cents INTEGER NOT NULL
        )""".update.run.transact(transactor)
        inserted <- sql"""INSERT INTO checkouts (order_id, total_cents)
          VALUES (101, 1250), (102, 750)
        """.update.run.transact(transactor)
        total <- sql"SELECT SUM(total_cents) FROM checkouts"
          .query[Long].unique.transact(transactor)
        _ <- IO {
          assertEquals(2, inserted)
          assertEquals(2000L, total)
          // Each completed transaction borrows and returns exactly one connection.
          assertEquals(3L, registry.getTimers.get(s"$poolName.pool.Wait").getCount)
          assertEquals(3L, registry.getHistograms.get(s"$poolName.pool.Usage").getCount)
          assertEquals(0L, registry.getMeters.get(s"$poolName.pool.ConnectionTimeoutRate").getCount)
        }
      } yield ()
    }.unsafeRunSync()

    assertTrue(registry.getMetrics.isEmpty, "Closing the transactor must unregister its pool metrics")
  }
}
