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
import io.circe.literal._
import org.junit.jupiter.api.Test

class Circe_literal_3Test:
  @Test
  def expandsJsonLiteralIntoNestedCirceJsonValues(): Unit =
    val document: Json = json"""
      {
        "name": "circe-literal",
        "enabled": true,
        "retries": 3,
        "ratio": 1.25,
        "tags": ["macro", "json", "scala-3"],
        "nested": {
          "nullValue": null,
          "numbers": [1, 2, 3],
          "flag": false
        }
      }
    """

    val cursor = document.hcursor

    assert(cursor.get[String]("name").contains("circe-literal"))
    assert(cursor.get[Boolean]("enabled").contains(true))
    assert(cursor.get[Int]("retries").contains(3))
    assert(cursor.downField("tags").focus.contains(Json.arr(
      Json.fromString("macro"),
      Json.fromString("json"),
      Json.fromString("scala-3")
    )))
    assert(cursor.downField("nested").downField("numbers").focus.contains(Json.arr(
      Json.fromInt(1),
      Json.fromInt(2),
      Json.fromInt(3)
    )))
    assert(cursor.downField("nested").downField("nullValue").focus.contains(Json.Null))
    assert(cursor.downField("nested").get[Boolean]("flag").contains(false))

  @Test
  def buildsTopLevelJsonValuesFromLiteralAndInterpolatedInputs(): Unit =
    val message: String = "top-level string"
    val count: Int = 17
    val enabled: Boolean = false

    assert(json"null" == Json.Null)
    assert(json"true" == Json.True)
    assert(json"-12.5" == Json.fromDoubleOrNull(-12.5))
    assert(json"$message" == Json.fromString(message))
    assert(json"$count" == Json.fromInt(count))
    assert(json"$enabled" == Json.fromBoolean(enabled))

  @Test
  def encodesInterpolatedScalarCollectionAndJsonValues(): Unit =
    val name: String = "generated"
    val count: Int = 42
    val active: Boolean = true
    val scores: List[Int] = List(5, 8, 13)
    val embedded: Json = Json.obj(
      "source" -> Json.fromString("already-json"),
      "rank" -> Json.fromInt(7)
    )

    val document: Json = json"""
      {
        "name": $name,
        "count": $count,
        "active": $active,
        "scores": $scores,
        "embedded": $embedded
      }
    """

    val cursor = document.hcursor

    assert(cursor.get[String]("name").contains(name))
    assert(cursor.get[Int]("count").contains(count))
    assert(cursor.get[Boolean]("active").contains(active))
    assert(cursor.downField("scores").focus.contains(Json.arr(
      Json.fromInt(5),
      Json.fromInt(8),
      Json.fromInt(13)
    )))
    assert(cursor.downField("embedded").focus.contains(embedded))
    assert(document.noSpaces.contains("\"already-json\""))

  @Test
  def interpolatesObjectKeysWithKeyEncoderAndRetainsObjectShape(): Unit =
    val primaryKey: String = "answer"
    val secondaryKey: String = "nested-key"
    val value: Int = 42
    val nestedValue: Json = Json.obj("ok" -> Json.fromBoolean(true))

    val document: Json = json"""
      {
        $primaryKey: $value,
        "static": {
          $secondaryKey: $nestedValue
        }
      }
    """

    assert(document.hcursor.get[Int](primaryKey).contains(value))
    assert(document.hcursor.downField("static").downField(secondaryKey).focus.contains(nestedValue))
    assert(document.noSpaces == "{\"answer\":42,\"static\":{\"nested-key\":{\"ok\":true}}}")

  @Test
  def buildsJsonArraysFromMixedLiteralAndInterpolatedElements(): Unit =
    val label: String = "middle"
    val objectElement: Json = Json.obj("position" -> Json.fromString("last"))
    val numericElement: Int = 99

    val document: Json = json"""
      [
        { "position": "first" },
        $label,
        $numericElement,
        $objectElement,
        null
      ]
    """

    assert(document.asArray.exists(_.size == 5))
    assert(document.hcursor.downArray.downField("position").as[String].contains("first"))
    assert(document.hcursor.downArray.right.focus.contains(Json.fromString(label)))
    assert(document.hcursor.downArray.right.right.focus.contains(Json.fromInt(numericElement)))
    assert(document.hcursor.downArray.right.right.right.focus.contains(objectElement))
    assert(document.hcursor.downArray.right.right.right.right.focus.contains(Json.Null))

  @Test
  def usesCustomEncodersForInterpolatedValuesAndObjectKeys(): Unit =
    final case class Feature(name: String, enabled: Boolean)
    final case class FeatureKey(value: String)

    given Encoder[Feature] = Encoder.instance { feature =>
      Json.obj(
        "name" -> Json.fromString(feature.name),
        "enabled" -> Json.fromBoolean(feature.enabled)
      )
    }
    given KeyEncoder[FeatureKey] = KeyEncoder.instance(key => s"feature:${key.value}")

    val key: FeatureKey = FeatureKey("primary")
    val feature: Feature = Feature("custom-encoder", enabled = true)

    val document: Json = json"""
      {
        $key: $feature
      }
    """

    val encodedFieldName: String = "feature:primary"
    val encodedFeature = document.hcursor.downField(encodedFieldName)

    assert(encodedFeature.get[String]("name").contains(feature.name))
    assert(encodedFeature.get[Boolean]("enabled").contains(feature.enabled))
    assert(document.noSpaces == "{\"feature:primary\":{\"name\":\"custom-encoder\",\"enabled\":true}}")
