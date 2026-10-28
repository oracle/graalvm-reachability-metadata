/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_slick.slick_2_13

import com.typesafe.config.{Config, ConfigFactory}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.Await
import scala.concurrent.duration._

/** Exercises Slick's configuration-driven database/profile subsystem for a pooled product catalog.
  * A profile-neutral schema serves catalog edits and reads through the configured database.
  */
class ConfiguredCatalogTest {
  class CatalogSchema(val profile: JdbcProfile) {
    import profile.api._

    class Products(tag: Tag) extends Table[(String, Int, Boolean)](tag, "CONFIGURED_PRODUCTS") {
      def sku: Rep[String] = column[String]("SKU", O.PrimaryKey)
      def priceCents: Rep[Int] = column[Int]("PRICE_CENTS")
      def published: Rep[Boolean] = column[Boolean]("PUBLISHED")
      def * = (sku, priceCents, published)
    }

    val products: TableQuery[Products] = TableQuery(tag => new Products(tag))
  }

  @Test
  @Timeout(60)
  def maintainsCatalogThroughConfiguredProfileAndConnectionPool(): Unit = {
    val settings: Config = ConfigFactory.parseString(
      """catalog {
        |  profile = "slick.jdbc.H2Profile$"
        |  db {
        |    connectionPool = "HikariCP"
        |    driver = "org.h2.Driver"
        |    url = "jdbc:h2:mem:configured_catalog;DB_CLOSE_DELAY=-1"
        |    user = "sa"
        |    password = ""
        |    numThreads = 2
        |    minConnections = 1
        |    maxConnections = 2
        |    connectionTimeout = 10000
        |  }
        |} """.stripMargin
    )
    val configuration: DatabaseConfig[JdbcProfile] =
      DatabaseConfig.forConfig[JdbcProfile]("catalog", settings)
    val catalog: CatalogSchema = new CatalogSchema(configuration.profile)
    import catalog.profile.api._

    val database = configuration.db
    val products = catalog.products
    val storefront = products.filter(_.published).sortBy(_.sku)
      .map(product => (product.sku, product.priceCents)).result
    val setup = DBIO.seq(
      products.schema.create,
      products ++= Seq(("BOOK", 2500, true), ("DRAFT", 800, false), ("PEN", 150, true))
    )

    try {
      Await.result(database.run(setup), 15.seconds)
      val initial: Seq[(String, Int)] = Await.result(database.run(storefront), 15.seconds)
      assertEquals(Seq(("BOOK", 2500), ("PEN", 150)), initial)

      val changes = DBIO.seq(
        products.filter(_.sku === "BOOK").map(_.priceCents).update(2200),
        products.filter(_.sku === "DRAFT").map(_.published).update(true),
        products.filter(_.sku === "PEN").map(_.published).update(false)
      ).transactionally
      Await.result(database.run(changes), 15.seconds)

      val refreshed: Seq[(String, Int)] = Await.result(database.run(storefront), 15.seconds)
      val saved: Seq[(String, Int, Boolean)] = Await.result(
        database.run(products.sortBy(_.sku).result), 15.seconds
      )
      assertEquals(Seq(("BOOK", 2200), ("DRAFT", 800)), refreshed)
      assertEquals(
        Seq(("BOOK", 2200, true), ("DRAFT", 800, true), ("PEN", 150, false)),
        saved
      )
    } finally {
      try {
        Await.result(database.run(products.schema.dropIfExists), 10.seconds)
      } finally {
        database.close()
      }
    }
  }
}
