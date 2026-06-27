/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_literal_3

import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.Json
import io.circe.KeyDecoder
import io.circe.KeyEncoder
import io.circe.Printer
import io.circe.literal.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

final case class LiteralAddress(city: String, postalCode: Int)

object LiteralAddress {
  given Encoder.AsObject[LiteralAddress] = Encoder.forProduct2("city", "postalCode") { address =>
    (address.city, address.postalCode)
  }

  given Decoder[LiteralAddress] = Decoder.forProduct2("city", "postalCode")(LiteralAddress.apply)
}

final case class LiteralEvent(
    id: String,
    address: LiteralAddress,
    tags: List[String],
    metrics: Map[String, BigDecimal],
    enabled: Boolean
)

object LiteralEvent {
  given Encoder.AsObject[LiteralEvent] = Encoder.forProduct5("id", "address", "tags", "metrics", "enabled") { event =>
    (event.id, event.address, event.tags, event.metrics, event.enabled)
  }

  given Decoder[LiteralEvent] = Decoder.forProduct5("id", "address", "tags", "metrics", "enabled")(LiteralEvent.apply)
}

final case class LiteralMetricKey(namespace: String, name: String)

object LiteralMetricKey {
  given KeyEncoder[LiteralMetricKey] = KeyEncoder.instance { key =>
    s"${key.namespace}:${key.name}"
  }

  given KeyDecoder[LiteralMetricKey] = KeyDecoder.instance { key =>
    key.split(":", 2).toList match {
      case namespace :: name :: Nil if namespace.nonEmpty && name.nonEmpty => Some(LiteralMetricKey(namespace, name))
      case _ => None
    }
  }
}

class Circe_literal_3Test {
  @Test
  def createsStaticJsonLiteralsForEveryJsonType(): Unit = {
    val document: Json = json"""
      {
        "string": "line one\nline two",
        "escapedQuote": "She said \"hello\"",
        "unicode": "\u2603",
        "integer": 42,
        "decimal": 12345678901234567890.125,
        "exponent": -6.022e23,
        "boolean": true,
        "nullValue": null,
        "array": [false, null, { "nested": "value" }]
      }
    """

    assertThat(document.isObject).isTrue
    assertThat(document.hcursor.get[String]("string")).isEqualTo(Right("line one\nline two"))
    assertThat(document.hcursor.get[String]("escapedQuote")).isEqualTo(Right("She said \"hello\""))
    assertThat(document.hcursor.get[String]("unicode")).isEqualTo(Right("☃"))
    assertThat(document.hcursor.get[Int]("integer")).isEqualTo(Right(42))
    assertThat(document.hcursor.get[BigDecimal]("decimal")).isEqualTo(Right(BigDecimal("12345678901234567890.125")))
    assertThat(document.hcursor.get[BigDecimal]("exponent")).isEqualTo(Right(BigDecimal("-6.022e23")))
    assertThat(document.hcursor.get[Boolean]("boolean")).isEqualTo(Right(true))
    assertThat(document.hcursor.downField("nullValue").focus).isEqualTo(Some(Json.Null))
    assertThat(document.hcursor.downField("array").downN(2).get[String]("nested")).isEqualTo(Right("value"))
  }

  @Test
  def producesTopLevelPrimitiveArrayAndObjectValues(): Unit = {
    val stringLiteral: Json = json""" "top-level string" """
    val numberLiteral: Json = json"""9007199254740993"""
    val booleanLiteral: Json = json"""false"""
    val nullLiteral: Json = json"""null"""
    val arrayLiteral: Json = json"""[1, "two", true, null]"""
    val objectLiteral: Json = json"""{ "createdBy": "circe-literal" }"""

    assertThat(stringLiteral.asString).isEqualTo(Some("top-level string"))
    assertThat(numberLiteral.asNumber.flatMap(_.toLong)).isEqualTo(Some(9007199254740993L))
    assertThat(booleanLiteral.asBoolean).isEqualTo(Some(false))
    assertThat(nullLiteral).isEqualTo(Json.Null)
    assertThat(arrayLiteral.asArray.map(_.size)).isEqualTo(Some(4))
    assertThat(objectLiteral.hcursor.get[String]("createdBy")).isEqualTo(Right("circe-literal"))
  }

