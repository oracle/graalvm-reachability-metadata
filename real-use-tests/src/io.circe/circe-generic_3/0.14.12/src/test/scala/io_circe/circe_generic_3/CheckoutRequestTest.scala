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

/** Exercises semiautomatic product codec derivation for nested checkout request payloads. */
@Timeout(60)
class CheckoutRequestTest {
  final case class DeliveryAddress(street: String, city: String, postalCode: String)

  object DeliveryAddress {
    given Codec.AsObject[DeliveryAddress] = deriveCodec[DeliveryAddress]
  }

  final case class OrderLine(sku: String, quantity: Int, unitPrice: BigDecimal)

  object OrderLine {
    given Codec.AsObject[OrderLine] = deriveCodec[OrderLine]
  }

  final case class CheckoutRequest(
      customerId: String,
      delivery: DeliveryAddress,
      lines: List[OrderLine],
      giftMessage: Option[String],
      labels: Map[String, String]
  )

  object CheckoutRequest {
    given Codec.AsObject[CheckoutRequest] = deriveCodec[CheckoutRequest]
  }

  @Test
  def acceptsCheckoutAndSerializesThePayloadForFulfillment(): Unit = {
    val incoming: String = """
      {
        "customerId": "customer-42",
        "delivery": {
          "street": "12 Market Street",
          "city": "Bristol",
          "postalCode": "BS1 1AA"
        },
        "lines": [
          {"sku": "notebook", "quantity": 2, "unitPrice": 4.25},
          {"sku": "pencil", "quantity": 3, "unitPrice": 0.50}
        ],
        "labels": {"channel": "web"}
      }
      """
    val expected: CheckoutRequest = CheckoutRequest(
      "customer-42",
      DeliveryAddress("12 Market Street", "Bristol", "BS1 1AA"),
      List(
        OrderLine("notebook", 2, BigDecimal("4.25")),
        OrderLine("pencil", 3, BigDecimal("0.50"))
      ),
      None,
      Map("channel" -> "web")
    )
    val request: CheckoutRequest = decode[CheckoutRequest](incoming).fold(
      error => throw new AssertionError(s"Checkout payload was rejected: $error"),
      identity
    )

    assertThat(request).isEqualTo(expected)
    val total: BigDecimal = request.lines.map(line => line.unitPrice * line.quantity).sum
    assertThat(total).isEqualTo(BigDecimal("10.00"))

    val fulfillmentPayload: Json = request.asJson
    val expectedPayload: Json = parse(incoming).fold(
      error => throw new AssertionError(s"Invalid fixture: $error"),
      identity
    ).mapObject(_.add("giftMessage", Json.Null))

    assertThat(fulfillmentPayload).isEqualTo(expectedPayload)
    assertThat(decode[CheckoutRequest](fulfillmentPayload.noSpaces)).isEqualTo(Right(expected))
  }
}
