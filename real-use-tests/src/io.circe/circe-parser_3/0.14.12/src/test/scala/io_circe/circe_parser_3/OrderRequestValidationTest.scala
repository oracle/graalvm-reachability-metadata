/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_parser_3

import cats.data.Validated
import io.circe.{CursorOp, Decoder, DecodingFailure, ParsingFailure}
import io.circe.parser.decodeAccumulating
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue, fail}
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/** Exercises accumulating JSON request validation for an order-entry endpoint. */
@Timeout(60)
class OrderRequestValidationTest {
  private final case class Sku(value: String)
  private final case class Quantity(value: Int)
  private final case class OrderRequest(sku: Sku, quantity: Quantity, expedited: Boolean)

  private given Decoder[Sku] = Decoder.decodeString.emap { value =>
    Either.cond(value.trim.nonEmpty, Sku(value), "SKU must not be blank")
  }

  private given Decoder[Quantity] = Decoder.decodeInt.emap { value =>
    Either.cond(value > 0, Quantity(value), "Quantity must be positive")
  }

  private given Decoder[OrderRequest] =
    Decoder.forProduct3("sku", "quantity", "expedited")(OrderRequest.apply)

  @Test
  def returnsAllFieldProblemsBeforeAcceptingACorrectedOrder(): Unit = {
    val invalidRequest: String =
      """{"sku":"   ","quantity":0,"expedited":"yes"}"""

    decodeAccumulating[OrderRequest](invalidRequest) match {
      case Validated.Invalid(errors) =>
        val fieldProblems: Map[String, String] = errors.toList.map {
          case failure: DecodingFailure =>
            val field: String = failure.history.collect {
              case CursorOp.DownField(name) => name
            }.reverse.mkString(".")
            field -> failure.message
          case other => throw new AssertionError("Expected a field error, got: " + other)
        }.toMap

        assertEquals(3, errors.length)
        assertEquals(Set("sku", "quantity", "expedited"), fieldProblems.keySet)
        assertEquals("SKU must not be blank", fieldProblems("sku"))
        assertEquals("Quantity must be positive", fieldProblems("quantity"))
        assertTrue(fieldProblems("expedited").nonEmpty)
      case Validated.Valid(_) => fail("An invalid order must not be accepted")
    }

    val correctedRequest: String =
      """{"sku":"book-42","quantity":2,"expedited":true}"""
    assertEquals(
      Validated.Valid(OrderRequest(Sku("book-42"), Quantity(2), true)),
      decodeAccumulating[OrderRequest](correctedRequest)
    )
  }

  @Test
  def distinguishesMalformedJsonFromFieldValidationErrors(): Unit = {
    val malformedRequest: String = """{"sku":"book-42","quantity":2,"expedited":true"""

    decodeAccumulating[OrderRequest](malformedRequest) match {
      case Validated.Invalid(errors) =>
        assertEquals(1, errors.length)
        assertTrue(errors.head.isInstanceOf[ParsingFailure])
      case Validated.Valid(_) => fail("A truncated JSON request must not be accepted")
    }
  }
}
