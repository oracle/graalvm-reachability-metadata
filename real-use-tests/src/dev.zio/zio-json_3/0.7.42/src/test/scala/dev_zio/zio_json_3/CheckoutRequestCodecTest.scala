/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_json_3

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import zio.json.*

/** Exercises derived product codecs for accepting and storing checkout requests. */
class CheckoutRequestCodecTest {
  final case class Address(city: String, @jsonField("postal_code") postalCode: String)
  final case class Line(sku: String, quantity: Int, unitPriceCents: Int)
  final case class CheckoutRequest(
    @jsonField("order_id") orderId: String,
    address: Address,
    lines: List[Line],
    note: Option[String],
    currency: String = "USD"
  )

  given JsonCodec[Address] = DeriveJsonCodec.gen[Address]
  given JsonCodec[Line] = DeriveJsonCodec.gen[Line]
  given JsonCodec[CheckoutRequest] = DeriveJsonCodec.gen[CheckoutRequest]

  @Test
  @Timeout(30)
  def acceptCheckoutAndStoreNormalizedRequest(): Unit = {
    val incoming: String = """
      {
        "order_id": "cart-104",
        "address": {"city": "Portland", "postal_code": "97205"},
        "lines": [
          {"sku": "coffee", "quantity": 2, "unitPriceCents": 1250},
          {"sku": "filter", "quantity": 1, "unitPriceCents": 400}
        ]
      }
      """
    val expected: CheckoutRequest = CheckoutRequest(
      "cart-104",
      Address("Portland", "97205"),
      List(Line("coffee", 2, 1250), Line("filter", 1, 400)),
      None
    )
    val decoded: Either[String, CheckoutRequest] = incoming.fromJson[CheckoutRequest]
    assertEquals(Right(expected), decoded)

    val order: CheckoutRequest = decoded.fold(error => fail[CheckoutRequest](error), identity)
    val totalCents: Int = order.lines.map(line => line.quantity * line.unitPriceCents).sum
    assertEquals(2900, totalCents)
    assertEquals("USD", order.currency)
    assertEquals(None, order.note)

    val stored: CheckoutRequest = order.copy(note = Some("Leave at the \"side\" door\nRing once"))
    val document: String = stored.toJson
    assertTrue(document.contains("\"order_id\":\"cart-104\""), document)
    assertTrue(document.contains("\"postal_code\":\"97205\""), document)
    assertTrue(document.contains("\\\"side\\\" door\\nRing once"), document)
    assertEquals(Right(stored), document.fromJson[CheckoutRequest])

    val invalid: String = incoming.replace("\"quantity\": 2", "\"quantity\": \"two\"")
    val rejected: Either[String, CheckoutRequest] = invalid.fromJson[CheckoutRequest]
    assertTrue(rejected.isLeft, "A textual quantity must not be accepted as an order")
    val error: String = rejected.swap.toOption.get
    assertTrue(error.contains("lines"), error)
    assertTrue(error.contains("quantity"), error)
  }
}
