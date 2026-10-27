/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_slick.slick_2_13

import java.sql.Types

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import slick.jdbc.H2Profile.api._
import slick.jdbc.meta.MTable

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/** Exercises Slick's JDBC metadata subsystem to validate a stock import schema before loading data.
  * The importer accepts a compatible installation and rejects schema drift without writing rows.
  */
class ImportSchemaInspectionTest {
  class StockTable(tag: Tag, tableName: String)
      extends Table[(String, Int, Option[String])](tag, tableName) {
    def sku: Rep[String] = column[String]("SKU", O.PrimaryKey)
    def quantity: Rep[Int] = column[Int]("QUANTITY")
    def note: Rep[Option[String]] = column[Option[String]]("NOTE")
    def * = (sku, quantity, note)
  }

  private def schemaProblems(tableName: String): DBIO[Vector[String]] = for {
    tables <- MTable.getTables
    table <- tables.find(_.name.name == tableName) match {
      case Some(found) => DBIO.successful(found)
      case None => DBIO.failed(new IllegalArgumentException(s"Missing table: $tableName"))
    }
    columns <- table.getColumns
    keys <- table.getPrimaryKeys
  } yield {
    val requirements: Vector[(String, Int)] = Vector("SKU" -> Types.VARCHAR, "QUANTITY" -> Types.INTEGER)
    val columnProblems: Vector[String] = requirements.flatMap { case (name, sqlType) =>
      columns.find(_.name == name) match {
        case None => Vector(s"Missing column: $name")
        case Some(column) =>
          Vector(
            Option.when(column.sqlType != sqlType)(s"Wrong SQL type: $name"),
            Option.when(column.nullable != Some(false))(s"Column must be required: $name")
          ).flatten
      }
    }
    val keyColumns: Vector[String] = keys.sortBy(_.keySeq).map(_.column)
    columnProblems ++ Option.when(keyColumns != Vector("SKU"))("Primary key must be SKU")
  }

  private def importStock(
      tableName: String,
      rows: Seq[(String, Int, Option[String])]
  ): DBIO[Int] = schemaProblems(tableName).flatMap { problems =>
    if (problems.nonEmpty) {
      DBIO.failed(new IllegalArgumentException(problems.mkString("; ")))
    } else {
      val stock: TableQuery[StockTable] = TableQuery(tag => new StockTable(tag, tableName))
      (stock ++= rows).map(_.getOrElse(0))
    }
  }

  @Test
  @Timeout(60)
  def validatesInstalledSchemaBeforeImportingStock(): Unit = {
    val database: Database = Database.forURL(
      "jdbc:h2:mem:import_schema_inspection;DB_CLOSE_DELAY=-1",
      driver = "org.h2.Driver"
    )
    val ready: TableQuery[StockTable] = TableQuery(tag => new StockTable(tag, "IMPORT_READY"))
    val rows: Seq[(String, Int, Option[String])] = Seq(
      ("PEN", 12, Some("blue ink")),
      ("PAPER", 40, None)
    )
    val setup = DBIO.seq(
      ready.schema.create,
      sqlu"""CREATE TABLE IMPORT_DRAFT (
        SKU VARCHAR(80) NOT NULL,
        QUANTITY VARCHAR(20) NOT NULL,
        NOTE VARCHAR(80)
      )"""
    )
    val cleanup = DBIO.seq(
      sqlu"DROP TABLE IF EXISTS IMPORT_DRAFT",
      ready.schema.dropIfExists
    )
    val scenario = for {
      _ <- setup
      acceptedSchema <- schemaProblems("IMPORT_READY")
      rejectedSchema <- schemaProblems("IMPORT_DRAFT")
      rejected <- importStock("IMPORT_DRAFT", rows).asTry
      untouched <- sql"SELECT COUNT(*) FROM IMPORT_DRAFT".as[Int].head
      inserted <- importStock("IMPORT_READY", rows)
      saved <- ready.sortBy(_.sku).result
    } yield (acceptedSchema, rejectedSchema, rejected, untouched, inserted, saved)

    try {
      val (acceptedSchema, rejectedSchema, rejected, untouched, inserted, saved) = Await.result(
        database.run(scenario.andFinally(cleanup)),
        30.seconds
      )
      assertEquals(Vector.empty[String], acceptedSchema)
      assertEquals(Vector("Wrong SQL type: QUANTITY", "Primary key must be SKU"), rejectedSchema)
      assertTrue(rejected.isFailure)
      assertTrue(rejected.failed.get.isInstanceOf[IllegalArgumentException])
      assertEquals("Wrong SQL type: QUANTITY; Primary key must be SKU", rejected.failed.get.getMessage)
      assertEquals(0, untouched)
      assertEquals(2, inserted)
      assertEquals(rows.sortBy(_._1), saved)
    } finally {
      database.close()
    }
  }
}
