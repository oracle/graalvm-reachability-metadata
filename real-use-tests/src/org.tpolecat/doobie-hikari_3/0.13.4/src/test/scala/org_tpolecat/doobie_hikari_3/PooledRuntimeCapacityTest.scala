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
import cats.effect.LiftIO
import cats.effect.Resource
import cats.effect.Timer
import cats.effect.concurrent.Deferred
import doobie.ConnectionIO
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import doobie.util.ExecutionContexts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

/** Exercises runtime pool-capacity reconfiguration through the Hikari transactor.
  * A settlement service adds capacity for an urgent payment while a batch holds its original connection.
  */
class PooledRuntimeCapacityTest {
  private implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  private implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)

  @Test
  @Timeout(60)
  def capacityIncreaseAdmitsUrgentPaymentWithoutInterruptingTheBatch(): Unit = {
    val pool: Resource[IO, HikariTransactor[IO]] = for {
      connectExecutionContext <- ExecutionContexts.fixedThreadPool[IO](2)
      blocker <- Blocker[IO]
      transactor <- HikariTransactor.newHikariTransactor[IO](
        "org.h2.Driver",
        "jdbc:h2:mem:runtime_settlement_capacity",
        "sa",
        "",
        connectExecutionContext,
        blocker
      )
    } yield transactor

    pool.use { transactor =>
      val settlements: ConnectionIO[List[(Int, String)]] =
        sql"SELECT payment_id, status FROM settlements ORDER BY payment_id"
          .query[(Int, String)].to[List]

      for {
        _ <- transactor.configure { source => IO {
          source.setMaximumPoolSize(1)
          source.setMinimumIdle(1)
          source.setConnectionTimeout(10000)
          source.setTransactionIsolation("TRANSACTION_READ_COMMITTED")
          ()
        }}
        _ <- (for {
          _ <- sql"""CREATE TABLE settlements (
            payment_id INTEGER PRIMARY KEY,
            status VARCHAR(20) NOT NULL
          )""".update.run
          _ <- sql"INSERT INTO settlements VALUES (1, 'pending'), (2, 'pending')".update.run
        } yield ()).transact(transactor)
        batchStarted <- Deferred[IO, Int]
        releaseBatch <- Deferred[IO, Unit]
        batch: ConnectionIO[Int] = for {
          changed <- sql"UPDATE settlements SET status = 'settled' WHERE payment_id = 1".update.run
          session <- sql"SELECT SESSION_ID()".query[Int].unique
          _ <- LiftIO[ConnectionIO].liftIO(batchStarted.complete(session))
          _ <- LiftIO[ConnectionIO].liftIO(releaseBatch.get.timeout(30.seconds))
        } yield changed
        _ <- Resource.make(batch.transact(transactor).start)(_.cancel).use { worker =>
          for {
            originalSession <- batchStarted.get.timeout(10.seconds)
            // Tune the running pool, not a replacement transactor or a startup configuration.
            _ <- transactor.configure { source => IO {
              source.getHikariConfigMXBean.setMaximumPoolSize(2)
            }}
            urgent <- (for {
              session <- sql"SELECT SESSION_ID()".query[Int].unique
              changed <- sql"UPDATE settlements SET status = 'settled' WHERE payment_id = 2".update.run
            } yield (session, changed)).transact(transactor).timeout(10.seconds)
            beforeBatchCommit <- settlements.transact(transactor).timeout(10.seconds)
            _ <- IO {
              assertNotEquals(originalSession, urgent._1, "Extra capacity must use another session")
              assertEquals(1, urgent._2)
              assertEquals(
                List((1, "pending"), (2, "settled")),
                beforeBatchCommit,
                "Urgent work must commit while the original batch still owns its connection"
              )
            }
            _ <- releaseBatch.complete(())
            batchChanged <- worker.join.timeout(10.seconds)
            _ <- IO(assertEquals(1, batchChanged))
          } yield ()
        }
        completed <- settlements.transact(transactor)
        _ <- IO(assertEquals(List((1, "settled"), (2, "settled")), completed))
      } yield ()
    }.unsafeRunSync()
  }
}
