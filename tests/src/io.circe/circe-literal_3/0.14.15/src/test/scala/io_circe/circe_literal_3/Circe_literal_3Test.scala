/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_literal_3

import io.circe.{Encoder, Json, KeyEncoder}
import io.circe.jawn.parse
import io.circe.literal.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Circe_literal_3Test {
  @Test
  def constructsJsonFromLiteralSyntaxWithoutInterpolatedValues(): Unit = {
    val document: Json = json"""
      {
        "name": "circe-literal",
        "active": true,
        "release": null,
        "numbers": [1, 2, 3],
        "nested": {
          "smallDecimal": 0.0001,
          "negativeExponent": -1.2e3
        }
      }
    """

    val expected: Either[io.circe.ParsingFailure, Json] = parse("""
      {
        "name": "circe-literal",
        "active": true,
        "release": null,
        "numbers": [1, 2, 3],
        "nested": {
          "smallDecimal": 0.0001,
          "negativeExponent": -1.2e3
        }
      }
    """)

    assertEquals(expected, Right(document))
  }

  @Test
  def supportsTopLevelJsonLiteralValues(): Unit = {
    assertEquals(Json.Null, json"null")
    assertEquals(Json.True, json"true")
    assertEquals(Json.False, json"false")
    assertEquals(Json.fromInt(42), json"42")
    assertEquals(Json.fromBigDecimal(BigDecimal("12345.6789")), json"12345.6789")
    assertEquals(Json.fromString("plain text"), json""""plain text"""")
  }

  @Test
  def decodesEscapesAndUnicodeInStringLiterals(): Unit = {
    val document: Json = json"""
      {
        "quote": "She said \"hello\"",
        "path": "C:\\tmp\\circe",
        "line": "first\nsecond",
        "unicode": "\u03bb"
      }
    """

    assertEquals(Some("She said \"hello\""), document.hcursor.downField("quote").as[String].toOption)
    assertEquals(Some("C:\\tmp\\circe"), document.hcursor.downField("path").as[String].toOption)
    assertEquals(Some("first\nsecond"), document.hcursor.downField("line").as[String].toOption)
    assertEquals(Some("λ"), document.hcursor.downField("unicode").as[String].toOption)
  }

  @Test
  def interpolatesPrimitiveAndCollectionValuesWithCirceEncoders(): Unit = {
    val count: Int = 13
    val label: String = "alpha"
    val enabled: Boolean = true
    val ratio: BigDecimal = BigDecimal("7.25")
    val tags: Vector[String] = Vector("native", "image")
    val histogram: Map[String, List[Int]] = Map("p50" -> List(10, 11), "p99" -> List(42))
    val optionalValues: List[Option[Int]] = List(Some(1), None, Some(3))

    val document: Json = json"""
      {
        "count": $count,
        "label": $label,
        "enabled": $enabled,
        "ratio": $ratio,
        "tags": $tags,
        "histogram": $histogram,
        "optionalValues": $optionalValues
      }
    """

    val expected: Json = Json.obj(
      "count" -> Json.fromInt(count),
      "label" -> Json.fromString(label),
      "enabled" -> Json.fromBoolean(enabled),
      "ratio" -> Json.fromBigDecimal(ratio),
      "tags" -> Json.arr(Json.fromString("native"), Json.fromString("image")),
      "histogram" -> Json.obj(
        "p50" -> Json.arr(Json.fromInt(10), Json.fromInt(11)),
        "p99" -> Json.arr(Json.fromInt(42))
      ),
      "optionalValues" -> Json.arr(Json.fromInt(1), Json.Null, Json.fromInt(3))
    )

    assertEquals(expected, document)
  }

  @Test
  def interpolatesJsonValuesWithoutStringifyingThem(): Unit = {
    val nested: Json = Json.obj(
      "id" -> Json.fromString("nested"),
      "items" -> Json.arr(Json.fromInt(1), Json.fromInt(2))
    )

    val document: Json = json"""
      {
        "nested": $nested,
        "copies": [$nested, $nested]
      }
    """

    val expected: Json = Json.obj(
      "nested" -> nested,
      "copies" -> Json.arr(nested, nested)
    )

    assertEquals(expected, document)
  }

  @Test
  def treatsInterpolatedStringsAsJsonStringsInsteadOfRawJsonFragments(): Unit = {
    val unsafeLookingString: String = "{\"admin\":true}"

    val document: Json = json"""
      {
        "payload": $unsafeLookingString,
        "array": [$unsafeLookingString]
      }
    """

    assertEquals(Some(unsafeLookingString), document.hcursor.downField("payload").as[String].toOption)
    assertEquals(
      Some(unsafeLookingString),
      document.hcursor.downField("array").downArray.as[String].toOption
    )
  }

  @Test
  def interpolatesStringAndNonStringObjectKeysWithKeyEncoders(): Unit = {
    val stringKey: String = "space and \"quote\""
    val numericKey: Int = 2026
    val firstValue: String = "escaped key"
    val secondValue: Boolean = true

    val document: Json = json"""
      {
        $stringKey: $firstValue,
        $numericKey: $secondValue
      }
    """

    val expected: Json = Json.obj(
      stringKey -> Json.fromString(firstValue),
      numericKey.toString -> Json.fromBoolean(secondValue)
    )

    assertEquals(expected, document)
  }

  @Test
  def usesCustomEncodersAndKeyEncodersAvailableAtInterpolationSite(): Unit = {
    final case class Metric(name: String, samples: List[Int])
    final case class MetricKey(value: String)

    given Encoder[Metric] = Encoder.instance { metric =>
      Json.obj(
        "name" -> Json.fromString(metric.name),
        "samples" -> Json.arr(metric.samples.map(Json.fromInt)*)
      )
    }
    given KeyEncoder[MetricKey] = KeyEncoder.instance(_.value.toUpperCase)

    val metric: Metric = Metric("latency", List(7, 11, 13))
    val key: MetricKey = MetricKey("p95")

    val document: Json = json"""
      {
        $key: $metric,
        "again": $metric
      }
    """

    val encodedMetric: Json = Json.obj(
      "name" -> Json.fromString("latency"),
      "samples" -> Json.arr(Json.fromInt(7), Json.fromInt(11), Json.fromInt(13))
    )
    val expected: Json = Json.obj(
      "P95" -> encodedMetric,
      "again" -> encodedMetric
    )

    assertEquals(expected, document)
  }

  @Test
  def supportsTopLevelInterpolatedValues(): Unit = {
    val text: String = "top-level"
    val number: BigDecimal = BigDecimal("99.5")
    val jsonValue: Json = Json.obj("ok" -> Json.True)

    assertEquals(Json.fromString(text), json"$text")
    assertEquals(Json.fromBigDecimal(number), json"$number")
    assertEquals(jsonValue, json"$jsonValue")
  }
}
