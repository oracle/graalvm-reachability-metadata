/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_core

import cats.data.{Ior, NonEmptyList}
import cats.syntax.all.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

/** Exercises Cats Ior to import supplier products with partial-success warning reports.
  * Accepted records retain migration warnings, while fatal errors prevent publication.
  */
@Timeout(60)
class SupplierProductIorTest {
  private case class SupplierRow(sku: String, description: String, priceCents: Long)

  private case class Product(sku: String, description: String, priceCents: Long)

  private type ImportResult[A] = Ior[NonEmptyList[String], A]

  private def normalizeSku(raw: String): ImportResult[String] = {
    val sku: String = raw.trim
    if (sku.isEmpty) {
      Ior.Left(NonEmptyList.one("sku is required"))
    } else if (sku.startsWith("OLD-")) {
      Ior.Both(NonEmptyList.one(s"legacy sku migrated: $sku"), sku.stripPrefix("OLD-"))
    } else {
      Ior.Right(sku)
    }
  }

  private def descriptionFor(raw: String, sku: String): ImportResult[String] = {
    val description: String = raw.trim
    if (description.isEmpty) {
      Ior.Both(NonEmptyList.one("description defaulted to sku"), sku)
    } else {
      Ior.Right(description)
    }
  }

  private def validatePrice(cents: Long): ImportResult[Long] = {
    if (cents > 0L) Ior.Right(cents)
    else Ior.Left(NonEmptyList.one("price must be positive"))
  }

  private def importProduct(row: SupplierRow): ImportResult[Product] = {
    for {
      sku <- normalizeSku(row.sku)
      description <- descriptionFor(row.description, sku)
      price <- validatePrice(row.priceCents)
    } yield Product(sku, description, price)
  }

  @Test
  def acceptsACleanProductWithoutWarnings(): Unit = {
    val result: ImportResult[Product] = importProduct(
      SupplierRow(" BOOK-42 ", " Illustrated field guide ", 2500L)
    )

    assertEquals(Ior.Right(Product("BOOK-42", "Illustrated field guide", 2500L)), result)
    assertEquals(Some(Product("BOOK-42", "Illustrated field guide", 2500L)), result.toOption)
  }

  @Test
  def publishesAMigratedProductAndRetainsEveryWarning(): Unit = {
    val result: ImportResult[Product] = importProduct(SupplierRow("OLD-BOOK-42", " ", 2500L))
    val warnings: NonEmptyList[String] = NonEmptyList.of(
      "legacy sku migrated: OLD-BOOK-42",
      "description defaulted to sku"
    )
    val product: Product = Product("BOOK-42", "BOOK-42", 2500L)

    assertEquals(Ior.Both(warnings, product), result)
    assertEquals(Some(product), result.toOption)
  }

  @Test
  def retainsEarlierWarningsWhenAFatalErrorPreventsPublication(): Unit = {
    val result: ImportResult[Product] = importProduct(SupplierRow("OLD-BOOK-42", "", 0L))
    val messages: NonEmptyList[String] = NonEmptyList.of(
      "legacy sku migrated: OLD-BOOK-42",
      "description defaulted to sku",
      "price must be positive"
    )

    assertEquals(Ior.Left(messages), result)
    assertEquals(None, result.toOption)
    assertEquals(
      Ior.Left(NonEmptyList.one("sku is required")),
      importProduct(SupplierRow(" ", "", 0L))
    )
  }
}
