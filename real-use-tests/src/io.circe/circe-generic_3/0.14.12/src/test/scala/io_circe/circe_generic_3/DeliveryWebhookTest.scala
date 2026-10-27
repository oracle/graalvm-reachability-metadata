/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_generic_3

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.parser.parse
import io.circe.syntax.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/** Exercises automatic sealed-trait codec derivation for polymorphic delivery webhook events. */
@Timeout(60)
class DeliveryWebhookTest {
  sealed trait DeliveryEvent

  final case class Dispatched(trackingId: String, carrier: String) extends DeliveryEvent

  final case class Delivered(trackingId: String, receivedBy: String) extends DeliveryEvent

  final case class Webhook(eventId: String, event: DeliveryEvent)

  @Test
  def routesWebhookVariantsAndPreservesTheirWireRepresentation(): Unit = {
    val incoming: String = """
      [
        {
          "eventId": "evt-101",
          "event": {"Dispatched": {"trackingId": "parcel-42", "carrier": "Parcel Express"}}
        },
        {
          "eventId": "evt-102",
          "event": {"Delivered": {"trackingId": "parcel-42", "receivedBy": "Reception"}}
        }
      ]
      """
    val webhooks: List[Webhook] = decode[List[Webhook]](incoming).fold(
      error => throw new AssertionError(s"Webhook batch was rejected: $error"),
      identity
    )

    assertThat(webhooks).isEqualTo(List(
      Webhook("evt-101", Dispatched("parcel-42", "Parcel Express")),
      Webhook("evt-102", Delivered("parcel-42", "Reception"))
    ))

    val notifications: List[String] = webhooks.map { webhook =>
      webhook.event match {
        case Dispatched(trackingId, carrier) => s"$trackingId shipped with $carrier"
        case Delivered(trackingId, receivedBy) => s"$trackingId received by $receivedBy"
      }
    }
    assertThat(notifications).isEqualTo(List(
      "parcel-42 shipped with Parcel Express",
      "parcel-42 received by Reception"
    ))

    val archived: Json = webhooks.asJson
    val expected: Json = parse(incoming).fold(
      error => throw new AssertionError(s"Invalid webhook fixture: $error"),
      identity
    )
    assertThat(archived).isEqualTo(expected)
    assertThat(decode[List[Webhook]](archived.noSpaces)).isEqualTo(Right(webhooks))

    val unknownEvent: String = """
      {"eventId": "evt-103", "event": {"Returned": {"trackingId": "parcel-42"}}}
      """
    assertThat(decode[Webhook](unknownEvent).isLeft).isTrue()
  }
}
