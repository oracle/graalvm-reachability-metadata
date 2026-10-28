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
import com.zaxxer.hikari.HikariDataSource
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import doobie.util.ExecutionContexts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import scala.concurrent.ExecutionContext

/** Exercises resource-managed Hikari connection pooling for an inventory service.
  * Separate transactions persist stock changes and read a report before the pool is closed.
  */
class PooledInventoryTest {
  private implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  @Test
  @Timeout(60)
  def inventorySurvivesConnectionReturnsAndPoolCloses(): Unit = {
    val pool: Resource[IO, HikariTransactor[IO]] = for {
      connectExecutionContext <- ExecutionContexts.fixedThreadPool[IO](2)
      blocker <- Blocker[IO]
      transactor <- HikariTransactor.newHikariTransactor[IO](
        "org.h2.Driver",
        "jdbc:h2:mem:pooled_inventory",
        "sa",
        "",
        connectExecutionContext,
        blocker
      )
    } yield transactor

    val closedPool: HikariDataSource = pool.use { transactor =>
      for {
        _ <- transactor.configure { dataSource => IO {
          dataSource.setMaximumPoolSize(1)
          dataSource.setMinimumIdle(1)
          ()
        }}
        _ <- sql"""CREATE TABLE inventory (
          sku VARCHAR(40) PRIMARY KEY,
          quantity INTEGER NOT NULL CHECK (quantity >= 0)
        )""".update.run.transact(transactor)
        inserted <- sql"INSERT INTO inventory (sku, quantity) VALUES (${"coffee"}, ${12})"
          .update.run.transact(transactor)
        shipped <- sql"UPDATE inventory SET quantity = quantity - ${3} WHERE sku = ${"coffee"}"
          .update.run.transact(transactor)
        stock <- sql"SELECT sku, quantity FROM inventory ORDER BY sku"
          .query[(String, Int)].to[List].transact(transactor)
        _ <- IO {
          assertEquals(1, inserted)
          assertEquals(1, shipped)
          assertEquals(List(("coffee", 9)), stock)
          assertTrue(!transactor.kernel.isClosed, "The pool must stay open during the service lifetime")
        }
      } yield transactor.kernel
    }.unsafeRunSync()

    assertTrue(closedPool.isClosed, "Releasing the transactor must close its connection pool")
  }
}
