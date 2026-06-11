/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_literal_3

import io.circe.Json
import io.circe.literal.*
import org.junit.jupiter.api.Test

class Circe_literal_3Test:
  @Test
  def jsonLiteralBuildsNestedDocumentsWithAllJsonValueKinds(): Unit =
    val document: Json = json"""
      {
        "string": "line\nbreak and \"quoted\" text",
        "integer": 123,
        "decimal": -12.75,
        "boolean": true,
        "nothing": null,
        "array": [1, "two", false, null],
        "object": {
          "emptyObject": {},
          "emptyArray": [],
          "nested": { "enabled": false }
        }
      }
    """
    val cursor = document.hcursor

    assert(cursor.downField("string").as[String] == Right("line\nbreak and \"quoted\" text"))
    assert(cursor.downField("integer").as[Int] == Right(123))
    assert(cursor.downField("decimal").as[BigDecimal] == Right(BigDecimal("-12.75")))
    assert(cursor.downField("boolean").as[Boolean] == Right(true))
    assert(cursor.downField("nothing").focus.contains(Json.Null))
    assert(cursor.downField("array").focus.contains(Json.arr(
      Json.fromInt(1),
      Json.fromString("two"),
      Json.fromBoolean(false),
      Json.Null
    )))
    assert(cursor.downField("object").downField("emptyObject").focus.exists(_.asObject.exists(_.isEmpty)))
    assert(cursor.downField("object").downField("emptyArray").focus.exists(_.asArray.exists(_.isEmpty)))
    assert(cursor.downField("object").downField("nested").downField("enabled").as[Boolean] == Right(false))

  @Test
  def jsonLiteralSupportsTopLevelScalarValues(): Unit =
    val stringValue: Json = json""" "circe literal" """
    val numberValue: Json = json""" 42 """
    val decimalValue: Json = json""" 3.1415 """
    val booleanValue: Json = json""" false """
    val nullValue: Json = json""" null """

    assert(stringValue.asString.contains("circe literal"))
    assert(numberValue.asNumber.exists(_.toInt.contains(42)))
    assert(decimalValue.asNumber.exists(_.toBigDecimal.contains(BigDecimal("3.1415"))))
    assert(booleanValue.asBoolean.contains(false))
    assert(nullValue.isNull)

  @Test
  def jsonLiteralInterpolatesJsonValuesInsideObjectsAndArrays(): Unit =
    val address: Json = json"""
      {
        "city": "Belgrade",
        "coordinates": [44.8125, 20.4612]
      }
    """
    val roles: Json = Json.arr(Json.fromString("admin"), Json.fromString("operator"))
    val profile: Json = json"""
      {
        "name": "Ada",
        "active": true,
        "age": 37,
        "address": $address,
        "roles": $roles
      }
    """
    val cursor = profile.hcursor

    assert(cursor.downField("name").as[String] == Right("Ada"))
    assert(cursor.downField("active").as[Boolean] == Right(true))
    assert(cursor.downField("age").as[Int] == Right(37))
    assert(cursor.downField("address").focus.contains(address))
    assert(cursor.downField("roles").focus.contains(roles))

  @Test
  def jsonLiteralInterpolatesScalaValuesWithEncodersAndDynamicObjectKeys(): Unit =
    val nameKey: String = "name"
    val metadataKey: String = "metadata"
    val name: String = "Grace Hopper"
    val score: Int = 99
    val flags: List[Boolean] = List(true, false, true)
    val absentNickname: Option[String] = None
    val document: Json = json"""
      {
        $nameKey: $name,
        "score": $score,
        "flags": $flags,
        "nickname": $absentNickname,
        $metadataKey: {
          "source": "encoder interpolation"
        }
      }
    """
    val cursor = document.hcursor

    assert(cursor.downField("name").as[String] == Right("Grace Hopper"))
    assert(cursor.downField("score").as[Int] == Right(99))
    assert(cursor.downField("flags").as[List[Boolean]] == Right(List(true, false, true)))
    assert(cursor.downField("nickname").focus.contains(Json.Null))
    assert(cursor.downField("metadata").downField("source").as[String] == Right("encoder interpolation"))

  @Test
  def jsonLiteralInterpolatesValuesAtMultipleArrayPositions(): Unit =
    val first: Json = Json.obj("id" -> Json.fromInt(1), "label" -> Json.fromString("first"))
    val second: Json = json""" { "id": 2, "label": "second" } """
    val events: Json = json"""
      {
        "events": [
          $first,
          { "id": 99, "label": "inline" },
          $second
        ]
      }
    """
    val eventValues: Vector[Json] = events.hcursor.downField("events").focus.flatMap(_.asArray).getOrElse(Vector.empty)

    assert(eventValues == Vector(
      first,
      Json.obj("id" -> Json.fromInt(99), "label" -> Json.fromString("inline")),
      second
    ))

  @Test
  def jsonLiteralPreservesEscapedStringsAndProducesStableCompactJson(): Unit =
    val document: Json = json"""
      {
        "quote": "She said \"hello\"",
        "path": "C:\\tmp\\circe.json",
        "unicode": "snowman ☃"
      }
    """
    val cursor = document.hcursor

    assert(cursor.downField("quote").as[String] == Right("She said \"hello\""))
    assert(cursor.downField("path").as[String] == Right("C:\\tmp\\circe.json"))
    assert(cursor.downField("unicode").as[String] == Right("snowman ☃"))
    assert(document.noSpaces == "{\"quote\":\"She said \\\"hello\\\"\",\"path\":\"C:\\\\tmp\\\\circe.json\",\"unicode\":\"snowman ☃\"}")
