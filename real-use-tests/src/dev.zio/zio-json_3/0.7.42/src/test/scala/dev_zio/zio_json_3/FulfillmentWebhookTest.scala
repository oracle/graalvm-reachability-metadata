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

/** Exercises discriminated sum-type codecs for routing fulfillment webhook events. */
class FulfillmentWebhookTest {
  @jsonDiscriminator("event_type")
  sealed trait FulfillmentEvent

  @jsonHint("shipment_dispatched")
  final case class Dispatched(orderId: String, trackingNumber: String) extends FulfillmentEvent

  @jsonHint("order_cancelled")
  final case class Cancelled(orderId: String, reason: String) extends FulfillmentEvent

  given JsonCodec[Dispatched] = DeriveJsonCodec.gen[Dispatched]
  given JsonCodec[Cancelled] = DeriveJsonCodec.gen[Cancelled]
  given JsonCodec[FulfillmentEvent] = DeriveJsonCodec.gen[FulfillmentEvent]

  @Test
  @Timeout(30)
  def routeWebhookBatchAndPersistItsAuditTrail(): Unit = {
    val payload: String = """
      [
        {"event_type":"shipment_dispatched","orderId":"order-71","trackingNumber":"TRACK-902"},
        {"orderId":"order-72","reason":"customer request","event_type":"order_cancelled"}
      ]
      """
    val expected: List[FulfillmentEvent] = List(
      Dispatched("order-71", "TRACK-902"),
      Cancelled("order-72", "customer request")
    )
    val decoded: Either[String, List[FulfillmentEvent]] = payload.fromJson[List[FulfillmentEvent]]
    assertEquals(Right(expected), decoded)
    val events: List[FulfillmentEvent] = decoded.fold(
      error => fail[List[FulfillmentEvent]](error),
      identity
    )

    val customerUpdates: Map[String, String] = events.map {
      case Dispatched(orderId, trackingNumber) => orderId -> s"Track your shipment: $trackingNumber"
      case Cancelled(orderId, reason) => orderId -> s"Order cancelled: $reason"
    }.toMap
    assertEquals(
      Map(
        "order-71" -> "Track your shipment: TRACK-902",
        "order-72" -> "Order cancelled: customer request"
      ),
      customerUpdates
    )

    val auditTrail: String = events.toJson
    assertTrue(auditTrail.contains("\"event_type\":\"shipment_dispatched\""), auditTrail)
    assertTrue(auditTrail.contains("\"event_type\":\"order_cancelled\""), auditTrail)
    assertEquals(Right(expected), auditTrail.fromJson[List[FulfillmentEvent]])

    val unsupportedEvent: String =
      """{"event_type":"shipment_recalled","orderId":"order-71"}"""
    assertTrue(
      unsupportedEvent.fromJson[FulfillmentEvent].isLeft,
      "An unknown event must be rejected rather than routed as a known fulfillment action"
    )
    val incompleteDispatch: String =
      """{"event_type":"shipment_dispatched","orderId":"order-71"}"""
    assertTrue(
      incompleteDispatch.fromJson[FulfillmentEvent].isLeft,
      "A dispatch without a tracking number must not generate a customer update"
    )
  }
}
