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

/** Exercises concurrent connection allocation through the Hikari transactor.
  * Two ingestion workers update independent counters without sharing uncommitted transaction state.
  */
class ConcurrentPooledWorkersTest {
  private implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  private implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)

  private def ingest(
    workerId: Int,
    peerId: Int,
    observed: Deferred[IO, (Int, Int)],
    commit: Deferred[IO, Unit]
  ): ConnectionIO[Int] = for {
    updated <- sql"UPDATE ingestion_counts SET processed = processed + 10 WHERE worker_id = $workerId"
      .update.run
    session <- sql"SELECT SESSION_ID()".query[Int].unique
    peerCount <- sql"SELECT processed FROM ingestion_counts WHERE worker_id = $peerId"
      .query[Int].unique
    _ <- LiftIO[ConnectionIO].liftIO(observed.complete((session, peerCount)))
    // Keep both transactions open until the application has checked their independent views.
    _ <- LiftIO[ConnectionIO].liftIO(commit.get.timeout(20.seconds))
  } yield updated

  @Test
  @Timeout(60)
  def simultaneousWorkersUseIndependentConnectionsAndCommitBothUpdates(): Unit = {
    val pool: Resource[IO, HikariTransactor[IO]] = for {
      connectExecutionContext <- ExecutionContexts.fixedThreadPool[IO](2)
      blocker <- Blocker[IO]
      transactor <- HikariTransactor.newHikariTransactor[IO](
        "org.h2.Driver",
        "jdbc:h2:mem:concurrent_ingestion_workers",
        "sa",
        "",
        connectExecutionContext,
        blocker
      )
    } yield transactor

    pool.use { transactor =>
      for {
        _ <- transactor.configure { dataSource => IO {
          dataSource.setMaximumPoolSize(2)
          dataSource.setMinimumIdle(0)
          dataSource.setConnectionTimeout(10000)
          dataSource.setTransactionIsolation("TRANSACTION_READ_COMMITTED")
          ()
        }}
        _ <- (for {
          _ <- sql"""CREATE TABLE ingestion_counts (
            worker_id INTEGER PRIMARY KEY,
            processed INTEGER NOT NULL
          )""".update.run
          _ <- sql"INSERT INTO ingestion_counts VALUES (1, 0), (2, 0)".update.run
        } yield ()).transact(transactor)
        firstObserved <- Deferred[IO, (Int, Int)]
        secondObserved <- Deferred[IO, (Int, Int)]
        commit <- Deferred[IO, Unit]
        workers = for {
          first <- Resource.make(
            ingest(1, 2, firstObserved, commit).transact(transactor).start
          )(_.cancel)
          second <- Resource.make(
            ingest(2, 1, secondObserved, commit).transact(transactor).start
          )(_.cancel)
        } yield (first, second)
        _ <- workers.use { case (first, second) =>
          for {
            firstView <- firstObserved.get.timeout(10.seconds)
            secondView <- secondObserved.get.timeout(10.seconds)
            _ <- IO {
              assertNotEquals(firstView._1, secondView._1, "Concurrent workers need separate sessions")
              assertEquals(0, firstView._2, "A worker must not see its peer's uncommitted counter")
              assertEquals(0, secondView._2, "A worker must not see its peer's uncommitted counter")
            }
            _ <- commit.complete(())
            firstUpdated <- first.join.timeout(10.seconds)
            secondUpdated <- second.join.timeout(10.seconds)
            _ <- IO {
              assertEquals(1, firstUpdated)
              assertEquals(1, secondUpdated)
            }
          } yield ()
        }
        // A later request can reuse the pool and sees both workers' committed progress.
        progress <- sql"SELECT worker_id, processed FROM ingestion_counts ORDER BY worker_id"
          .query[(Int, Int)].to[List].transact(transactor)
        _ <- IO(assertEquals(List((1, 10), (2, 10)), progress))
      } yield ()
    }.unsafeRunSync()
  }
}
