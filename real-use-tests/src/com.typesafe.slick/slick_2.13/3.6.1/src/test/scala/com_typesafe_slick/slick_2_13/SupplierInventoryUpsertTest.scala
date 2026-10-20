/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_slick.slick_2_13

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import slick.jdbc.H2Profile.api._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/** Exercises Slick's insert-or-update subsystem to synchronize supplier inventory feeds.
  * Composite keys isolate suppliers, and replaying a feed updates stock without duplicating rows.
  */
class SupplierInventoryUpsertTest {
  case class Stock(supplierId: Int, sku: String, available: Int, costCents: Int)

  class Inventory(tag: Tag) extends Table[Stock](tag, "UPSERT_SUPPLIER_INVENTORY") {
    def supplierId: Rep[Int] = column[Int]("SUPPLIER_ID")
    def sku: Rep[String] = column[String]("SKU")
    def available: Rep[Int] = column[Int]("AVAILABLE")
    def costCents: Rep[Int] = column[Int]("COST_CENTS")
    def key = primaryKey("SUPPLIER_INVENTORY_PK", (supplierId, sku))
    def * = (supplierId, sku, available, costCents).mapTo[Stock]
  }

  @Test
  @Timeout(60)
  def synchronizesNewAndExistingStockWithoutAffectingOtherSuppliers(): Unit = {
    val inventory = TableQuery(tag => new Inventory(tag))
    val database: Database = Database.forURL(
      "jdbc:h2:mem:supplier_inventory_upsert;DB_CLOSE_DELAY=-1",
      driver = "org.h2.Driver"
    )
    val feed: Seq[Stock] = Seq(
      Stock(10, "PEN", 12, 125),
      Stock(10, "PAPER", 30, 450)
    )
    val initial: Seq[Stock] = Seq(
      Stock(10, "PEN", 5, 100),
      Stock(10, "FOLDER", 8, 300),
      Stock(20, "PEN", 50, 90)
    )
    val allStock = inventory.sortBy(row => (row.supplierId, row.sku)).result

    def importFeed(rows: Seq[Stock]): DBIO[Unit] =
      DBIO.seq(rows.map(row => inventory.insertOrUpdate(row)): _*).transactionally

    val scenario = for {
      _ <- inventory.schema.create
      _ <- inventory ++= initial
      _ <- importFeed(feed)
      afterImport <- allStock
      _ <- importFeed(feed)
      afterReplay <- allStock
      _ <- importFeed(Seq(Stock(10, "PEN", 0, 130)))
      afterSoldOut <- allStock
      purchasable <- inventory.filter(row => row.supplierId === 10 && row.available > 0)
        .sortBy(_.sku).map(_.sku).result
    } yield (afterImport, afterReplay, afterSoldOut, purchasable)

    try {
      val (afterImport, afterReplay, afterSoldOut, purchasable) = Await.result(
        database.run(scenario.andFinally(inventory.schema.drop)),
        30.seconds
      )
      val expected: Seq[Stock] = Seq(
        Stock(10, "FOLDER", 8, 300),
        Stock(10, "PAPER", 30, 450),
        Stock(10, "PEN", 12, 125),
        Stock(20, "PEN", 50, 90)
      )
      assertEquals(expected, afterImport)
      assertEquals(expected, afterReplay)
      assertEquals(
        expected.map(row => if (row.supplierId == 10 && row.sku == "PEN")
          row.copy(available = 0, costCents = 130) else row),
        afterSoldOut
      )
      assertEquals(Seq("FOLDER", "PAPER"), purchasable)
    } finally {
      database.close()
    }
  }
}
