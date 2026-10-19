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
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import doobie.util.ExecutionContexts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import scala.concurrent.ExecutionContext

/** Exercises soft eviction and connection replacement in the Hikari transactor's pool.
  * A billing service rotates database sessions during maintenance without losing committed invoices.
  */
class PooledConnectionRotationTest {
  private implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  @Test
  @Timeout(60)
  def maintenanceReplacesTheSessionAndBillingContinues(): Unit = {
    val pool: Resource[IO, HikariTransactor[IO]] = for {
      connectExecutionContext <- ExecutionContexts.fixedThreadPool[IO](2)
      blocker <- Blocker[IO]
      transactor <- HikariTransactor.newHikariTransactor[IO](
        "org.h2.Driver",
        // Keep the embedded database alive while maintenance replaces its last connection.
        "jdbc:h2:mem:rotating_billing_sessions;DB_CLOSE_DELAY=-1",
        "sa",
        "",
        connectExecutionContext,
        blocker
      )
    } yield transactor

    pool.use { transactor =>
      for {
        _ <- transactor.configure { dataSource => IO {
          dataSource.setMaximumPoolSize(1)
          dataSource.setMinimumIdle(1)
          dataSource.setConnectionTimeout(10000)
          ()
        }}
        _ <- (for {
          _ <- sql"""CREATE TABLE invoices (
            invoice_id INTEGER PRIMARY KEY,
            amount_cents INTEGER NOT NULL,
            paid BOOLEAN NOT NULL
          )""".update.run
          _ <- sql"INSERT INTO invoices VALUES (101, 2500, FALSE)".update.run
        } yield ()).transact(transactor)
        originalSession <- sql"SELECT SESSION_ID()".query[Int].unique.transact(transactor)
        _ <- transactor.configure { dataSource => IO {
          dataSource.getHikariPoolMXBean.softEvictConnections()
        }}
        replacementSession <- sql"SELECT SESSION_ID()".query[Int].unique.transact(transactor)
        beforePayment <- sql"SELECT invoice_id, amount_cents, paid FROM invoices ORDER BY invoice_id"
          .query[(Int, Int, Boolean)].to[List].transact(transactor)
        paid <- sql"UPDATE invoices SET paid = TRUE WHERE invoice_id = 101 AND paid = FALSE"
          .update.run.transact(transactor)
        afterPayment <- sql"SELECT invoice_id, amount_cents, paid FROM invoices ORDER BY invoice_id"
          .query[(Int, Int, Boolean)].to[List].transact(transactor)
        _ <- IO {
          assertNotEquals(originalSession, replacementSession, "Maintenance must replace the old session")
          assertEquals(List((101, 2500, false)), beforePayment)
          assertEquals(1, paid)
          assertEquals(List((101, 2500, true)), afterPayment)
        }
        _ <- sql"DROP TABLE invoices".update.run.transact(transactor)
        _ <- sql"SET DB_CLOSE_DELAY 0".update.run.transact(transactor)
      } yield ()
    }.unsafeRunSync()
  }
}
