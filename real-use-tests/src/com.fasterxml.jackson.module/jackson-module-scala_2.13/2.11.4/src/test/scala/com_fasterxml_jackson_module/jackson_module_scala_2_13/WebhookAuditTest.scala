/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_scala_2_13

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.{DefaultScalaModule, ScalaObjectMapper}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

/** Exercises Scala untyped JSON data binding for schema-flexible webhook audit redaction. */
@Timeout(60)
class WebhookAuditTest {
  private def redact(value: Any): Any = value match {
    case fields: scala.collection.Map[_, _] => fields.map {
      case (key, entry) =>
        val sanitized: Any =
          if (key == "email" || key == "accessToken") "[redacted]" else redact(entry)
        key -> sanitized
    }
    case entries: scala.collection.Seq[_] => entries.map(redact)
    case scalar => scalar
  }

  @Test
  def redactsNestedSecretsWithoutDiscardingUnknownWebhookFields(): Unit = {
    val mapper: ObjectMapper with ScalaObjectMapper = new ObjectMapper() with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)

    // Different webhook producers can add fields without an audit-service schema change.
    val incoming: String =
      """{
        |  "event": "order-updated",
        |  "accessToken": "private-token",
        |  "customer": {"email": "buyer@example.test", "id": 41},
        |  "changes": [
        |    {"email": "recipient@example.test", "quantity": 3},
        |    {"labels": ["priority", "gift"], "confirmed": true}
        |  ],
        |  "extension": {"attempt": 2, "previous": null, "flags": []}
        |}""".stripMargin
    val decoded: Any = mapper.readValue[Any](incoming)
    val expectedOriginal: Map[String, Any] = Map(
      "event" -> "order-updated",
      "accessToken" -> "private-token",
      "customer" -> Map("email" -> "buyer@example.test", "id" -> 41),
      "changes" -> List(
        Map("email" -> "recipient@example.test", "quantity" -> 3),
        Map("labels" -> List("priority", "gift"), "confirmed" -> true)),
      "extension" -> Map("attempt" -> 2, "previous" -> null, "flags" -> List.empty[Any]))
    assertEquals(expectedOriginal, decoded)

    val sanitized: Any = redact(decoded)
    val published: String = mapper.writeValueAsString(sanitized)
    val auditRecord: JsonNode = mapper.readTree(published)
    val expected: JsonNode = mapper.readTree(
      """{
        |  "event": "order-updated",
        |  "accessToken": "[redacted]",
        |  "customer": {"email": "[redacted]", "id": 41},
        |  "changes": [
        |    {"email": "[redacted]", "quantity": 3},
        |    {"labels": ["priority", "gift"], "confirmed": true}
        |  ],
        |  "extension": {"attempt": 2, "previous": null, "flags": []}
        |}""".stripMargin)
    assertEquals(expected, auditRecord)

    // Redaction leaves the original payload available to the authorized business handler.
    assertEquals(expectedOriginal, decoded)
    val restored: Any = mapper.readValue[Any](published)
    assertEquals(sanitized, restored)
    assertEquals(restored, redact(restored))
  }
}
