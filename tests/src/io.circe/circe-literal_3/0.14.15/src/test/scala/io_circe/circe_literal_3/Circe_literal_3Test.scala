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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

final class Circe_literal_3Test {
  @Test
  def staticLiteralBuildsNestedJsonWithPrimitiveValues(): Unit = {
    val actual: Json = json"""
      {
        "name": "circe-literal",
        "enabled": true,
        "missing": null,
        "count": 3,
        "items": ["text", false, { "nested": "yes" }],
        "escaped": "line\nsnowman \u2603"
      }
      """

    val expected: Json = Json.obj(
      "name" -> Json.fromString("circe-literal"),
      "enabled" -> Json.True,
      "missing" -> Json.Null,
      "count" -> Json.fromInt(3),
      "items" -> Json.arr(
        Json.fromString("text"),
        Json.False,
        Json.obj("nested" -> Json.fromString("yes"))
      ),
      "escaped" -> Json.fromString("line\nsnowman ☃")
    )

    assertEquals(expected, actual)
  }

  @Test
  def topLevelLiteralsProduceCirceJsonValues(): Unit = {
    val stringValue: Json = json""" "standalone" """
    val trueValue: Json = json""" true """
    val falseValue: Json = json""" false """
    val nullValue: Json = json""" null """
    val numberValue: Json = json""" 42 """
    val arrayValue: Json = json""" [1, "two", true] """

    assertEquals(Json.fromString("standalone"), stringValue)
    assertEquals(Json.True, trueValue)
    assertEquals(Json.False, falseValue)
    assertEquals(Json.Null, nullValue)
    assertEquals(Json.fromInt(42), numberValue)
    assertEquals(Json.arr(Json.fromInt(1), Json.fromString("two"), Json.True), arrayValue)
  }

  @Test
  def numericLiteralsPreserveJsonNumberFormsBeyondIntegers(): Unit = {
    val actual: Json = json"""
      {
        "negativeDecimal": -12.75,
        "exponent": 6.022e23,
        "largeInteger": 123456789012345678901234567890
      }
      """

    val expected: Json = Json.obj(
      "negativeDecimal" -> jsonNumber("-12.75"),
      "exponent" -> jsonNumber("6.022e23"),
      "largeInteger" -> jsonNumber("123456789012345678901234567890")
    )

    assertEquals(expected, actual)
  }

  @Test
  def interpolatedValuesUseCirceEncodersInsideObjects(): Unit = {
    val name: String = "literal"
    val count: Int = 5
    val active: Boolean = true
    val numbers: List[Int] = List(1, 2, 3)
    val nested: Json = Json.obj("ready" -> Json.True)
    val present: Option[String] = Some("available")
    val absent: Option[String] = None

    val actual: Json = json"""
      {
        "name": $name,
        "count": $count,
        "active": $active,
        "numbers": $numbers,
        "nested": $nested,
        "present": $present,
        "absent": $absent
      }
      """

    val expected: Json = Json.obj(
      "name" -> Json.fromString("literal"),
      "count" -> Json.fromInt(5),
      "active" -> Json.True,
      "numbers" -> Json.arr(Json.fromInt(1), Json.fromInt(2), Json.fromInt(3)),
      "nested" -> Json.obj("ready" -> Json.True),
      "present" -> Json.fromString("available"),
      "absent" -> Json.Null
    )

    assertEquals(expected, actual)
  }

  @Test
  def interpolatedValuesUseCirceEncodersInsideArraysAndAtTopLevel(): Unit = {
    val first: String = "alpha"
    val second: Json = Json.obj("rank" -> Json.fromInt(2))
    val third: List[Boolean] = List(true, false)
    val topLevel: Json = json""" $third """

    val actual: Json = json""" [ $first, $second, $third ] """

    val expected: Json = Json.arr(
      Json.fromString("alpha"),
      Json.obj("rank" -> Json.fromInt(2)),
      Json.arr(Json.True, Json.False)
    )

    assertEquals(expected, actual)
    assertEquals(Json.arr(Json.True, Json.False), topLevel)
  }

  @Test
  def interpolatedObjectKeysUseCirceKeyEncoders(): Unit = {
    val stringKey: String = "title"
    val numericKey: Int = 2024
    val customKey: FieldName = FieldName("custom-field")
    given KeyEncoder[FieldName] = KeyEncoder.instance(_.value)

    val actual: Json = json"""
      {
        $stringKey: "Circe",
        $numericKey: true,
        $customKey: [1, 2]
      }
      """

    val expected: Json = Json.obj(
      "title" -> Json.fromString("Circe"),
      "2024" -> Json.True,
      "custom-field" -> Json.arr(Json.fromInt(1), Json.fromInt(2))
    )

    assertEquals(expected, actual)
  }

  @Test
  def interpolatedCustomTypesUseLocalEncoders(): Unit = {
    val person: Person = Person("Ada", 36)
    val team: List[Person] = List(person, Person("Grace", 85))
    given Encoder[Person] = Encoder.instance { value =>
      Json.obj(
        "name" -> Json.fromString(value.name),
        "age" -> Json.fromInt(value.age)
      )
    }

    val actual: Json = json"""
      {
        "person": $person,
        "team": $team
      }
      """

    val expected: Json = Json.obj(
      "person" -> Json.obj(
        "name" -> Json.fromString("Ada"),
        "age" -> Json.fromInt(36)
      ),
      "team" -> Json.arr(
        Json.obj(
          "name" -> Json.fromString("Ada"),
          "age" -> Json.fromInt(36)
        ),
        Json.obj(
          "name" -> Json.fromString("Grace"),
          "age" -> Json.fromInt(85)
        )
      )
    )

    assertEquals(expected, actual)
  }

  private def jsonNumber(value: String): Json =
    JsonNumber.fromString(value).map(Json.fromJsonNumber).get

  private final case class FieldName(value: String)

  private final case class Person(name: String, age: Int)
}
