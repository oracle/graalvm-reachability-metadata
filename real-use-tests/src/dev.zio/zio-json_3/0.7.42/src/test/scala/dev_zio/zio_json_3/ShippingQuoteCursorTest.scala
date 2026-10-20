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
import zio.json.ast.{Json, JsonCursor}

/** Exercises typed JSON cursor navigation for selecting a shipping quote from a provider envelope. */
class ShippingQuoteCursorTest {
  final case class Rate(service: String, cents: Int, currency: String)
  final case class CheckoutQuote(requestId: String, rate: Rate)

  given JsonDecoder[Rate] = DeriveJsonDecoder.gen[Rate]

  private val requestIdCursor: JsonCursor[Json, Json.Str] =
    JsonCursor.field("request").isObject.field("id").isString
  private val preferredRateCursor: JsonCursor[Json, Json.Obj] =
    JsonCursor.field("data").isObject.field("quotes").isArray.element(0).isObject

  private def selectQuote(response: String): Either[String, CheckoutQuote] = {
    for {
      document <- response.fromJson[Json]
      requestId <- document.get(requestIdCursor)
      preferred <- document.get(preferredRateCursor)
      rate <- preferred.as[Rate]
    } yield CheckoutQuote(requestId.value, rate)
  }

  @Test
  @Timeout(30)
  def extractPreferredQuoteWithoutModelingTheWholeProviderResponse(): Unit = {
    val response: String = """
      {
        "request": {"id": "request-81", "region": "west"},
        "data": {
          "quotes": [
            {"service": "Express", "cents": 1250, "currency": "USD", "carrierData": {"zone": 4}},
            {"service": "Economy", "cents": 850, "currency": "USD"}
          ],
          "warehouse": {"name": "Portland", "extensions": [null, true, {"priority": 3}]}
        },
        "debug": {"quotes": [{"service": "Not a customer offer"}]}
      }
      """
    val chosen: CheckoutQuote = selectQuote(response).fold(
      error => fail[CheckoutQuote](error),
      identity
    )
    assertEquals(CheckoutQuote("request-81", Rate("Express", 1250, "USD")), chosen)
    assertEquals(4250, 3000 + chosen.rate.cents, "Checkout must charge the provider's preferred rate")

    val nextResponse: String = """
      {
        "data": {"quotes": [{"currency": "EUR", "cents": 0, "service": "Pickup"}]},
        "request": {"id": "request-82"}
      }
      """
    assertEquals(
      Right(CheckoutQuote("request-82", Rate("Pickup", 0, "EUR"))),
      selectQuote(nextResponse),
      "Reusable cursors must read each response independently and retain valid free offers"
    )

    val unavailableOffers: List[String] = List(
      """{"request":{"id":"missing"},"data":{}}""",
      """{"request":{"id":"empty"},"data":{"quotes":[]}}""",
      """{"request":{"id":"wrong-shape"},"data":{"quotes":{}}}""",
      """{"request":{"id":"null-rate"},"data":{"quotes":[null]}}""",
      response.replace("\"request-81\"", "81"),
      response.replace("\"cents\": 1250", "\"cents\": \"unknown\"")
    )
    unavailableOffers.foreach { document =>
      val result: Either[String, CheckoutQuote] = selectQuote(document)
      assertTrue(result.isLeft, s"An incomplete or mistyped preferred offer cannot be charged: $document")
      assertTrue(result.swap.toOption.get.nonEmpty, "The application needs a diagnostic for a rejected response")
    }
  }
}
