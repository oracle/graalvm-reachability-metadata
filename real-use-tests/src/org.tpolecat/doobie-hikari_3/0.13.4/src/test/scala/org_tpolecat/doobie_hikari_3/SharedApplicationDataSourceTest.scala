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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import scala.concurrent.ExecutionContext

/** Exercises adaptation of an application-owned Hikari data source into Doobie transactors.
  * An outbox producer and dispatcher share a pool whose lifetime belongs to the application.
  */
class SharedApplicationDataSourceTest {
  private implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  @Test
  @Timeout(60)
  def producerAndDispatcherShareTheApplicationOwnedPool(): Unit = {
    val application: Resource[IO, (HikariDataSource, HikariTransactor[IO], HikariTransactor[IO])] = for {
      connectExecutionContext <- ExecutionContexts.fixedThreadPool[IO](2)
      blocker <- Blocker[IO]
      dataSource <- Resource.make(IO {
        val source: HikariDataSource = new HikariDataSource()
        source.setDriverClassName("org.h2.Driver")
        source.setJdbcUrl("jdbc:h2:mem:shared_application_outbox")
        source.setUsername("sa")
        source.setPassword("")
        source.setMaximumPoolSize(1)
        source.setMinimumIdle(1)
        source.setConnectionTimeout(10000)
        source
      })(source => IO(source.close()))
    } yield (
      dataSource,
      HikariTransactor[IO](dataSource, connectExecutionContext, blocker),
      HikariTransactor[IO](dataSource, connectExecutionContext, blocker)
    )

    val releasedSource: HikariDataSource = application.use { case (dataSource, producer, dispatcher) =>
      for {
        _ <- sql"""CREATE TABLE outbox (
          message_id INTEGER PRIMARY KEY,
          payload VARCHAR(80) NOT NULL,
          delivered BOOLEAN NOT NULL
        )""".update.run.transact(producer)
        _ <- sql"""INSERT INTO outbox (message_id, payload, delivered)
          VALUES (1, 'order-confirmed:101', FALSE)
        """.update.run.transact(producer)
        pending <- sql"SELECT message_id, payload FROM outbox WHERE delivered = FALSE ORDER BY message_id"
          .query[(Int, String)].to[List].transact(dispatcher)
        delivered <- sql"UPDATE outbox SET delivered = TRUE WHERE message_id = 1 AND delivered = FALSE"
          .update.run.transact(dispatcher)
        _ <- IO {
          assertEquals(List((1, "order-confirmed:101")), pending)
          assertEquals(1, delivered)
          assertFalse(dataSource.isClosed, "Finishing component work must not close the shared pool")
        }
        // The producer can still use the pool after the dispatcher's transaction has finished.
        _ <- sql"""INSERT INTO outbox (message_id, payload, delivered)
          VALUES (2, 'order-confirmed:102', FALSE)
        """.update.run.transact(producer)
        messages <- sql"SELECT message_id, payload, delivered FROM outbox ORDER BY message_id"
          .query[(Int, String, Boolean)].to[List].transact(dispatcher)
        _ <- IO {
          assertEquals(
            List((1, "order-confirmed:101", true), (2, "order-confirmed:102", false)),
            messages
          )
        }
      } yield dataSource
    }.unsafeRunSync()

    assertTrue(releasedSource.isClosed, "Application shutdown must release the shared data source")
  }
}
