/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lihaoyi.upickle_3

import java.time.Instant
import java.util.Currency

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import upickle.core.Abort
import upickle.core.AbortException
import upickle.core.TraceVisitor.TraceException

/**
 * Custom pickler configuration through an upickle.AttributeTagged API object with key mapping,
 * tag naming, default serialization and bimap codecs, as a billing service consumes and emits
 * snake_case webhook events of a payment provider.
 */
class BillingWebhookCustomPicklerTest {

  import BillingWebhookCustomPicklerTest.*

  @Test
  def providerEventIsReadThroughSnakeCaseKeysAndKindTag(): Unit = {
    val payload: String =
      """{
        |  "kind": "invoice_paid",
        |  "invoice_id": "in_1001",
        |  "amount_due": {"currency": "EUR", "minor_units": 129900},
        |  "paid_at": "2026-03-14T09:26:53Z",
        |  "provider_trace": {"region": "eu-1"}
        |}""".stripMargin

    val event: WebhookEvent = BillingPickle.read[WebhookEvent](payload)

    assertEquals(
      InvoicePaid("in_1001", Money(Currency.getInstance("EUR"), 129900L), Instant.parse("2026-03-14T09:26:53Z")),
      event
    )
  }

  @Test
  def emittedEventUsesSnakeCaseKeysAndTagValues(): Unit = {
    val event: WebhookEvent = SubscriptionCancelled(
      subscriptionId = "sub_77",
      cancelledAt = Instant.parse("2026-04-01T00:00:00Z"),
      reason = None
    )

    val json: ujson.Value = BillingPickle.writeJs(event)

    assertEquals("subscription_cancelled", json("kind").str)
    assertEquals("sub_77", json("subscription_id").str)
    assertEquals("2026-04-01T00:00:00Z", json("cancelled_at").str)
    assertEquals(ujson.Null, json("reason"))
    assertEquals(true, json("prorate").bool, "defaults are serialized by this API")
    assertFalse(json.obj.keySet.exists(_.exists(_.isUpper)), json.toString)
    assertEquals(event, BillingPickle.read[WebhookEvent](json))
  }

  @Test
  def moneyIsCarriedAsCurrencyCodeAndMinorUnits(): Unit = {
    val refund: RefundIssued = RefundIssued(
      refundId = "re_9",
      amount = Money(Currency.getInstance("JPY"), 4500L),
      lineItems = List(LineItem("sku_basic", 2), LineItem("sku_addon", 1))
    )

    val json: String = BillingPickle.write(refund)

    assertTrue(json.contains("\"currency\":\"JPY\""), json)
    assertTrue(json.contains("\"minor_units\":4500"), json)
    assertTrue(json.contains("\"line_items\":["), json)
    assertTrue(json.contains("\"product_code\":\"sku_basic\""), json)
    assertEquals(refund, BillingPickle.read[RefundIssued](json))
  }

  @Test
  def unsupportedCurrencyCodeIsRejectedWithItsPath(): Unit = {
    val payload: String =
      """{"kind":"refund_issued","refund_id":"re_1","amount":{"currency":"XYZQ","minor_units":10},"line_items":[]}"""

    val error: TraceException =
      assertThrows(classOf[TraceException], () => BillingPickle.read[WebhookEvent](payload))

    assertEquals("$['amount']['currency']", error.jsonPath)
    assertTrue(error.getCause.isInstanceOf[AbortException], error.getCause.toString)
    assertTrue(error.getCause.getMessage.contains("unknown currency XYZQ"), error.getCause.getMessage)
  }

  @Test
  def defaultApiStillUsesCamelCaseAndOmitsDefaults(): Unit = {
    val item: LineItem = LineItem("sku_basic", 1)

    assertEquals("""{"product_code":"sku_basic","quantity":1}""", BillingPickle.write(item))
    assertEquals(
      """{"productCode":"sku_basic"}""",
      upickle.default.write(DefaultLineItem("sku_basic"))
    )
  }
}

object BillingWebhookCustomPicklerTest {

  /** The provider's wire conventions: snake_case keys, a `kind` tag and explicit defaults. */
  object BillingPickle extends upickle.AttributeTagged {

    private def camelToSnake(s: String): String = s.replaceAll("([A-Z])", "_$1").toLowerCase

    private def snakeToCamel(s: String): String = {
      val parts: Array[String] = s.split("_")
      parts.head + parts.tail.map(_.capitalize).mkString
    }

    override def tagName: String = "kind"

    override def serializeDefaults: Boolean = true

    override def allowUnknownKeys: Boolean = true

    override def objectAttributeKeyReadMap(s: CharSequence): CharSequence = snakeToCamel(s.toString)

    override def objectAttributeKeyWriteMap(s: CharSequence): CharSequence = camelToSnake(s.toString)

    override def objectTypeKeyReadMap(s: CharSequence): CharSequence = snakeToCamel(s.toString).capitalize

    override def objectTypeKeyWriteMap(s: CharSequence): CharSequence =
      camelToSnake(s.toString).stripPrefix("_")
  }

  given BillingPickle.ReadWriter[Instant] =
    BillingPickle.readwriter[String].bimap[Instant](_.toString, Instant.parse(_))

  given BillingPickle.ReadWriter[Currency] =
    BillingPickle.readwriter[String].bimap[Currency](
      _.getCurrencyCode,
      code =>
        try Currency.getInstance(code)
        catch { case _: IllegalArgumentException => throw Abort(s"unknown currency $code") }
    )

  final case class Money(currency: Currency, minorUnits: Long) derives BillingPickle.ReadWriter

  final case class LineItem(productCode: String, quantity: Int) derives BillingPickle.ReadWriter

  final case class DefaultLineItem(productCode: String, quantity: Int = 1) derives upickle.default.ReadWriter

  sealed trait WebhookEvent derives BillingPickle.ReadWriter

  final case class InvoicePaid(invoiceId: String, amountDue: Money, paidAt: Instant) extends WebhookEvent

  final case class SubscriptionCancelled(
      subscriptionId: String,
      cancelledAt: Instant,
      reason: Option[String],
      prorate: Boolean = true
  ) extends WebhookEvent

  final case class RefundIssued(refundId: String, amount: Money, lineItems: List[LineItem]) extends WebhookEvent
}
