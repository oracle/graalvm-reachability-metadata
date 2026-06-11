/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_literal_3

import io.circe.Encoder
import io.circe.Json
import io.circe.KeyEncoder
import io.circe.literal.*
import org.junit.jupiter.api.Test

class Circe_literal_3Test:
  @Test
  def jsonInterpolatorBuildsNestedJsonDocuments(): Unit =
    val document: Json = json"""
      {
        "library": "circe-literal",
        "active": true,
        "missing": null,
        "numbers": [1, -2, 3.5],
        "metadata": {
          "scala": 3,
          "tags": ["json", "literal"]
        }
      }
      """

    val expected: Json = Json.obj(
      "library" -> Json.fromString("circe-literal"),
      "active" -> Json.True,
      "missing" -> Json.Null,
      "numbers" -> Json.arr(
        Json.fromInt(1),
        Json.fromInt(-2),
        Json.fromBigDecimal(BigDecimal("3.5"))
      ),
      "metadata" -> Json.obj(
        "scala" -> Json.fromInt(3),
        "tags" -> Json.arr(Json.fromString("json"), Json.fromString("literal"))
      )
    )

    assert(document == expected)

  @Test
  def jsonInterpolatorDecodesEscapesAndPreservesNumberValues(): Unit =
    val document: Json = json"""
      {
        "escaped": "line one\nline two with \"quotes\"",
        "unicode": "\u03bb",
        "integer": 9223372036854775807,
        "decimal": -12.75,
        "scientific": 6.02e23
      }
      """
    val cursor = document.hcursor

    assert(cursor.get[String]("escaped") == Right("line one\nline two with \"quotes\""))
    assert(cursor.get[String]("unicode") == Right("λ"))
    assert(cursor.get[Long]("integer") == Right(Long.MaxValue))
    assert(cursor.get[BigDecimal]("decimal") == Right(BigDecimal("-12.75")))
    assert(cursor.get[BigDecimal]("scientific") == Right(BigDecimal("6.02e23")))

  @Test
  def jsonInterpolatorEncodesInterpolatedValuesWithCirceEncoders(): Unit =
    val name: String = "metadata"
    val attempts: Int = 3
    val enabled: Boolean = true
    val aliases: List[String] = List("reachability", "native-image")
    val nested: Json = json"""{"format": "json", "stable": true}"""
    val optionalValue: Option[Int] = Some(42)

    val document: Json = json"""
      {
        "name": $name,
        "attempts": $attempts,
        "enabled": $enabled,
        "aliases": $aliases,
        "nested": $nested,
        "optionalValue": $optionalValue
      }
      """

    val expected: Json = Json.obj(
      "name" -> Json.fromString("metadata"),
      "attempts" -> Json.fromInt(3),
      "enabled" -> Json.True,
      "aliases" -> Json.arr(Json.fromString("reachability"), Json.fromString("native-image")),
      "nested" -> Json.obj("format" -> Json.fromString("json"), "stable" -> Json.True),
      "optionalValue" -> Json.fromInt(42)
    )

    assert(document == expected)

  @Test
  def jsonInterpolatorUsesKeyEncodersForInterpolatedObjectKeys(): Unit =
    final case class FieldName(value: String)
    given KeyEncoder[FieldName] = KeyEncoder.instance((fieldName: FieldName) => fieldName.value)

    val statusKey: FieldName = FieldName("status")
    val countKey: FieldName = FieldName("count")
    val status: String = "covered"
    val count: Int = 2

    val document: Json = json"""{$statusKey: $status, $countKey: $count}"""
    val cursor = document.hcursor

    assert(cursor.get[String]("status") == Right("covered"))
    assert(cursor.get[Int]("count") == Right(2))
    assert(document.asObject.exists(_.keys.toVector == Vector("status", "count")))

  @Test
  def jsonInterpolatorSupportsSingleValueAndArrayReplacementContexts(): Unit =
    final case class TestResult(name: String, passed: Boolean)
    given Encoder[TestResult] = Encoder.instance { (result: TestResult) =>
      Json.obj(
        "name" -> Json.fromString(result.name),
        "passed" -> Json.fromBoolean(result.passed)
      )
    }

    val replacement: TestResult = TestResult("literal expansion", passed = true)
    val single: Json = json"""$replacement"""
    val array: Json = json"""[$replacement, false, null, "constant"]"""

    val encodedReplacement: Json = Json.obj(
      "name" -> Json.fromString("literal expansion"),
      "passed" -> Json.True
    )

    assert(single == encodedReplacement)
    assert(
      array == Json.arr(encodedReplacement, Json.False, Json.Null, Json.fromString("constant"))
    )
