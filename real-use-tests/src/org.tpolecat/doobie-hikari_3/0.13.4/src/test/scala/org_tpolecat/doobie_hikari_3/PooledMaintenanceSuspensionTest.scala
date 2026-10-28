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
import com.zaxxer.hikari.HikariConfig
import doobie.ConnectionIO
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import doobie.util.ExecutionContexts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

/** Exercises pool suspension and resumption for maintenance through the Hikari transactor.
  * A fulfillment service drains an existing dispatch before reopening database admission.
  */
class PooledMaintenanceSuspensionTest {
  private implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  private implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)

  @Test
  @Timeout(60)
  def inFlightDispatchCommitsDuringSuspensionAndNewWorkRunsAfterResumption(): Unit = {
    val pool: Resource[IO, HikariTransactor[IO]] = for {
      connectExecutionContext <- ExecutionContexts.fixedThreadPool[IO](2)
      blocker <- Blocker[IO]
      config <- Resource.eval(IO {
        val settings: HikariConfig = new HikariConfig()
        settings.setDriverClassName("org.h2.Driver")
        settings.setJdbcUrl("jdbc:h2:mem:maintenance_dispatch")
        settings.setUsername("sa")
        settings.setPassword("")
        settings.setMaximumPoolSize(1)
        settings.setMinimumIdle(1)
        settings.setConnectionTimeout(10000)
        settings.setAllowPoolSuspension(true)
        settings
      })
      transactor <- HikariTransactor.fromHikariConfig[IO](config, connectExecutionContext, blocker)
    } yield transactor

    pool.use { transactor =>
      for {
        _ <- (for {
          _ <- sql"""CREATE TABLE dispatches (
            dispatch_id INTEGER PRIMARY KEY,
            status VARCHAR(20) NOT NULL
          )""".update.run
          _ <- sql"INSERT INTO dispatches VALUES (1, 'pending'), (2, 'pending')".update.run
        } yield ()).transact(transactor)
        dispatchStarted <- Deferred[IO, Unit]
        finishDispatch <- Deferred[IO, Unit]
        dispatch: ConnectionIO[Int] = for {
          updated <- sql"UPDATE dispatches SET status = 'sent' WHERE dispatch_id = 1".update.run
          _ <- LiftIO[ConnectionIO].liftIO(dispatchStarted.complete(()))
          _ <- LiftIO[ConnectionIO].liftIO(finishDispatch.get.timeout(20.seconds))
        } yield updated
        _ <- Resource.make(dispatch.transact(transactor).start)(_.cancel).use { worker =>
          for {
            // Suspension starts only after the dispatch has borrowed the sole connection.
            _ <- dispatchStarted.get.timeout(10.seconds)
            _ <- Resource.make(
              transactor.configure(source => IO(source.getHikariPoolMXBean.suspendPool()))
            )(_ => transactor.configure(source => IO(source.getHikariPoolMXBean.resumePool())))
              .use { _ =>
                for {
                  _ <- finishDispatch.complete(())
                  // Existing borrowers must be able to commit and return while admission is closed.
                  completed <- worker.join.timeout(10.seconds)
                  _ <- IO(assertEquals(1, completed))
                } yield ()
              }
          } yield ()
        }
        afterMaintenance <- sql"SELECT dispatch_id, status FROM dispatches ORDER BY dispatch_id"
          .query[(Int, String)].to[List].transact(transactor)
        nextDispatch <- sql"""UPDATE dispatches SET status = 'sent'
          WHERE dispatch_id = 2 AND status = 'pending'
        """.update.run.transact(transactor)
        afterResumption <- sql"SELECT dispatch_id, status FROM dispatches ORDER BY dispatch_id"
          .query[(Int, String)].to[List].transact(transactor)
        _ <- IO {
          assertEquals(List((1, "sent"), (2, "pending")), afterMaintenance)
          assertEquals(1, nextDispatch)
          assertEquals(List((1, "sent"), (2, "sent")), afterResumption)
        }
      } yield ()
    }.unsafeRunSync()
  }
}
