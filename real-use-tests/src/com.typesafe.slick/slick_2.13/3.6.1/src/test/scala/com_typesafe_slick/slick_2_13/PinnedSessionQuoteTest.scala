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
import slick.lifted.ProvenShape

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/** Exercises Slick's pinned-session JDBC interoperability subsystem to price a staged quote request.
  * A legacy JDBC batch populates a session-local table that lifted queries join to the price catalog.
  */
class PinnedSessionQuoteTest {
  class Prices(tag: Tag) extends Table[(String, Int)](tag, "QUOTE_PRICES") {
    def sku: Rep[String] = column[String]("SKU", O.PrimaryKey)
    def cents: Rep[Int] = column[Int]("CENTS")
    def * : ProvenShape[(String, Int)] = (sku, cents)
  }

  class RequestedItems(tag: Tag) extends Table[(String, Int)](tag, "QUOTE_REQUEST") {
    def sku: Rep[String] = column[String]("SKU", O.PrimaryKey)
    def quantity: Rep[Int] = column[Int]("QUANTITY")
    def * : ProvenShape[(String, Int)] = (sku, quantity)
  }

  private def stageRequest(items: Seq[(String, Int)]): DBIO[Unit] = SimpleDBIO { context =>
    val create = context.connection.createStatement()
    try {
      create.executeUpdate(
        "CREATE LOCAL TEMPORARY TABLE QUOTE_REQUEST (SKU VARCHAR(80) PRIMARY KEY, QUANTITY INT NOT NULL)"
      )
    } finally {
      create.close()
    }
    val insert = context.connection.prepareStatement(
      "INSERT INTO QUOTE_REQUEST (SKU, QUANTITY) VALUES (?, ?)"
    )
    try {
      items.foreach { case (sku, quantity) =>
        insert.setString(1, sku)
        insert.setInt(2, quantity)
        insert.addBatch()
      }
      insert.executeBatch()
      ()
    } finally {
      insert.close()
    }
  }

  @Test
  @Timeout(60)
  def pricesJdbcStagedItemsAndReportsUnknownSkusOnTheSameSession(): Unit = {
    val prices: TableQuery[Prices] = TableQuery(tag => new Prices(tag))
    val requested: TableQuery[RequestedItems] = TableQuery(tag => new RequestedItems(tag))
    val database: Database = Database.forURL(
      "jdbc:h2:mem:pinned_session_quote;DB_CLOSE_DELAY=-1",
      driver = "org.h2.Driver"
    )
    val setup = DBIO.seq(
      prices.schema.create,
      prices ++= Seq(("BOOK", 2000), ("PEN", 250), ("FOLDER", 500))
    )
    val pricedItems = (for {
      item <- requested
      price <- prices if price.sku === item.sku
    } yield (item.sku, item.quantity, item.quantity * price.cents)).sortBy(_._1)
    val unknownItems = requested.filterNot(item => prices.filter(_.sku === item.sku).exists)
      .sortBy(_.sku).map(_.sku)
    val quote = (for {
      _ <- stageRequest(Seq(("PEN", 3), ("UNKNOWN", 1), ("BOOK", 2)))
      lines <- pricedItems.result
      unknown <- unknownItems.result
    } yield (lines, unknown)).andFinally(
      sqlu"DROP TABLE IF EXISTS QUOTE_REQUEST"
    ).withPinnedSession

    try {
      Await.result(database.run(setup), 15.seconds)
      val (lines, unknown) = Await.result(database.run(quote), 20.seconds)
      assertEquals(Seq(("BOOK", 2, 4000), ("PEN", 3, 750)), lines)
      assertEquals(4750, lines.map(_._3).sum)
      assertEquals(Seq("UNKNOWN"), unknown)
      val unchangedPrices: Seq[(String, Int)] = Await.result(
        database.run(prices.sortBy(_.sku).result), 10.seconds
      )
      assertEquals(Seq(("BOOK", 2000), ("FOLDER", 500), ("PEN", 250)), unchangedPrices)
    } finally {
      try {
        Await.result(database.run(prices.schema.dropIfExists), 10.seconds)
      } finally {
        database.close()
      }
    }
  }
}