  @Test
  def interpolatesEncodedValuesIntoObjectsArraysAndTopLevelPositions(): Unit = {
    val serviceName: String = "payments"
    val retries: Int = 3
    val enabled: Boolean = true
    val thresholds: List[BigDecimal] = List(BigDecimal("0.75"), BigDecimal("0.95"))
    val nested: Json = json"""{ "source": "literal", "safe": true }"""

    val document: Json = json"""
      {
        "service": $serviceName,
        "retries": $retries,
        "enabled": $enabled,
        "thresholds": $thresholds,
        "nested": $nested,
        "history": [$serviceName, $retries, $enabled, $thresholds]
      }
    """
    val topLevelList: Json = json"""$thresholds"""

    assertThat(document.hcursor.get[String]("service")).isEqualTo(Right("payments"))
    assertThat(document.hcursor.get[Int]("retries")).isEqualTo(Right(3))
    assertThat(document.hcursor.get[Boolean]("enabled")).isEqualTo(Right(true))
    assertThat(decodeOrFail(document.hcursor.get[List[BigDecimal]]("thresholds"))).isEqualTo(thresholds)
    assertThat(document.hcursor.downField("nested").get[String]("source")).isEqualTo(Right("literal"))
    assertThat(document.hcursor.downField("history").downN(3).as[List[BigDecimal]]).isEqualTo(Right(thresholds))
    assertThat(topLevelList.as[List[BigDecimal]]).isEqualTo(Right(thresholds))
  }

  @Test
  def usesCustomEncodersWhenInterpolatingDomainValues(): Unit = {
    val event: LiteralEvent = LiteralEvent(
      id = "event-1",
      address = LiteralAddress("Belgrade", 11000),
      tags = List("json", "literal", "scala3"),
      metrics = Map("latency" -> BigDecimal("12.50"), "successRate" -> BigDecimal("0.995")),
      enabled = true
    )

    val envelope: Json = json"""
      {
        "kind": "domain-event",
        "event": $event,
        "copies": [$event, $event]
      }
    """
    val topLevelEvent: Json = json"""$event"""

    assertThat(envelope.hcursor.get[String]("kind")).isEqualTo(Right("domain-event"))
    assertThat(envelope.hcursor.downField("event").as[LiteralEvent]).isEqualTo(Right(event))
    assertThat(envelope.hcursor.downField("copies").downN(1).as[LiteralEvent]).isEqualTo(Right(event))
    assertThat(topLevelEvent.as[LiteralEvent]).isEqualTo(Right(event))
  }

  @Test
  def interpolatesObjectKeysWithKeyEncoders(): Unit = {
    val latencyKey: LiteralMetricKey = LiteralMetricKey("http", "latencyMillis")
    val statusKey: LiteralMetricKey = LiteralMetricKey("http", "status")
    val latency: BigDecimal = BigDecimal("18.25")
    val status: Int = 200

    val metrics: Json = json"""
      {
        $latencyKey: $latency,
        $statusKey: $status,
        "static:key": 1
      }
    """

    assertThat(metrics.hcursor.get[BigDecimal]("http:latencyMillis")).isEqualTo(Right(BigDecimal("18.25")))
    assertThat(metrics.hcursor.get[Int]("http:status")).isEqualTo(Right(200))
    assertThat(metrics.hcursor.get[Int]("static:key")).isEqualTo(Right(1))

    val decoded: Map[LiteralMetricKey, BigDecimal] = decodeOrFail(metrics.as[Map[LiteralMetricKey, BigDecimal]])
    assertThat(decoded.get(latencyKey)).isEqualTo(Some(BigDecimal("18.25")))
    assertThat(decoded.get(statusKey)).isEqualTo(Some(BigDecimal("200")))
  }

