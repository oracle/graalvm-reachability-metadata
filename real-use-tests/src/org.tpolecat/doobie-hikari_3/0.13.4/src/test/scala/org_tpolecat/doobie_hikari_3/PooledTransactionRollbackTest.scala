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
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import doobie.util.ExecutionContexts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import java.sql.SQLException
import scala.concurrent.ExecutionContext

/** Exercises transaction rollback and failure recovery through the Hikari transactor.
  * A rejected account transfer must leave both balances intact and the pool usable.
  */
class PooledTransactionRollbackTest {
  private implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  private def transfer(amount: Int): ConnectionIO[(Int, Int)] = for {
    debited <- sql"UPDATE accounts SET balance = balance - $amount WHERE account_id = ${1}".update.run
    credited <- sql"UPDATE accounts SET balance = balance + $amount WHERE account_id = ${2}".update.run
  } yield (debited, credited)

  @Test
  @Timeout(60)
  def failedTransferRollsBackBeforeNextBorrower(): Unit = {
    val pool: Resource[IO, HikariTransactor[IO]] = for {
      connectExecutionContext <- ExecutionContexts.fixedThreadPool[IO](2)
      blocker <- Blocker[IO]
      transactor <- HikariTransactor.newHikariTransactor[IO](
        "org.h2.Driver",
        "jdbc:h2:mem:transfer_rollback",
        "sa",
        "",
        connectExecutionContext,
        blocker
      )
    } yield transactor

    pool.use { transactor =>
      val balances: ConnectionIO[List[(Int, Int)]] =
        sql"SELECT account_id, balance FROM accounts ORDER BY account_id".query[(Int, Int)].to[List]

      for {
        _ <- transactor.configure { dataSource => IO {
          dataSource.setMaximumPoolSize(1)
          dataSource.setMinimumIdle(1)
          ()
        }}
        _ <- (for {
          _ <- sql"""CREATE TABLE accounts (
            account_id INTEGER PRIMARY KEY,
            balance INTEGER NOT NULL CHECK (balance BETWEEN 0 AND 100)
          )""".update.run
          _ <- sql"INSERT INTO accounts (account_id, balance) VALUES (1, 100), (2, 90)".update.run
        } yield ()).transact(transactor)
        // The debit succeeds, but the credit exceeds the recipient's balance limit.
        rejected <- transfer(30).transact(transactor).attempt
        afterRejection <- balances.transact(transactor)
        _ <- IO {
          rejected match {
            case Left(error: SQLException) => assertEquals("23513", error.getSQLState)
            case other => fail(s"Expected a balance constraint violation, received $other")
          }
          assertEquals(List((1, 100), (2, 90)), afterRejection)
        }
        accepted <- transfer(10).transact(transactor)
        afterSuccess <- balances.transact(transactor)
        _ <- IO {
          assertEquals((1, 1), accepted)
          assertEquals(List((1, 90), (2, 100)), afterSuccess)
        }
      } yield ()
    }.unsafeRunSync()
  }
}
