/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_plokhotnyuk_jsoniter_scala.jsoniter_scala_core_3

import com.github.plokhotnyuk.jsoniter_scala.core.JsonReaderException
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.core.WriterConfig
import com.github.plokhotnyuk.jsoniter_scala.core.readFromArray
import com.github.plokhotnyuk.jsoniter_scala.core.readFromString
import com.github.plokhotnyuk.jsoniter_scala.core.writeToArray
import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import com.github.plokhotnyuk.jsoniter_scala.macros.CodecMakerConfig
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant

import OrderPayloadRoundTripTest.*

/**
 * Whole-document string and byte-array reading and writing of a macro-derived domain model:
 * an order service renders nested case classes, a sealed payment hierarchy, optional fields,
 * defaults, maps and `java.time` values to JSON, accepts client payloads that omit defaulted
 * fields, and rejects malformed documents with a positioned error.
 */
class OrderPayloadRoundTripTest {

  private val sample: Order = Order(
    id = "ord-1001",
    placedAt = Instant.parse("2026-03-14T09:26:53Z"),
    customer = Customer("Ada Lovelace", "ada@example.com", note = Some("leave at door")),
    items = List(
      LineItem("SKU-1", 2, Money(BigDecimal("19.99"), "EUR")),
      LineItem("SKU-2", 1, Money(BigDecimal("5.00"), "EUR"))
    ),
    payment = Card("4242", "VISA"),
    tags = Map("channel" -> "web", "campaign" -> "spring")
  )

  @Test
  def compactDocumentOmitsDefaultsAndRoundTrips(): Unit = {
    val json: String = writeToString(sample)

    assertTrue(json.startsWith("""{"id":"ord-1001","placedAt":"2026-03-14T09:26:53Z""""), json)
    assertTrue(json.contains(""""payment":{"kind":"Card","last4":"4242","network":"VISA"}"""), json)
    assertTrue(json.contains(""""amount":19.99"""), json)
    assertFalse(json.contains("priority"), json)
    assertFalse(json.contains("giftWrap"), json)
    assertEquals(sample, readFromString[Order](json))
  }

  @Test
  def prettyPrintedBytesRoundTripEveryPaymentKind(): Unit = {
    val pretty: WriterConfig = WriterConfig.withIndentionStep(2)
    val orders: List[Order] = List(
      sample,
      sample.copy(id = "ord-1002", payment = BankTransfer("DE89370400440532013000"), priority = 3),
      sample.copy(id = "ord-1003", payment = CashOnDelivery, customer = sample.customer.copy(note = None))
    )

    orders.foreach { order =>
      val bytes: Array[Byte] = writeToArray(order, pretty)
      val text: String = new String(bytes, UTF_8)
      assertTrue(text.startsWith("{\n  \"id\": \"" + order.id + "\""), text)
      assertEquals(order, readFromArray[Order](bytes))
    }
    val cod: String = new String(writeToArray(orders(2)), UTF_8)
    assertTrue(cod.contains(""""payment":{"kind":"CashOnDelivery"}"""), cod)
    assertFalse(cod.contains("note"), cod)
  }

  @Test
  def clientPayloadWithoutDefaultedFieldsGetsDefaults(): Unit = {
    val payload: String =
      """{
        |  "id": "ord-2001",
        |  "placedAt": "2026-04-01T12:00:00Z",
        |  "customer": {"name": "Grace Hopper", "email": "grace@example.com"},
        |  "items": [{"sku": "SKU-9", "quantity": 4, "unitPrice": {"amount": 2.5, "currency": "USD"}}],
        |  "payment": {"kind": "BankTransfer", "iban": "GB33BUKB20201555555555"},
        |  "unknownField": [1, 2, {"ignored": true}]
        |}""".stripMargin

    val order: Order = readFromString[Order](payload)

    assertEquals("Grace Hopper", order.customer.name)
    assertEquals(None, order.customer.note)
    assertEquals(0, order.priority)
    assertFalse(order.giftWrap)
    assertEquals(Map.empty[String, String], order.tags)
    assertEquals(BankTransfer("GB33BUKB20201555555555"), order.payment)
    assertEquals(BigDecimal("10.0"), order.total)
  }

  @Test
  def malformedDocumentsAreRejectedWithPosition(): Unit = {
    val missingRequired: JsonReaderException = assertThrows(
      classOf[JsonReaderException],
      () => readFromString[Order]("""{"id":"ord-3001"}""")
    )
    assertTrue(missingRequired.getMessage.contains("missing required field"), missingRequired.getMessage)

    val unknownPayment: JsonReaderException = assertThrows(
      classOf[JsonReaderException],
      () => readFromString[Payment]("""{"kind":"Crypto","wallet":"0xabc"}""")
    )
    assertTrue(unknownPayment.getMessage.contains("illegal value of discriminator field \"kind\""),
      unknownPayment.getMessage)

    val badTimestamp: JsonReaderException = assertThrows(
      classOf[JsonReaderException],
      () => readFromString[Order](writeToString(sample).replace("2026-03-14T09:26:53Z", "yesterday"))
    )
    assertTrue(badTimestamp.getMessage.contains("offset: 0x"), badTimestamp.getMessage)
  }
}

object OrderPayloadRoundTripTest {

  final case class Money(amount: BigDecimal, currency: String)

  final case class LineItem(sku: String, quantity: Int, unitPrice: Money)

  final case class Customer(name: String, email: String, note: Option[String] = None)

  sealed trait Payment

  final case class Card(last4: String, network: String) extends Payment

  final case class BankTransfer(iban: String) extends Payment

  case object CashOnDelivery extends Payment

  final case class Order(
    id: String,
    placedAt: Instant,
    customer: Customer,
    items: List[LineItem],
    payment: Payment,
    tags: Map[String, String] = Map.empty,
    priority: Int = 0,
    giftWrap: Boolean = false
  ) {
    def total: BigDecimal = items.map(item => item.unitPrice.amount * item.quantity).sum
  }

  given orderCodec: JsonValueCodec[Order] =
    JsonCodecMaker.make(CodecMakerConfig.withDiscriminatorFieldName(Some("kind")))

  given paymentCodec: JsonValueCodec[Payment] =
    JsonCodecMaker.make(CodecMakerConfig.withDiscriminatorFieldName(Some("kind")))
}
