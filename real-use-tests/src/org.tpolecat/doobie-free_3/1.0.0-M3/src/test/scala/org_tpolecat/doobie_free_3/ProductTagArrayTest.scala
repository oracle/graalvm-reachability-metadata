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
import doobie.free.{connection, preparedstatement, resultset}
import doobie.hi.{connection => HC, preparedstatement => HPS}
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import scala.concurrent.duration._

/** Exercises SQL-array binding and retrieval to store ordered product tags and filter a catalog. */
@Timeout(60)
class ProductTagArrayTest {
  private case class Product(sku: String, tags: List[String])

  private def store(product: Product): connection.ConnectionIO[Unit] =
    connection.createArrayOf("VARCHAR", product.tags.toArray[Object]).bracket { tags =>
      HC.prepareStatement("INSERT INTO tagged_products (sku, tags) VALUES (?, ?)")(for {
        _ <- preparedstatement.setString(1, product.sku)
        _ <- preparedstatement.setArray(2, tags)
        _ <- preparedstatement.executeUpdate
      } yield ())
    }(tags => connection.delay(tags.free()))

  private def readTags: resultset.ResultSetIO[List[String]] =
    resultset.next.flatMap { available =>
      if (available) {
        for {
          tag <- resultset.getString(2)
          remaining <- readTags
        } yield tag :: remaining
      } else {
        resultset.pure(List.empty[String])
      }
    }

  private def readProducts: resultset.ResultSetIO[List[Product]] =
    resultset.next.flatMap { available =>
      if (available) {
        for {
          sku <- resultset.getString("sku")
          tags <- resultset.getArray("tags").bracket { array =>
            resultset.delay(array.getResultSet).bracket { elements =>
              resultset.embed(elements, readTags)
            }(elements => resultset.embed(elements, resultset.close))
          }(array => resultset.delay(array.free()))
          remaining <- readProducts
        } yield Product(sku, tags) :: remaining
      } else {
        resultset.pure(List.empty[Product])
      }
    }

  @Test
  def catalogPreservesTagOrderAndFindsOnlyMatchingProducts(): Unit = {
    val transactor: Transactor[IO] = Transactor.fromDriverManager[IO](
      "org.h2.Driver",
      "jdbc:h2:mem:product_tag_array;DB_CLOSE_DELAY=-1",
      "sa",
      ""
    )
    val products: List[Product] = List(
      Product("coffee", List("fair-trade", "baker's-choice", "organic")),
      Product("mug", List.empty[String]),
      Product("tea", List("organic", "loose-leaf"))
    )
    val wantedTag: String = "organic"

    val initialize: connection.ConnectionIO[Unit] = for {
      _ <- sql"DROP TABLE IF EXISTS tagged_products".update.run
      _ <- sql"""CREATE TABLE tagged_products (
                  sku VARCHAR(40) PRIMARY KEY,
                  tags VARCHAR ARRAY NOT NULL
                )""".update.run
    } yield ()

    val catalog: connection.ConnectionIO[List[Product]] = HC.prepareStatement(
      "SELECT sku, tags FROM tagged_products ORDER BY sku"
    )(HPS.executeQuery(readProducts))

    val matchingProducts: connection.ConnectionIO[List[String]] =
      sql"""SELECT sku FROM tagged_products
            WHERE ARRAY_CONTAINS(tags, $wantedTag) ORDER BY sku""".query[String].to[List]

    val (restored, matching): (List[Product], List[String]) = (for {
      _ <- initialize.transact(transactor)
      _ <- products.traverse_(store).transact(transactor)
      persisted <- catalog.transact(transactor)
      selected <- matchingProducts.transact(transactor)
      _ <- sql"DROP TABLE tagged_products".update.run.transact(transactor)
    } yield (persisted, selected)).timeout(45.seconds).unsafeRunSync()

    assertEquals(products, restored)
    assertEquals(List("coffee", "tea"), matching)
  }
}
