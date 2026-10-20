/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_tpolecat.doobie_free_3

import cats.effect.IO
import cats.effect.syntax.all._
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import doobie.free.{connection, databasemetadata, resultset}
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import java.sql.{DatabaseMetaData, Types}
import scala.concurrent.duration._

/** Exercises database metadata discovery to check a shipping application's schema compatibility. */
@Timeout(60)
class SchemaCompatibilityTest {
  private case class Column(name: String, sqlType: Int, nullable: Boolean)
  private case class Schema(tables: List[String], columns: List[Column], primaryKey: List[String])

  private def readRows[A](row: resultset.ResultSetIO[A]): resultset.ResultSetIO[List[A]] =
    resultset.next.flatMap { available =>
      if (available) {
        for {
          value <- row
          remaining <- readRows(row)
        } yield value :: remaining
      } else {
        resultset.pure(List.empty[A])
      }
    }

  @Test
  def discoversRequiredColumnsAndShipmentIdentity(): Unit = {
    val transactor: Transactor[IO] = Transactor.fromDriverManager[IO](
      "org.h2.Driver",
      "jdbc:h2:mem:schema_compatibility;DB_CLOSE_DELAY=-1",
      "sa",
      ""
    )

    val initialize: connection.ConnectionIO[Unit] = for {
      _ <- sql"DROP TABLE IF EXISTS shipment_manifest".update.run
      _ <- sql"""CREATE TABLE shipment_manifest (
                  shipment_id INTEGER PRIMARY KEY,
                  destination VARCHAR(120) NOT NULL,
                  delivery_note VARCHAR(200)
                )""".update.run
    } yield ()

    val readColumn: resultset.ResultSetIO[Column] = for {
      name <- resultset.getString("COLUMN_NAME")
      sqlType <- resultset.getInt("DATA_TYPE")
      nullable <- resultset.getInt("NULLABLE")
    } yield Column(name, sqlType, nullable == DatabaseMetaData.columnNullable)

    val inspect: connection.ConnectionIO[Schema] = for {
      metadata <- connection.getMetaData
      schema <- connection.embed(metadata, for {
        tables <- databasemetadata.getTables(null, "PUBLIC", "SHIPMENT_MANIFEST", Array("TABLE"))
          .bracket { rows =>
            databasemetadata.embed(rows, readRows(resultset.getString("TABLE_NAME")))
          }(rows => databasemetadata.embed(rows, resultset.close))
        columns <- databasemetadata.getColumns(null, "PUBLIC", "SHIPMENT_MANIFEST", null)
          .bracket { rows =>
            databasemetadata.embed(rows, readRows(readColumn))
          }(rows => databasemetadata.embed(rows, resultset.close))
        primaryKey <- databasemetadata.getPrimaryKeys(null, "PUBLIC", "SHIPMENT_MANIFEST")
          .bracket { rows =>
            databasemetadata.embed(rows, readRows(resultset.getString("COLUMN_NAME")))
          }(rows => databasemetadata.embed(rows, resultset.close))
      } yield Schema(tables, columns, primaryKey))
    } yield schema

    val discovered: Schema = (for {
      _ <- initialize.transact(transactor)
      schema <- inspect.transact(transactor)
      _ <- sql"DROP TABLE shipment_manifest".update.run.transact(transactor)
    } yield schema).timeout(45.seconds).unsafeRunSync()

    assertEquals(List("SHIPMENT_MANIFEST"), discovered.tables)
    assertEquals(
      List(
        Column("SHIPMENT_ID", Types.INTEGER, false),
        Column("DESTINATION", Types.VARCHAR, false),
        Column("DELIVERY_NOTE", Types.VARCHAR, true)
      ),
      discovered.columns
    )
    assertEquals(List("SHIPMENT_ID"), discovered.primaryKey)
  }
}
