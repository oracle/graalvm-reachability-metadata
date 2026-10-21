/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_tpolecat.doobie_free_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import doobie.free.connection
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import java.sql.SQLException
import scala.concurrent.duration._

/** Exercises ConnectionIO savepoints to recover a catalog import from a duplicate SKU. */
@Timeout(60)
class SavepointImportTest {
  @Test
  def duplicateRowDoesNotDiscardAcceptedCatalogEntries(): Unit = {
    val transactor: Transactor[IO] = Transactor.fromDriverManager[IO](
      "org.h2.Driver",
      "jdbc:h2:mem:savepoint_import;DB_CLOSE_DELAY=-1",
      "sa",
      ""
    )

    val initialize: connection.ConnectionIO[Unit] =
      for {
        _ <- sql"DROP TABLE IF EXISTS import_catalog".update.run
        _ <- sql"""CREATE TABLE import_catalog (
                    sku VARCHAR(40) PRIMARY KEY,
                    label VARCHAR(100) NOT NULL,
                    quantity INTEGER NOT NULL
                  )""".update.run
      } yield ()

    val importCatalog: connection.ConnectionIO[Unit] =
      for {
        _ <- sql"INSERT INTO import_catalog VALUES ('coffee', 'Ground coffee', 12)".update.run
        checkpoint <- connection.setSavepoint("catalog_row")
        _ <- sql"INSERT INTO import_catalog VALUES ('tea', 'Rejected draft', 99)".update.run
        _ <- sql"INSERT INTO import_catalog VALUES ('coffee', 'Duplicate coffee', 50)".update.run
          .void.handleErrorWith {
            case error: SQLException if error.getSQLState == "23505" =>
              connection.rollback(checkpoint)
            case error => connection.raiseError[Unit](error)
          }
        _ <- connection.releaseSavepoint(checkpoint)
        _ <- sql"INSERT INTO import_catalog VALUES ('tea', 'Green tea', 8)".update.run
      } yield ()

    val readCatalog: connection.ConnectionIO[List[(String, String, Int)]] =
      sql"SELECT sku, label, quantity FROM import_catalog ORDER BY sku"
        .query[(String, String, Int)].to[List]

    val result: List[(String, String, Int)] = (for {
      _ <- initialize.transact(transactor)
      _ <- importCatalog.transact(transactor)
      rows <- readCatalog.transact(transactor)
      _ <- sql"DROP TABLE import_catalog".update.run.transact(transactor)
    } yield rows).timeout(45.seconds).unsafeRunSync()

    assertEquals(
      List(("coffee", "Ground coffee", 12), ("tea", "Green tea", 8)),
      result
    )
  }
}
