/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_slick.slick_2_13

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import slick.jdbc.GetResult
import slick.jdbc.H2Profile.api._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/** Exercises Slick's plain SQL subsystem to build a warehouse replenishment feed.
  * Bound parameters preserve literal SKUs, and a custom decoder maps nullable warehouse locations.
  */
class PlainSqlReplenishmentTest {
  case class Replenishment(sku: String, missingUnits: Int, bin: Option[String])

  implicit val replenishmentResult: GetResult[Replenishment] = GetResult { row =>
    Replenishment(row.nextString(), row.nextInt(), row.nextStringOption())
  }

  private def addStock(sku: String, quantity: Int, reorderPoint: Int, bin: Option[String]): DBIO[Int] =
    sqlu"""INSERT INTO REPLENISHMENT_STOCK (SKU, QUANTITY, REORDER_POINT, BIN)
            VALUES ($sku, $quantity, $reorderPoint, $bin)"""

  @Test
  @Timeout(60)
  def refreshesFeedAfterReceivingStock(): Unit = {
    val database: Database = Database.forURL(
      "jdbc:h2:mem:plain_sql_replenishment;DB_CLOSE_DELAY=-1",
      driver = "org.h2.Driver"
    )
    val teaSku: String = "tea's-box"
    val receivedUnits: Int = 4
    val create = sqlu"""CREATE TABLE REPLENISHMENT_STOCK (
      SKU VARCHAR(80) PRIMARY KEY,
      QUANTITY INTEGER NOT NULL,
      REORDER_POINT INTEGER NOT NULL,
      BIN VARCHAR(40)
    )"""
    val drop = sqlu"DROP TABLE REPLENISHMENT_STOCK"
    val feed = sql"""SELECT SKU, REORDER_POINT - QUANTITY, BIN
                     FROM REPLENISHMENT_STOCK
                     WHERE QUANTITY < REORDER_POINT
                     ORDER BY SKU""".as[Replenishment]

    val scenario = for {
      _ <- create
      _ <- addStock(teaSku, 2, 5, None)
      _ <- addStock("coffee", 1, 4, Some("A2"))
      _ <- addStock("mug", 10, 4, Some("B1"))
      beforeReceipt <- feed
      updated <- sqlu"""UPDATE REPLENISHMENT_STOCK
                         SET QUANTITY = QUANTITY + $receivedUnits
                         WHERE SKU = $teaSku"""
      afterReceipt <- feed
      teaQuantity <- sql"SELECT QUANTITY FROM REPLENISHMENT_STOCK WHERE SKU = $teaSku".as[Int].head
      totalQuantity <- sql"SELECT SUM(QUANTITY) FROM REPLENISHMENT_STOCK".as[Int].head
    } yield (beforeReceipt, updated, afterReceipt, teaQuantity, totalQuantity)

    try {
      val (beforeReceipt, updated, afterReceipt, teaQuantity, totalQuantity) = Await.result(
        database.run(scenario.andFinally(drop)),
        30.seconds
      )
      assertEquals(
        Seq(Replenishment("coffee", 3, Some("A2")), Replenishment(teaSku, 3, None)),
        beforeReceipt
      )
      assertEquals(1, updated)
      assertEquals(Seq(Replenishment("coffee", 3, Some("A2"))), afterReceipt)
      assertEquals(6, teaQuantity)
      assertEquals(17, totalQuantity)
    } finally {
      database.close()
    }
  }
}
