/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_json_3

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import java.io.ByteArrayInputStream
import java.math.BigDecimal as JavaBigDecimal
import java.math.MathContext
import java.nio.charset.StandardCharsets
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import play.api.libs.json.BigDecimalParseConfig
import play.api.libs.json.BigDecimalSerializerConfig
import play.api.libs.json.JsArray
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.JsonConfig
import play.api.libs.json.jackson.PlayJsonMapperModule

/**
 * Jackson integration through `PlayJsonMapperModule` and the byte, stream and text codecs of `Json`
 * under default and custom `JsonConfig` number settings.
 *
 * A payment gateway shares one Jackson `ObjectMapper` between its legacy Jackson code and its
 * Play JSON handlers: inbound webhook bytes are bound straight to `JsObject` by the mapper,
 * a ledger entry is handed back to Jackson as a `JsonNode` tree and read back as `JsValue`,
 * receipts are written as compact, ASCII-safe and pretty-printed text, and a hardened mapper
 * with tight `BigDecimal` limits rejects oversized amounts and renders large totals in
 * scientific notation.
 */
class PaymentWebhookJacksonGatewayTest {
  import PaymentWebhookJacksonGatewayTest.*

  @Test
  def webhookPayloadsFlowThroughSharedJacksonMapper(): Unit = {
    val mapper: ObjectMapper = JsonMapper.builder().addModule(new PlayJsonMapperModule(JsonConfig.settings)).build()

    val webhookBytes: Array[Byte] =
      """{"event":"payment.captured","payment":{"id":"pay_42","amount":149.90,"currency":"EUR",
        |"payer":{"name":"Jos\u00e9 Zu\u00f1iga","city":"Z\u00fcrich"}},"attempts":[1,2],"livemode":false,
        |"refundOf":null}""".stripMargin.getBytes(StandardCharsets.UTF_8)
    val webhook: JsObject = mapper.readValue(webhookBytes, classOf[JsObject])
    assertEquals("payment.captured", (webhook \ "event").as[String])
    assertEquals(BigDecimal("149.90"), (webhook \ "payment" \ "amount").as[BigDecimal])
    assertEquals("Z\u00fcrich", (webhook \ "payment" \ "payer" \ "city").as[String])
    assertEquals(Seq(1, 2), (webhook \ "attempts").as[Seq[Int]])
    assertEquals(Json.parse(webhookBytes), webhook)

    val attempts: JsArray = mapper.readValue("[1, 2.5, \"retry\", true, null]", classOf[JsArray])
    assertEquals(5, attempts.value.size)
    assertEquals(JsNumber(BigDecimal("2.5")), attempts(1))

    val ledgerEntry: JsObject = Json.obj(
      "paymentId" -> (webhook \ "payment" \ "id").as[String],
      "amount" -> (webhook \ "payment" \ "amount").as[BigDecimal],
      "settled" -> true,
      "tags" -> Json.arr("card", "eu")
    )
    val tree: JsonNode = mapper.valueToTree[JsonNode](ledgerEntry)
    assertEquals("pay_42", tree.get("paymentId").asText())
    assertEquals(0, new JavaBigDecimal("149.9").compareTo(tree.get("amount").decimalValue()))
    assertTrue(tree.get("settled").booleanValue())
    assertEquals("eu", tree.get("tags").get(1).asText())
    assertEquals(ledgerEntry, mapper.treeToValue(tree, classOf[JsValue]))

    val receipt: JsObject = Json.obj("paymentId" -> "pay_42", "payer" -> "Jos\u00e9", "total" -> BigDecimal("149.90"))
    val compact: String = mapper.writeValueAsString(receipt)
    assertEquals(Json.stringify(receipt), compact)
    assertEquals("{\"paymentId\":\"pay_42\",\"payer\":\"Jos\u00e9\",\"total\":149.9}", compact)
    assertEquals(
      "{\"paymentId\":\"pay_42\",\"payer\":\"Jos\\u00E9\",\"total\":149.9}",
      Json.asciiStringify(receipt)
    )
    assertEquals(
      "{\n  \"paymentId\" : \"pay_42\",\n  \"payer\" : \"Jos\u00e9\",\n  \"total\" : 149.9\n}",
      Json.prettyPrint(receipt)
    )

    val stored: Array[Byte] = Json.toBytes(receipt)
    assertEquals(receipt, Json.parse(new ByteArrayInputStream(stored)))
  }

  @Test
  def hardenedMapperEnforcesNumberLimits(): Unit = {
    val hardened: ObjectMapper = new ObjectMapper().registerModule(new PlayJsonMapperModule(HardenedSettings))

    val accepted: JsObject = hardened.readValue("""{"amount": 1234.56}""", classOf[JsObject])
    assertEquals(BigDecimal("1234.56"), (accepted \ "amount").as[BigDecimal])

    val oversized: Exception = assertThrows(
      classOf[Exception],
      () => hardened.readValue("""{"amount": 123456789012345678901234567890}""", classOf[JsObject])
    )
    assertTrue(rootCause(oversized).isInstanceOf[IllegalArgumentException], String.valueOf(oversized))

    val totals: JsObject = Json.obj("daily" -> BigDecimal("25000000"), "fee" -> BigDecimal("0.50"))
    assertEquals("""{"daily":2.5E+7,"fee":0.5}""", hardened.writeValueAsString(totals))
    assertEquals("""{"daily":25000000,"fee":0.5}""", Json.stringify(totals))
  }
}

object PaymentWebhookJacksonGatewayTest {
  /** Rejects numbers longer than 20 digits and prints values outside (0.001, 1000000) in exponent form. */
  val HardenedSettings: JsonConfig = JsonConfig(
    BigDecimalParseConfig(MathContext.DECIMAL64, scaleLimit = 50, digitsLimit = 20),
    BigDecimalSerializerConfig(minPlain = BigDecimal("0.001"), maxPlain = BigDecimal("1000000"))
  )

  def rootCause(error: Throwable): Throwable =
    if (error.getCause == null || (error.getCause eq error)) error else rootCause(error.getCause)
}
