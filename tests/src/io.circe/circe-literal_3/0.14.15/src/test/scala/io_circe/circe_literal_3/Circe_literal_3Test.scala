/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_literal_3

import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.KeyEncoder
import io.circe.literal.*
import org.junit.jupiter.api.Test

class Circe_literal_3Test:
  @Test
  def jsonInterpolatorBuildsLiteralDocumentsWithNestedJsonTypes(): Unit =
    val document: Json = json"""
      {
        "name": "circe-literal",
        "enabled": true,
        "missing": null,
        "count": 3,
        "nested": {
          "unicode": "π",
          "escaped": "quote: \" and slash: \\"
        },
        "items": [1, "two", false, null, { "deep": [10.5, -2.0e3] }]
      }
      """

    assert(field[String](document, "name") == "circe-literal")
    assert(field[Boolean](document, "enabled"))
    assert(field[Int](document, "count") == 3)
    assert(focus(document, "missing") == Json.Null)

    val nested: Json = focus(document, "nested")
    assert(field[String](nested, "unicode") == "π")
    assert(field[String](nested, "escaped") == "quote: \" and slash: \\")

    val items: Vector[Json] = arrayValues(focus(document, "items"))
    assert(items(0) == Json.fromInt(1))
    assert(items(1) == Json.fromString("two"))
    assert(items(2) == Json.False)
    assert(items(3) == Json.Null)

    val deepValues: Vector[Json] = arrayValues(focus(items(4), "deep"))
    assert(decoded[BigDecimal](deepValues(0)) == BigDecimal("10.5"))
    assert(decoded[BigDecimal](deepValues(1)) == BigDecimal("-2.0e3"))

  @Test
  def jsonInterpolatorEncodesInterpolatedValuesWithCirceEncoders(): Unit =
    given Encoder[Details] = Encoder.instance { (details: Details) =>
      Json.obj(
        "id" -> Json.fromInt(details.id),
        "label" -> Json.fromString(details.label),
        "active" -> Json.fromBoolean(details.active)
      )
    }

    val id: Int = 42
    val enabled: Boolean = true
    val message: String = "quoted \"value\" with unicode π"
    val tags: List[String] = List("scala-3", "native-image", "circe")
    val optional: Option[String] = None
    val details: Details = Details(7, "macro-generated", active = true)

    val document: Json = json"""
      {
        "id": $id,
        "enabled": $enabled,
        "message": $message,
        "tags": $tags,
        "optional": $optional,
        "details": $details
      }
      """

    assert(field[Int](document, "id") == id)
    assert(field[Boolean](document, "enabled") == enabled)
    assert(field[String](document, "message") == message)
    assert(arrayValues(focus(document, "tags")).map(stringValue) == tags.toVector)
    assert(focus(document, "optional") == Json.Null)

    val encodedDetails: Json = focus(document, "details")
    assert(field[Int](encodedDetails, "id") == details.id)
    assert(field[String](encodedDetails, "label") == details.label)
    assert(field[Boolean](encodedDetails, "active") == details.active)

  @Test
  def jsonInterpolatorEncodesInterpolatedObjectKeysWithKeyEncoders(): Unit =
    given KeyEncoder[MetricKey] = KeyEncoder.instance { (key: MetricKey) =>
      s"${key.namespace}.${key.name}"
    }

    val statusKey: MetricKey = MetricKey("image", "status")
    val countKey: MetricKey = MetricKey("test", "count")
    val status: String = "green"
    val count: Int = 5

    val document: Json = json"""
      {
        $statusKey: $status,
        "nested": {
          $countKey: $count
        }
      }
      """

    assert(field[String](document, "image.status") == status)
    assert(field[Int](focus(document, "nested"), "test.count") == count)

  @Test
  def jsonInterpolatorSupportsTopLevelAndArrayInterpolations(): Unit =
    val payload: Json = Json.obj(
      "source" -> Json.fromString("prebuilt-json"),
      "values" -> Json.arr(Json.fromInt(10), Json.fromInt(20))
    )
    val decimal: BigDecimal = BigDecimal("12345.678")
    val topLevelText: String = "line one\nline two \"quoted\""

    val topLevelJson: Json = json"""$payload"""
    val topLevelString: Json = json"""$topLevelText"""
    val array: Json = json"""
      [
        $payload,
        $decimal,
        $topLevelText,
        true
      ]
      """

    assert(topLevelJson == payload)
    assert(stringValue(topLevelString) == topLevelText)

    val values: Vector[Json] = arrayValues(array)
    assert(values(0) == payload)
    assert(decoded[BigDecimal](values(1)) == decimal)
    assert(stringValue(values(2)) == topLevelText)
    assert(values(3) == Json.True)

  private final case class Details(id: Int, label: String, active: Boolean)

  private final case class MetricKey(namespace: String, name: String)

  private def field[A](json: Json, fieldName: String)(using Decoder[A]): A =
    json.hcursor.get[A](fieldName).fold(
      failure =>
        throw new AssertionError(
          s"Unable to decode field '$fieldName' from ${json.noSpaces}",
          failure
        ),
      identity
    )

  private def focus(json: Json, fieldName: String): Json =
    json.hcursor.downField(fieldName).focus.getOrElse {
      throw new AssertionError(
        s"Unable to find field '$fieldName' in ${json.noSpaces}"
      )
    }

  private def arrayValues(json: Json): Vector[Json] =
    json.asArray.getOrElse {
      throw new AssertionError(s"Expected JSON array but found ${json.noSpaces}")
    }

  private def stringValue(json: Json): String =
    json.asString.getOrElse {
      throw new AssertionError(s"Expected JSON string but found ${json.noSpaces}")
    }

  private def decoded[A](json: Json)(using Decoder[A]): A =
    json.as[A].fold(
      failure =>
        throw new AssertionError(
          s"Unable to decode JSON value ${json.noSpaces}",
          failure
        ),
      identity
    )