  @Test
  def supportsStringKeysAndValuesContainingPlaceholderLikeText(): Unit = {
    val contentTypeKey: String = "content-type"
    val requestIdKey: String = "x-request-id"
    val literalText: String = "this value contains $contentTypeKey as ordinary text"

    val headers: Json = json"""
      {
        $contentTypeKey: "application/json",
        $requestIdKey: "req-123",
        "description": $literalText,
        "literalDollar": "$$contentTypeKey remains a JSON string"
      }
    """

    assertThat(headers.hcursor.get[String]("content-type")).isEqualTo(Right("application/json"))
    assertThat(headers.hcursor.get[String]("x-request-id")).isEqualTo(Right("req-123"))
    assertThat(headers.hcursor.get[String]("description")).isEqualTo(Right(literalText))
    assertThat(headers.hcursor.get[String]("literalDollar")).isEqualTo(Right("$contentTypeKey remains a JSON string"))
  }

  @Test
  def escapesInterpolatedKeysAndStringValuesWithoutParsingThem(): Unit = {
    val specialKey: String = "quote\"brace}comma,key"
    val newlineKey: String = "line\nbreak"
    val jsonShapedValue: String = """{"admin":true,"roles":["root"]}"""
    val pathValue: String = "folder\\file\nnext"

    val document: Json = json"""
      {
        $specialKey: $jsonShapedValue,
        "array": [$jsonShapedValue],
        "nested": { $newlineKey: $pathValue }
      }
    """

    assertThat(document.hcursor.get[String](specialKey)).isEqualTo(Right(jsonShapedValue))
    assertThat(document.hcursor.downField(specialKey).downField("admin").succeeded).isFalse
    assertThat(document.hcursor.downField("array").downN(0).as[String]).isEqualTo(Right(jsonShapedValue))
    assertThat(document.hcursor.downField("nested").get[String](newlineKey)).isEqualTo(Right(pathValue))
    assertThat(document.noSpaces).contains("\\\"admin\\\":true")
    assertThat(document.noSpaces).contains("line\\nbreak")
  }

  @Test
  def preservesSourceOrderForStaticAndInterpolatedObjectFields(): Unit = {
    val secondKey: String = "second"
    val fourthKey: String = "fourth"

    val document: Json = json"""
      {
        "first": 1,
        $secondKey: 2,
        "third": { "nestedFirst": true, "nestedSecond": false },
        $fourthKey: 4
      }
    """

    val fieldNames: List[String] = document.asObject.map(_.keys.toList).getOrElse(fail("Expected an object literal"))
    val nestedFieldNames: List[String] = document.hcursor
      .downField("third")
      .focus
      .flatMap(_.asObject.map(_.keys.toList))
      .getOrElse(fail("Expected a nested object literal"))

    assertThat(fieldNames.asJava).containsExactly("first", "second", "third", "fourth")
    assertThat(nestedFieldNames.asJava).containsExactly("nestedFirst", "nestedSecond")
    assertThat(document.noSpaces).isEqualTo(
      """{"first":1,"second":2,"third":{"nestedFirst":true,"nestedSecond":false},"fourth":4}"""
    )
  }

  @Test
  def literalResultsRemainFullyUsableCirceJsonValues(): Unit = {
    val base: Json = json"""
      {
        "users": [
          { "name": "Ada", "scores": [1, 2] },
          { "name": "Linus", "scores": [3, 5] }
        ],
        "removeMe": null
      }
    """
    val patch: Json = json"""
      {
        "users": [
          { "name": "Ada", "scores": [1, 2, 13] },
          { "name": "Linus", "scores": [3, 5, 8] }
        ],
        "active": true
      }
    """

    val merged: Json = base.deepDropNullValues.deepMerge(patch)
    val firstUserScores: List[Int] = decodeOrFail(merged.hcursor.downField("users").downArray.get[List[Int]]("scores"))
    val prettyPrinted: String = Printer.spaces2SortKeys.print(merged)

    assertThat(firstUserScores.asJava).containsExactly(1, 2, 13)
    assertThat(merged.hcursor.downField("removeMe").succeeded).isFalse
    assertThat(merged.hcursor.get[Boolean]("active")).isEqualTo(Right(true))
    assertThat(prettyPrinted).contains("\n  \"active\" : true")
    assertThat(merged.noSpacesSortKeys)
      .isEqualTo("""{"active":true,"users":[{"name":"Ada","scores":[1,2,13]},{"name":"Linus","scores":[3,5,8]}]}""")
  }

  private def decodeOrFail[A](result: Either[DecodingFailure, A]): A = {
    result.fold(error => fail(s"Expected decoding success but got: $error"), identity)
  }
}
