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
import doobie.Query0
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import doobie.util.ExecutionContexts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import scala.concurrent.ExecutionContext

/** Exercises streaming query resource finalization through the Hikari transactor.
  * A truncated shipment export releases the only pooled connection for a subsequent request.
  */
class PooledStreamingExportTest {
  private implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  @Test
  @Timeout(60)
  def truncatedExportReturnsConnectionForTheNextRequest(): Unit = {
    val pool: Resource[IO, HikariTransactor[IO]] = for {
      connectExecutionContext <- ExecutionContexts.fixedThreadPool[IO](2)
      blocker <- Blocker[IO]
      transactor <- HikariTransactor.newHikariTransactor[IO](
        "org.h2.Driver",
        "jdbc:h2:mem:streaming_shipments",
        "sa",
        "",
        connectExecutionContext,
        blocker
      )
    } yield transactor

    pool.use { transactor =>
      val shipments: Query0[(Int, String)] =
        sql"SELECT shipment_id, destination FROM shipments ORDER BY shipment_id"
        .query[(Int, String)]

      for {
        _ <- transactor.configure { dataSource => IO {
          dataSource.setMaximumPoolSize(1)
          dataSource.setMinimumIdle(1)
          dataSource.setConnectionTimeout(10000)
          ()
        }}
        _ <- (for {
          _ <- sql"""CREATE TABLE shipments (
            shipment_id INTEGER PRIMARY KEY,
            destination VARCHAR(40) NOT NULL
          )""".update.run
          _ <- sql"""INSERT INTO shipments (shipment_id, destination) VALUES
            (1, 'Paris'), (2, 'Oslo'), (3, 'Rome'),
            (4, 'Lima'), (5, 'Tokyo'), (6, 'Accra')
          """.update.run
        } yield ()).transact(transactor)
        preview <- shipments.streamWithChunkSize(2).transact(transactor)
          .map { case (id, destination) => s"$id,$destination" }
          .take(3)
          .compile.toList
        // With one connection, this request cannot run unless the truncated stream released it.
        changed <- sql"UPDATE shipments SET destination = ${"Kyoto"} WHERE shipment_id = ${5}"
          .update.run.transact(transactor)
        completeExport <- shipments.streamWithChunkSize(2).transact(transactor)
          .map { case (id, destination) => s"$id,$destination" }
          .compile.toList
        _ <- IO {
          assertEquals(List("1,Paris", "2,Oslo", "3,Rome"), preview)
          assertEquals(1, changed)
          assertEquals(
            List("1,Paris", "2,Oslo", "3,Rome", "4,Lima", "5,Kyoto", "6,Accra"),
            completeExport
          )
        }
      } yield ()
    }.unsafeRunSync()
  }
}
