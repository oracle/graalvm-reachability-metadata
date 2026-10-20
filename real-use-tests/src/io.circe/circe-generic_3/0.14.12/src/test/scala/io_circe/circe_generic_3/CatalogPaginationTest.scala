/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_generic_3

import io.circe.Codec
import io.circe.Json
import io.circe.generic.semiauto.deriveCodec
import io.circe.parser.decode
import io.circe.parser.parse
import io.circe.syntax.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/** Exercises parameterized product codec derivation for paginated catalog API responses. */
@Timeout(60)
class CatalogPaginationTest {
  final case class Page[A](items: List[A], total: Int, nextCursor: Option[String])

  object Page {
    given [A: Codec]: Codec.AsObject[Page[A]] = deriveCodec[Page[A]]
  }

  final case class Product(sku: String, price: BigDecimal)

  object Product {
    given Codec.AsObject[Product] = deriveCodec[Product]
  }

  final case class Category(slug: String, title: String)

  object Category {
    given Codec.AsObject[Category] = deriveCodec[Category]
  }

  @Test
  def servesTypedCatalogPagesUsingOneReusableResponseModel(): Unit = {
    val products: Page[Product] = Page(
      List(Product("notebook", BigDecimal("4.25")), Product("pencil", BigDecimal("0.50"))),
      3,
      Some("after-pencil")
    )
    val categories: Page[Category] = Page(List(Category("stationery", "Stationery")), 1, None)
    val productJson: Json = products.asJson
    val categoryJson: Json = categories.asJson

    assertThat(productJson).isEqualTo(fixture("""
      {
        "items": [{"sku": "notebook", "price": 4.25}, {"sku": "pencil", "price": 0.50}],
        "total": 3,
        "nextCursor": "after-pencil"
      }
      """))
    assertThat(categoryJson).isEqualTo(fixture("""
      {
        "items": [{"slug": "stationery", "title": "Stationery"}],
        "total": 1,
        "nextCursor": null
      }
      """))

    val firstPage: Page[Product] = decode[Page[Product]](productJson.noSpaces).fold(
      error => throw new AssertionError(s"Product page was rejected: $error"),
      identity
    )
    val categoryPage: Page[Category] = decode[Page[Category]](categoryJson.noSpaces).fold(
      error => throw new AssertionError(s"Category page was rejected: $error"),
      identity
    )
    assertThat(firstPage).isEqualTo(products)
    assertThat(categoryPage).isEqualTo(categories)
    assertThat(firstPage.nextCursor).isEqualTo(Some("after-pencil"))
    assertThat(categoryPage.nextCursor).isEqualTo(None)
    assertThat(decode[Page[Product]](categoryJson.noSpaces).isLeft).isTrue()

    val lastPage: Page[Product] = decode[Page[Product]]("""
      {"items": [{"sku": "eraser", "price": 1.25}], "total": 3, "nextCursor": null}
      """).fold(error => throw new AssertionError(s"Last product page was rejected: $error"), identity)
    val allProducts: List[Product] = firstPage.items ++ lastPage.items

    assertThat(allProducts.map(_.sku)).isEqualTo(List("notebook", "pencil", "eraser"))
    assertThat(allProducts.size).isEqualTo(firstPage.total)
    assertThat(allProducts.map(_.price).sum).isEqualTo(BigDecimal("6.00"))
    assertThat(lastPage.nextCursor).isEqualTo(None)
  }

  private def fixture(input: String): Json = parse(input).fold(
    error => throw new AssertionError(s"Invalid catalog fixture: $error"),
    identity
  )
}
