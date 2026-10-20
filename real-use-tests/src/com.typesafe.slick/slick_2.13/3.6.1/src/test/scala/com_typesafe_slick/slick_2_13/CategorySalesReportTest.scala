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

/** Exercises Slick's lifted relational query subsystem to produce a category sales report.
  * An application joins paid order lines to its active catalog, aggregates revenue, and pages results.
  */
class CategorySalesReportTest {
  case class Product(id: Int, name: String, category: String, priceCents: Int, active: Boolean)
  case class OrderLine(id: Int, productId: Int, quantity: Int, paid: Boolean)

  class Products(tag: Tag) extends Table[Product](tag, "REPORT_PRODUCTS") {
    def id: Rep[Int] = column[Int]("ID", O.PrimaryKey)
    def name: Rep[String] = column[String]("NAME")
    def category: Rep[String] = column[String]("CATEGORY")
    def priceCents: Rep[Int] = column[Int]("PRICE_CENTS")
    def active: Rep[Boolean] = column[Boolean]("ACTIVE")
    def * = (id, name, category, priceCents, active).mapTo[Product]
  }

  class OrderLines(tag: Tag) extends Table[OrderLine](tag, "REPORT_ORDER_LINES") {
    def id: Rep[Int] = column[Int]("ID", O.PrimaryKey)
    def productId: Rep[Int] = column[Int]("PRODUCT_ID")
    def quantity: Rep[Int] = column[Int]("QUANTITY")
    def paid: Rep[Boolean] = column[Boolean]("PAID")
    def * = (id, productId, quantity, paid).mapTo[OrderLine]
  }

  @Test
  @Timeout(60)
  def reportsPaidSalesByCategory(): Unit = {
    val products = TableQuery(tag => new Products(tag))
    val orderLines = TableQuery(tag => new OrderLines(tag))
    val database = Database.forURL(
      "jdbc:h2:mem:category_sales_report;DB_CLOSE_DELAY=-1",
      driver = "org.h2.Driver"
    )
    val schema = products.schema ++ orderLines.schema

    val paidCatalogLines = for {
      product <- products if product.active
      line <- orderLines if line.productId === product.id && line.paid
    } yield (product.category, line.quantity, product.priceCents * line.quantity)

    val rankedCategories = paidCatalogLines.groupBy(_._1).map { case (category, sales) =>
      (category, sales.map(_._2).sum.getOrElse(0), sales.map(_._3).sum.getOrElse(0))
    }.sortBy(row => (row._3.desc, row._1.asc))

    val report = for {
      _ <- schema.create
      _ <- products ++= Seq(
        Product(1, "Scala handbook", "Books", 3000, true),
        Product(2, "Pocket guide", "Books", 500, true),
        Product(3, "Keyboard", "Hardware", 2500, true),
        Product(4, "Retired monitor", "Hardware", 9000, false),
        Product(5, "Unordered notebook", "Stationery", 1000, true)
      )
      _ <- orderLines ++= Seq(
        OrderLine(1, 1, 2, true),
        OrderLine(2, 2, 5, true),
        OrderLine(3, 3, 3, true),
        OrderLine(4, 1, 100, false),
        OrderLine(5, 4, 10, true)
      )
      allCategories <- rankedCategories.result
      secondPage <- rankedCategories.drop(1).take(1).result
    } yield (allCategories, secondPage)

    try {
      val (allCategories, secondPage) = Await.result(
        database.run(report.andFinally(schema.drop)),
        30.seconds
      )
      assertEquals(Seq(("Books", 7, 8500), ("Hardware", 3, 7500)), allCategories)
      assertEquals(Seq(("Hardware", 3, 7500)), secondPage)
    } finally {
      database.close()
    }
  }
}
