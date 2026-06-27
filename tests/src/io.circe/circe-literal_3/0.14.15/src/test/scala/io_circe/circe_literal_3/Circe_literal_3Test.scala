/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_literal_3

import io.circe.Encoder
import io.circe.Json
import io.circe.JsonNumber
import io.circe.KeyEncoder
import io.circe.literal.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Circe_literal_3Test {
  @Test
  def topLevelLiteralValuesAreCompiledToCirceJson(): Unit = {
    val trueJson: Json = json"""true"""
    val falseJson: Json = json"""false"""
    val nullJson: Json = json"""null"""
    val integerJson: Json = json"""42"""

    assertThat(trueJson).isEqualTo(Json.True)
    assertThat(falseJson).isEqualTo(Json.False)
    assertThat(nullJson).isEqualTo(Json.Null)
    assertThat(integerJson).isEqualTo(Json.fromInt(42))
  }

  @Test
  def nestedObjectLiteralPreservesArraysEscapesBooleansAndNulls(): Unit = {
    val document: Json = json"""
      {
        "message": "hello\nworld",
        "active": true,
        "deletedAt": null,
        "counts": [1, -2, 3],
        "profile": {
          "name": "Ada",
          "roles": ["admin", "reviewer"]
        }
      }
      """

    val expected: Json = Json.obj(
      "message" -> Json.fromString("hello\nworld"),
      "active" -> Json.True,
      "deletedAt" -> Json.Null,
      "counts" -> Json.arr(Json.fromInt(1), Json.fromInt(-2), Json.fromInt(3)),
      "profile" -> Json.obj(
        "name" -> Json.fromString("Ada"),
        "roles" -> Json.arr(Json.fromString("admin"), Json.fromString("reviewer"))
      )
    )

    assertThat(document).isEqualTo(expected)
  }

  @Test
  def topLevelInterpolationCanEncodeACompleteJsonDocument(): Unit = {
    val payload: Json = Json.obj(
      "status" -> Json.fromString("accepted"),
      "attempts" -> Json.arr(Json.fromInt(1), Json.fromInt(2))
    )

    val document: Json = json"""$payload"""

    assertThat(document).isEqualTo(payload)
  }

  @Test
  def valueInterpolationUsesCirceEncodersForPrimitiveAndCollectionValues(): Unit = {
    val userName: String = "Grace Hopper"
    val loginCount: Int = 7
    val enabled: Boolean = true
    val tags: List[String] = List("compiler", "navy")

    val document: Json = json"""
      {
        "name": $userName,
        "loginCount": $loginCount,
        "enabled": $enabled,
        "tags": $tags
      }
      """

    val expected: Json = Json.obj(
      "name" -> Json.fromString(userName),
      "loginCount" -> Json.fromInt(loginCount),
      "enabled" -> Json.True,
      "tags" -> Json.arr(Json.fromString("compiler"), Json.fromString("navy"))
    )

    assertThat(document).isEqualTo(expected)
  }

  @Test
  def valueInterpolationUsesUserProvidedEncoderInNestedJson(): Unit = {
    final case class Coordinate(x: Int, y: Int)

    given Encoder[Coordinate] = Encoder.instance { coordinate =>
      Json.obj(
        "x" -> Json.fromInt(coordinate.x),
        "y" -> Json.fromInt(coordinate.y)
      )
    }

    val origin: Coordinate = Coordinate(0, 0)
    val destination: Coordinate = Coordinate(3, 4)

    val route: Json = json"""
      {
        "route": [$origin, $destination],
        "metadata": {
          "destination": $destination
        }
      }
      """

    val encodedOrigin: Json = Json.obj("x" -> Json.fromInt(0), "y" -> Json.fromInt(0))
    val encodedDestination: Json = Json.obj("x" -> Json.fromInt(3), "y" -> Json.fromInt(4))
    val expected: Json = Json.obj(
      "route" -> Json.arr(encodedOrigin, encodedDestination),
      "metadata" -> Json.obj("destination" -> encodedDestination)
    )

    assertThat(route).isEqualTo(expected)
  }

  @Test
  def keyInterpolationUsesCirceKeyEncoders(): Unit = {
    final case class FieldName(value: String)

    given KeyEncoder[FieldName] = KeyEncoder.instance(fieldName => s"field-${fieldName.value}")

    val primaryField: FieldName = FieldName("primary")
    val secondaryField: FieldName = FieldName("secondary")
    val primaryValue: String = "ready"
    val secondaryValue: Int = 99

    val document: Json = json"""
      {
        $primaryField: $primaryValue,
        $secondaryField: $secondaryValue,
        "static": "retained"
      }
      """

    val expected: Json = Json.obj(
      "field-primary" -> Json.fromString(primaryValue),
      "field-secondary" -> Json.fromInt(secondaryValue),
      "static" -> Json.fromString("retained")
    )

    assertThat(document).isEqualTo(expected)
  }

  @Test
  def numericLiteralsSupportFractionalAndExponentForms(): Unit = {
    val document: Json = json"""
      {
        "fractional": 0.0001,
        "positiveExponent": 6.022e23,
        "negativeExponent": -1.25E-2
      }
      """

    val expected: Json = Json.obj(
      "fractional" -> jsonNumber("0.0001"),
      "positiveExponent" -> jsonNumber("6.022e23"),
      "negativeExponent" -> jsonNumber("-1.25E-2")
    )

    assertThat(document).isEqualTo(expected)
  }

  private def jsonNumber(value: String): Json = {
    val number: JsonNumber = JsonNumber
      .fromString(value)
      .getOrElse(throw new AssertionError(s"Invalid JSON number: $value"))
    Json.fromJsonNumber(number)
  }
}
