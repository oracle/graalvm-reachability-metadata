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
import com.codahale.metrics.health.HealthCheckRegistry
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import doobie.util.ExecutionContexts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import scala.concurrent.ExecutionContext

/** Exercises Dropwizard connectivity health checks on the Hikari transactor's pool.
  * A reservation service checks database readiness without consuming its only connection.
  */
class PooledReadinessHealthCheckTest {
  private implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  @Test
  @Timeout(60)
  def readinessChecksLeaveThePoolAvailableForReservations(): Unit = {
    val resources: Resource[IO, (HikariTransactor[IO], HealthCheckRegistry)] = for {
      registry <- Resource.make(IO(new HealthCheckRegistry()))(checks => IO(checks.shutdown()))
      connectExecutionContext <- ExecutionContexts.fixedThreadPool[IO](2)
      blocker <- Blocker[IO]
      transactor <- HikariTransactor.newHikariTransactor[IO](
        "org.h2.Driver",
        "jdbc:h2:mem:reservation_readiness",
        "sa",
        "",
        connectExecutionContext,
        blocker
      )
    } yield (transactor, registry)

    resources.use { case (transactor, registry) =>
      val checkName: String = "reservations.pool.ConnectivityCheck"

      for {
        _ <- transactor.configure { dataSource => IO {
          dataSource.setPoolName("reservations")
          dataSource.setMaximumPoolSize(1)
          dataSource.setMinimumIdle(1)
          dataSource.setConnectionTimeout(10000)
          dataSource.addHealthCheckProperty("connectivityCheckTimeoutMs", "10000")
          dataSource.setHealthCheckRegistry(registry)
          ()
        }}
        _ <- sql"""CREATE TABLE reservations (
          reservation_id INTEGER PRIMARY KEY,
          customer VARCHAR(40) NOT NULL
        )""".update.run.transact(transactor)
        _ <- IO {
          assertTrue(registry.getNames.contains(checkName), "The pool must publish its readiness check")
          assertTrue(registry.runHealthCheck(checkName).isHealthy, "The database must be ready")
        }
        // A health probe must return the single connection before application work borrows it.
        inserted <- sql"INSERT INTO reservations (reservation_id, customer) VALUES (${17}, ${"Ada"})"
          .update.run.transact(transactor)
        _ <- IO {
          assertTrue(registry.runHealthCheck(checkName).isHealthy, "Readiness must survive a transaction")
        }
        reservations <- sql"SELECT reservation_id, customer FROM reservations ORDER BY reservation_id"
          .query[(Int, String)].to[List].transact(transactor)
        _ <- IO {
          assertEquals(1, inserted)
          assertEquals(List((17, "Ada")), reservations)
        }
      } yield ()
    }.unsafeRunSync()
  }
}
