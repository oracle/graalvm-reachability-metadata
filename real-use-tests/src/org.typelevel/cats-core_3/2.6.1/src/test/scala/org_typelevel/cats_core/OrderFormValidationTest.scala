/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_core

import cats.data.ValidatedNel
import cats.syntax.all.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

/** Exercises Cats Validated error accumulation for an application's order form.
  * Independent fields report all errors together, while quantity checks run in sequence.
  */
@Timeout(60)
class OrderFormValidationTest {
  private case class Order(sku: String, quantity: Int, email: String)

  private type Checked[A] = ValidatedNel[String, A]

  private def validateSku(raw: String): Checked[String] = {
    val sku: String = raw.trim
    if (sku.nonEmpty) sku.validNel[String]
    else "sku: required".invalidNel[String]
  }

  private def validateQuantity(raw: String): Checked[Int] = {
    raw.trim.toIntOption.toValidNel("quantity: must be a whole number").andThen { quantity =>
      if (quantity >= 1 && quantity <= 100) quantity.validNel[String]
      else "quantity: must be between 1 and 100".invalidNel[Int]
    }
  }

  private def validateEmail(raw: String): Checked[String] = {
    val email: String = raw.trim
    val parts: Array[String] = email.split("@", -1)
    if (parts.length == 2 && parts.forall(_.nonEmpty)) email.validNel[String]
    else "email: must have a local part and domain".invalidNel[String]
  }

  private def validateOrder(sku: String, quantity: String, email: String): Checked[Order] = {
    (validateSku(sku), validateQuantity(quantity), validateEmail(email)).mapN(Order.apply)
  }

  private def errors(result: Checked[Order]): List[String] = {
    result.fold(_.toList, _ => List.empty[String])
  }

  @Test
  def acceptsAndNormalizesAValidOrder(): Unit = {
    val result: Checked[Order] = validateOrder("  BOOK-42  ", " 3 ", " reader@example.com ")

    assertEquals(Right(Order("BOOK-42", 3, "reader@example.com")), result.toEither)
  }

  @Test
  def reportsEveryInvalidFieldInFormOrder(): Unit = {
    val result: Checked[Order] = validateOrder("   ", "0", "reader.example.com")

    assertEquals(
      List("sku: required", "quantity: must be between 1 and 100",
        "email: must have a local part and domain"),
      errors(result)
    )
  }

  @Test
  def reportsParsingBeforeRangeValidationAndAcceptsACorrectedForm(): Unit = {
    val malformed: Checked[Order] = validateOrder("BOOK-42", "many", "@example.com")

    assertEquals(
      List("quantity: must be a whole number", "email: must have a local part and domain"),
      errors(malformed)
    )
    assertEquals(
      List("quantity: must be between 1 and 100"),
      errors(validateOrder("BOOK-42", "101", "reader@example.com"))
    )
    assertEquals(
      Right(Order("BOOK-42", 100, "reader@example.com")),
      validateOrder("BOOK-42", "100", "reader@example.com").toEither
    )
  }
}
