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
import doobie.free.{connection, preparedstatement, resultset}
import doobie.hi.{connection => HC, preparedstatement => HPS}
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import scala.concurrent.duration._

/** Exercises result-set metadata to export reports with query-defined column labels as CSV. */
@Timeout(60)
class ReportCsvExportTest {
  private def csvCell(value: String): String =
    if (value.exists(character => character == ',' || character == '"' || character == '\n' || character == '\r')) {
      "\"" + value.replace("\"", "\"\"") + "\""
    } else value

  private def readRows(columns: List[Int]): resultset.ResultSetIO[List[List[String]]] =
    resultset.next.flatMap { available =>
      if (available) {
        for {
          cells <- columns.traverse { index =>
            for {
              value <- resultset.getString(index)
              missing <- resultset.wasNull
            } yield if (missing) "" else value
          }
          remaining <- readRows(columns)
        } yield cells :: remaining
      } else resultset.pure(List.empty[List[String]])
    }

  private val renderCsv: resultset.ResultSetIO[String] = for {
    metadata <- resultset.getMetaData
    labels <- resultset.delay {
      (1 to metadata.getColumnCount).toList.map(metadata.getColumnLabel)
    }
    rows <- readRows((1 to labels.size).toList)
  } yield (labels :: rows).map(_.map(csvCell).mkString(",")).mkString("", "\r\n", "\r\n")

  private def exportReport(minimumUnits: Int): connection.ConnectionIO[String] =
    HC.prepareStatement(
      """SELECT customer AS "Customer", units AS "Units", note AS "Delivery note"
         FROM report_shipments WHERE units >= ? ORDER BY shipment_id"""
    )(for {
      _ <- preparedstatement.setInt(1, minimumUnits)
      csv <- HPS.executeQuery(renderCsv)
    } yield csv)

  @Test
  def exportsLabeledRowsAndRetainsHeadersWhenTheReportIsEmpty(): Unit = {
    val transactor: Transactor[IO] = Transactor.fromDriverManager[IO](
      "org.h2.Driver",
      "jdbc:h2:mem:report_csv_export;DB_CLOSE_DELAY=-1",
      "sa",
      ""
    )
    val customer: String = "Coffee, Inc."
    val note: String = "Ring \"rear\" bell\nLeave at reception"

    val initialize: connection.ConnectionIO[Unit] = for {
      _ <- sql"DROP TABLE IF EXISTS report_shipments".update.run
      _ <- sql"""CREATE TABLE report_shipments (
                  shipment_id INTEGER PRIMARY KEY,
                  customer VARCHAR(100) NOT NULL,
                  units INTEGER NOT NULL,
                  note VARCHAR(200)
                )""".update.run
      _ <- sql"INSERT INTO report_shipments VALUES (1, $customer, 3, $note)".update.run
      _ <- sql"INSERT INTO report_shipments VALUES (2, 'Tea Shop', 1, NULL)".update.run
    } yield ()

    val (complete, filtered, empty): (String, String, String) = (for {
      _ <- initialize.transact(transactor)
      allRows <- exportReport(1).transact(transactor)
      largeShipments <- exportReport(2).transact(transactor)
      noRows <- exportReport(10).transact(transactor)
      _ <- sql"DROP TABLE report_shipments".update.run.transact(transactor)
    } yield (allRows, largeShipments, noRows)).timeout(45.seconds).unsafeRunSync()

    val header: String = "Customer,Units,Delivery note\r\n"
    val firstRow: String = "\"Coffee, Inc.\",3,\"Ring \"\"rear\"\" bell\nLeave at reception\"\r\n"
    assertEquals(header + firstRow + "Tea Shop,1,\r\n", complete)
    assertEquals(header + firstRow, filtered)
    assertEquals(header, empty)
  }
}
