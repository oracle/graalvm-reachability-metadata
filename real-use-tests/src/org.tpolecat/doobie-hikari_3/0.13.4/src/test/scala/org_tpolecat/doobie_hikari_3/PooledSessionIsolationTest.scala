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
import doobie.ConnectionIO
import doobie.free.connection as FC
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import doobie.util.ExecutionContexts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import java.sql.Connection
import scala.concurrent.ExecutionContext

/** Exercises pooled JDBC session-state reset between request-specific transaction isolation levels.
  * A serializable repricing job must not change the isolation level of subsequent catalog requests.
  */
class PooledSessionIsolationTest {
  private implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  @Test
  @Timeout(60)
  def repricingIsolationDoesNotLeakToTheNextCatalogRequest(): Unit = {
    val pool: Resource[IO, HikariTransactor[IO]] = for {
      connectExecutionContext <- ExecutionContexts.fixedThreadPool[IO](2)
      blocker <- Blocker[IO]
      transactor <- HikariTransactor.newHikariTransactor[IO](
        "org.h2.Driver",
        "jdbc:h2:mem:catalog_session_isolation",
        "sa",
        "",
        connectExecutionContext,
        blocker
      )
    } yield transactor

    pool.use { transactor =>
      val reprice: ConnectionIO[(Int, Int)] = for {
        _ <- FC.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE)
        isolation <- FC.getTransactionIsolation
        updated <- sql"UPDATE catalog SET price_cents = price_cents + 100".update.run
      } yield (isolation, updated)

      val catalogRequest: ConnectionIO[(Int, List[(String, Int)])] = for {
        isolation <- FC.getTransactionIsolation
        products <- sql"SELECT sku, price_cents FROM catalog ORDER BY sku"
          .query[(String, Int)].to[List]
      } yield (isolation, products)

      for {
        _ <- transactor.configure { dataSource => IO {
          // Reuse one physical connection so a request cannot hide dirty state behind another one.
          dataSource.setMaximumPoolSize(1)
          dataSource.setMinimumIdle(1)
          dataSource.setConnectionTimeout(10000)
          dataSource.setTransactionIsolation("TRANSACTION_READ_COMMITTED")
          ()
        }}
        _ <- (for {
          _ <- sql"""CREATE TABLE catalog (
            sku VARCHAR(40) PRIMARY KEY,
            price_cents INTEGER NOT NULL
          )""".update.run
          _ <- sql"INSERT INTO catalog (sku, price_cents) VALUES ('coffee', 500), ('tea', 300)"
            .update.run
        } yield ()).transact(transactor)
        initial <- catalogRequest.transact(transactor)
        repriced <- reprice.transact(transactor)
        subsequent <- catalogRequest.transact(transactor)
        _ <- IO {
          assertEquals(Connection.TRANSACTION_READ_COMMITTED, initial._1)
          assertEquals(List(("coffee", 500), ("tea", 300)), initial._2)
          assertEquals((Connection.TRANSACTION_SERIALIZABLE, 2), repriced)
          assertEquals(Connection.TRANSACTION_READ_COMMITTED, subsequent._1)
          assertEquals(List(("coffee", 600), ("tea", 400)), subsequent._2)
        }
      } yield ()
    }.unsafeRunSync()
  }
}
