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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

/** Exercises cancellation of in-flight transactions and connection reclamation through the Hikari transactor.
  * A cancelled job leaves its work pending so another worker can finish it using the same pool.
  */
class PooledJobCancellationTest {
  private implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  private implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)

  @Test
  @Timeout(60)
  def cancelledJobRollsBackAndReturnsTheOnlyConnection(): Unit = {
    val pool: Resource[IO, HikariTransactor[IO]] = for {
      connectExecutionContext <- ExecutionContexts.fixedThreadPool[IO](2)
      blocker <- Blocker[IO]
      transactor <- HikariTransactor.newHikariTransactor[IO](
        "org.h2.Driver",
        "jdbc:h2:mem:cancelled_jobs",
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
          _ <- sql"""CREATE TABLE jobs (
            job_id INTEGER PRIMARY KEY,
            status VARCHAR(20) NOT NULL
          )""".update.run
          _ <- sql"INSERT INTO jobs (job_id, status) VALUES (1, 'pending')".update.run
        } yield ()).transact(transactor)
        started <- Deferred[IO, Unit]
        cancelledJob: ConnectionIO[Unit] = for {
          _ <- sql"UPDATE jobs SET status = 'running' WHERE job_id = 1".update.run
          _ <- LiftIO[ConnectionIO].liftIO(started.complete(()))
          // Model a worker waiting for an external response without committing its transaction.
          _ <- LiftIO[ConnectionIO].liftIO[Unit](IO.never)
        } yield ()
        _ <- Resource.make(cancelledJob.transact(transactor).start)(_.cancel).use { worker =>
          for {
            // Cancel only after the write has executed, rather than racing transaction startup.
            _ <- started.get.timeout(10.seconds)
            _ <- worker.cancel
            afterCancellation <- sql"SELECT status FROM jobs WHERE job_id = 1"
              .query[String].unique.transact(transactor)
            completed <- sql"""UPDATE jobs SET status = 'complete'
              WHERE job_id = 1 AND status = 'pending'
            """.update.run.transact(transactor)
            afterRetry <- sql"SELECT status FROM jobs WHERE job_id = 1"
              .query[String].unique.transact(transactor)
            _ <- IO {
              assertEquals("pending", afterCancellation)
              assertEquals(1, completed)
              assertEquals("complete", afterRetry)
            }
          } yield ()
        }
      } yield ()
    }.unsafeRunSync()
  }
}
