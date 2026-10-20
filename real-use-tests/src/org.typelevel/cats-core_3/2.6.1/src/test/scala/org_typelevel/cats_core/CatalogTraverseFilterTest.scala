/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_core

import cats.TraverseFilter
import cats.implicits.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

/** Exercises Cats TraverseFilter to publish available catalog listings from a curated product list.
  * Unavailable products are omitted, but missing catalog records prevent a partial publication.
  */
@Timeout(60)
class CatalogTraverseFilterTest {
  private case class Product(title: String, priceCents: Long, units: Int, published: Boolean)

  private case class Listing(sku: String, title: String, priceCents: Long)

  private type Result[A] = Either[String, A]

  private val catalog: Map[String, Product] = Map(
    "BOOK" -> Product("Field guide", 2400L, 5, published = true),
    "PEN" -> Product("Fountain pen", 1800L, 0, published = true),
    "DRAFT" -> Product("Upcoming atlas", 3200L, 10, published = false),
    "PAD" -> Product("Sketch pad", 650L, 12, published = true)
  )

  private def listing(sku: String): Result[Option[Listing]] = {
    catalog.get(sku).toRight(s"catalog record missing: $sku").map { product =>
      Option.when(product.published && product.units > 0) {
        Listing(sku, product.title, product.priceCents)
      }
    }
  }

  private def publish(curatedSkus: List[String]): Result[List[Listing]] = {
    TraverseFilter[List].traverseFilter(curatedSkus)(listing)
  }

  @Test
  def enrichesAvailableProductsAndPreservesTheEditorsDisplayOrder(): Unit = {
    val result: Result[List[Listing]] = publish(List("PAD", "PEN", "DRAFT", "BOOK"))

    assertEquals(
      Right(List(Listing("PAD", "Sketch pad", 650L), Listing("BOOK", "Field guide", 2400L))),
      result
    )
    assertEquals(
      Right(List(Listing("BOOK", "Field guide", 2400L), Listing("PAD", "Sketch pad", 650L))),
      publish(List("BOOK", "PAD"))
    )
  }

  @Test
  def rejectsAnIncompleteCatalogAndAllowsPublicationAfterTheSelectionIsCorrected(): Unit = {
    assertEquals(
      Left("catalog record missing: REMOVED"),
      publish(List("BOOK", "REMOVED", "PAD"))
    )
    assertEquals(
      Left("catalog record missing: REMOVED"),
      publish(List("PEN", "REMOVED", "UNKNOWN"))
    )
    assertEquals(
      Right(List(Listing("BOOK", "Field guide", 2400L))),
      publish(List("BOOK", "PEN"))
    )
  }

  @Test
  def publishesAnEmptyPageWhenNoSelectedProductsAreAvailable(): Unit = {
    assertEquals(Right(List.empty[Listing]), publish(List("PEN", "DRAFT")))
    assertEquals(Right(List.empty[Listing]), publish(List.empty))
  }
}
