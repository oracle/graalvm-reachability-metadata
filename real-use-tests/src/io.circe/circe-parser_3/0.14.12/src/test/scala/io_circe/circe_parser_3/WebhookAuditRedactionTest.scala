/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_parser_3

import io.circe.Json
import io.circe.parser.parse
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse}
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/** Exercises JSON document parsing and cursor-based redaction for webhook audit records. */
@Timeout(60)
class WebhookAuditRedactionTest {
  @Test
  def redactsPaymentSecretsWhilePreservingTheWebhookDocument(): Unit = {
    val webhook: String =
      """{
        |  "event": "payment.completed",
        |  "signature": "secret-webhook-signature",
        |  "customer": {
        |    "id": "customer-17",
        |    "payment": {"cardNumber": "4111111111114242", "currency": "USD"}
        |  },
        |  "transactionId": 9007199254740993,
        |  "amount": 1234567890.123456789,
        |  "items": [{"sku": "book-1", "quantity": 2}],
        |  "extension": {"futureField": [true, null, "keep me"]}
        |}""".stripMargin

    val document: Json = parse(webhook).fold(error => throw error, identity)
    val cardNumber: String = document.hcursor
      .downField("customer")
      .downField("payment")
      .get[String]("cardNumber")
      .fold(error => throw error, identity)

    val auditDocument: Json = document
      .mapObject(_.remove("signature"))
      .hcursor
      .downField("customer")
      .downField("payment")
      .downField("cardNumber")
      .withFocus(_ => Json.fromString("****" + cardNumber.takeRight(4)))
      .top
      .getOrElse(throw new IllegalArgumentException("Missing payment card field"))

    val storedAudit: String = auditDocument.noSpaces
    assertFalse(storedAudit.contains("secret-webhook-signature"))
    assertFalse(storedAudit.contains(cardNumber))

    val expectedAudit: String =
      """{
        |  "event": "payment.completed",
        |  "customer": {
        |    "id": "customer-17",
        |    "payment": {"cardNumber": "****4242", "currency": "USD"}
        |  },
        |  "transactionId": 9007199254740993,
        |  "amount": 1234567890.123456789,
        |  "items": [{"sku": "book-1", "quantity": 2}],
        |  "extension": {"futureField": [true, null, "keep me"]}
        |}""".stripMargin

    val restoredAudit: Json = parse(storedAudit).fold(error => throw error, identity)
    assertEquals(parse(expectedAudit), Right(restoredAudit))
    assertEquals(Right(9007199254740993L), restoredAudit.hcursor.get[Long]("transactionId"))
    assertEquals(
      Right(BigDecimal("1234567890.123456789")),
      restoredAudit.hcursor.get[BigDecimal]("amount")
    )
  }
}
