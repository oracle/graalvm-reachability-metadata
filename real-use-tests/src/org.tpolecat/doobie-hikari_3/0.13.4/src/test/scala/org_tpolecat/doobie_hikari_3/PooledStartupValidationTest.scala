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
import cats.effect.concurrent.Ref
import com.zaxxer.hikari.HikariConfig
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import doobie.util.ExecutionContexts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import java.sql.SQLException
import scala.concurrent.ExecutionContext

/** Exercises fail-fast startup validation of the Hikari transactor's connection initialization.
  * A delivery service refuses to start before its schema migration and starts after migration succeeds.
  */
class PooledStartupValidationTest {
  private implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  @Test
  @Timeout(60)
  def missingSchemaPreventsStartupUntilMigrationCompletes(): Unit = {
    val databaseUrl: String = "jdbc:h2:mem:delivery_startup_validation"
    val infrastructure: Resource[IO, (HikariTransactor[IO], Resource[IO, HikariTransactor[IO]])] = for {
      connectExecutionContext <- ExecutionContexts.fixedThreadPool[IO](2)
      blocker <- Blocker[IO]
      administrator <- HikariTransactor.newHikariTransactor[IO](
        "org.h2.Driver", databaseUrl, "sa", "", connectExecutionContext, blocker
      )
    } yield {
      val service: Resource[IO, HikariTransactor[IO]] = for {
        config <- Resource.eval(IO {
          val settings: HikariConfig = new HikariConfig()
          settings.setDriverClassName("org.h2.Driver")
          settings.setJdbcUrl(databaseUrl)
          settings.setUsername("sa")
          settings.setPassword("")
          settings.setMaximumPoolSize(1)
          settings.setMinimumIdle(1)
          settings.setConnectionTimeout(10000)
          settings.setInitializationFailTimeout(10000)
          settings.setConnectionInitSql("SET SCHEMA DELIVERY")
          settings
        })
        transactor <- HikariTransactor.fromHikariConfig[IO](config, connectExecutionContext, blocker)
      } yield transactor
      (administrator, service)
    }

    infrastructure.use { case (administrator, service) =>
      for {
        // Open the administrator pool and keep the embedded database alive throughout deployment.
        _ <- sql"SELECT 1".query[Int].unique.transact(administrator)
        acceptingRequests <- Ref.of[IO, Boolean](false)
        startup <- service.use(_ => acceptingRequests.set(true)).attempt
        acceptedBeforeMigration <- acceptingRequests.get
        _ <- IO {
          assertFalse(acceptedBeforeMigration, "The service must not accept requests before pool startup")
          startup match {
            case Left(error) => error.getCause match {
              case cause: SQLException => assertEquals("90079", cause.getSQLState)
              case other => fail(s"Expected a missing-schema startup failure, received $other")
            }
            case Right(_) => fail("Pool startup must reject an unavailable initialization schema")
          }
        }
        _ <- (for {
          _ <- sql"CREATE SCHEMA DELIVERY".update.run
          _ <- sql"CREATE TABLE DELIVERY.parcels (parcel_id INTEGER PRIMARY KEY, destination VARCHAR(40))"
            .update.run
        } yield ()).transact(administrator)
        _ <- service.use { transactor =>
          for {
            _ <- acceptingRequests.set(true)
            inserted <- sql"INSERT INTO parcels VALUES (71, 'Oslo')".update.run.transact(transactor)
            schema <- sql"SELECT CURRENT_SCHEMA".query[String].unique.transact(transactor)
            parcels <- sql"SELECT parcel_id, destination FROM parcels ORDER BY parcel_id"
              .query[(Int, String)].to[List].transact(transactor)
            _ <- IO {
              assertEquals(1, inserted)
              assertEquals("DELIVERY", schema)
              assertEquals(List((71, "Oslo")), parcels)
            }
          } yield ()
        }
        persisted <- sql"SELECT parcel_id, destination FROM DELIVERY.parcels ORDER BY parcel_id"
          .query[(Int, String)].to[List].transact(administrator)
        _ <- IO(assertEquals(List((71, "Oslo")), persisted))
      } yield ()
    }.unsafeRunSync()
  }
}
