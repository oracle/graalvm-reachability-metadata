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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import java.sql.SQLTransientConnectionException
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

/** Exercises connection-acquisition timeouts under Hikari transactor pool exhaustion.
  * An overloaded booking service rejects a waiting request and accepts a retry after capacity returns.
  */
class PooledAcquisitionTimeoutTest {
  private implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  private implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)

  @Test
  @Timeout(60)
  def timedOutBookingDoesNotExecuteAndCanBeRetriedAfterCapacityReturns(): Unit = {
    val pool: Resource[IO, HikariTransactor[IO]] = for {
      connectExecutionContext <- ExecutionContexts.fixedThreadPool[IO](2)
      blocker <- Blocker[IO]
      transactor <- HikariTransactor.newHikariTransactor[IO](
        "org.h2.Driver",
        "jdbc:h2:mem:booking_acquisition_timeout",
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
        _ <- sql"CREATE TABLE bookings (booking_id INTEGER PRIMARY KEY, customer VARCHAR(40) NOT NULL)"
          .update.run.transact(transactor)
        occupied <- Deferred[IO, Unit]
        release <- Deferred[IO, Unit]
        activeBooking: ConnectionIO[Int] = for {
          inserted <- sql"INSERT INTO bookings VALUES (1, 'alice')".update.run
          _ <- LiftIO[ConnectionIO].liftIO(occupied.complete(()))
          // Keep the only connection until the waiting request has exhausted its acquisition budget.
          _ <- LiftIO[ConnectionIO].liftIO(release.get.timeout(30.seconds))
        } yield inserted
        retryableBooking: ConnectionIO[Int] = sql"INSERT INTO bookings VALUES (2, 'bob')".update.run
        _ <- Resource.make(activeBooking.transact(transactor).start)(_.cancel).use { active =>
          for {
            _ <- occupied.get.timeout(10.seconds)
            rejected <- retryableBooking.transact(transactor).attempt.timeout(20.seconds)
            _ <- IO {
              assertTrue(rejected.isLeft, "An exhausted pool must reject the waiting booking")
              rejected match {
                case Left(error) => assertTrue(
                  error.isInstanceOf[SQLTransientConnectionException],
                  s"Expected a retryable connection-acquisition failure, got $error"
                )
                case Right(_) => ()
              }
            }
            _ <- release.complete(())
            accepted <- active.join.timeout(10.seconds)
            beforeRetry <- sql"SELECT booking_id, customer FROM bookings ORDER BY booking_id"
              .query[(Int, String)].to[List].transact(transactor)
            retried <- retryableBooking.transact(transactor)
            afterRetry <- sql"SELECT booking_id, customer FROM bookings ORDER BY booking_id"
              .query[(Int, String)].to[List].transact(transactor)
            _ <- IO {
              assertEquals(1, accepted)
              assertEquals(List((1, "alice")), beforeRetry, "The rejected request must not execute SQL")
              assertEquals(1, retried)
              assertEquals(List((1, "alice"), (2, "bob")), afterRetry)
            }
          } yield ()
        }
      } yield ()
    }.unsafeRunSync()
  }
}
