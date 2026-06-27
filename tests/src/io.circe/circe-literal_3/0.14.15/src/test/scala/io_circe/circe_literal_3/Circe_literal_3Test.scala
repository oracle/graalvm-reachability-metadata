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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Circe_literal_3Test {
  @Test
  def parsesObjectArrayAndPrimitiveLiterals(): Unit = {
    val document: Json = json"""
      {
        "name": "circe-literal",
        "enabled": true,
        "missing": null,
        "count": 42,
        "ratio": -12.5e2,
        "items": [1, "two", false, { "nested": "value" }],
        "escaped": "line\nquote\"slash\\"
      }
    """

    val cursor = document.hcursor
    assertEquals(Right("circe-literal"), cursor.downField("name").as[String])
    assertEquals(Right(true), cursor.downField("enabled").as[Boolean])
    assertEquals(Some(Json.Null), cursor.downField("missing").focus)
    assertEquals(Right(42), cursor.downField("count").as[Int])
    assertEquals(Some(BigDecimal("-1250")), cursor.downField("ratio").focus.flatMap(_.asNumber).flatMap(_.toBigDecimal))
    assertEquals(Right(1), cursor.downField("items").downArray.as[Int])
    assertEquals(Right("two"), cursor.downField("items").downArray.right.as[String])
    assertEquals(Right(false), cursor.downField("items").downArray.right.right.as[Boolean])
    assertEquals(Right("value"), cursor.downField("items").downArray.right.right.right.downField("nested").as[String])
    assertEquals(Right("line\nquote\"slash\\"), cursor.downField("escaped").as[String])
  }

  @Test
  def parsesTopLevelPrimitiveLiterals(): Unit = {
    val stringValue: Json = json""""top-level string""""
    val numberValue: Json = json"""123.456"""
    val trueValue: Json = json"""true"""
    val falseValue: Json = json"""false"""
    val nullValue: Json = json"""null"""

    assertEquals(Some("top-level string"), stringValue.asString)
    assertEquals(Some(BigDecimal("123.456")), numberValue.asNumber.flatMap(_.toBigDecimal))
    assertTrue(trueValue.asBoolean.contains(true))
    assertTrue(falseValue.asBoolean.contains(false))
    assertEquals(Json.Null, nullValue)
  }

  @Test
  def encodesInterpolatedValuesWithAvailableEncoders(): Unit = {
    final case class Payload(id: String, scores: Vector[Int])

    given Encoder[Payload] with
      override def apply(payload: Payload): Json = Json.obj(
        "id" -> Json.fromString(payload.id),
        "scores" -> Json.arr(payload.scores.map(Json.fromInt)*)
      )

    val ownerField: String = "owner"
    val ownerName: String = "Ada Lovelace"
    val quantity: Int = 3
    val active: Boolean = true
    val tags: List[String] = List("scala", "json", "native")
    val payload: Payload = Payload("payload-1", Vector(5, 8, 13))
    val prebuilt: Json = Json.obj("source" -> Json.fromString("manual"))

    val document: Json = json"""
      {
        $ownerField: $ownerName,
        "quantity": $quantity,
        "active": $active,
        "tags": $tags,
        "payload": $payload,
        "prebuilt": $prebuilt
      }
    """

    val cursor = document.hcursor
    assertEquals(Right("Ada Lovelace"), cursor.downField("owner").as[String])
    assertEquals(Right(3), cursor.downField("quantity").as[Int])
    assertEquals(Right(true), cursor.downField("active").as[Boolean])
    assertEquals(Right(List("scala", "json", "native")), cursor.downField("tags").as[List[String]])
    assertEquals(Right("payload-1"), cursor.downField("payload").downField("id").as[String])
    assertEquals(Right(Vector(5, 8, 13)), cursor.downField("payload").downField("scores").as[Vector[Int]])
    assertEquals(Right("manual"), cursor.downField("prebuilt").downField("source").as[String])
  }

  @Test
  def encodesCustomObjectKeysWithKeyEncoder(): Unit = {
    final case class MetricName(section: String, name: String)

    given KeyEncoder[MetricName] with
      override def apply(metricName: MetricName): String = s"${metricName.section}.${metricName.name}"

    val durationKey: MetricName = MetricName("build", "durationMillis")
    val countKey: MetricName = MetricName("test", "count")
    val durationMillis: Long = 1250L
    val testCount: Int = 7

    val document: Json = json"""
      {
        $durationKey: $durationMillis,
        $countKey: $testCount,
        "static": "kept"
      }
    """

    val cursor = document.hcursor
    assertEquals(Right(1250L), cursor.downField("build.durationMillis").as[Long])
    assertEquals(Right(7), cursor.downField("test.count").as[Int])
    assertEquals(Right("kept"), cursor.downField("static").as[String])
    assertFalse(cursor.downField("MetricName(build,durationMillis)").succeeded)
  }

  @Test
  def embedsInterpolatedJsonValuesInsideArraysAndObjects(): Unit = {
    val first: Json = Json.obj("kind" -> Json.fromString("object"), "index" -> Json.fromInt(0))
    val second: Json = Json.arr(Json.fromString("nested"), Json.fromInt(1), Json.fromBoolean(true))
    val label: String = "literal-element"

    val document: Json = json"""
      [
        $first,
        $second,
        { "label": $label, "literal": ["alpha", 2, null] }
      ]
    """

    val cursor = document.hcursor
    assertEquals(Right("object"), cursor.downArray.downField("kind").as[String])
    assertEquals(Right(0), cursor.downArray.downField("index").as[Int])
    assertEquals(Right("nested"), cursor.downArray.right.downArray.as[String])
    assertEquals(Right(1), cursor.downArray.right.downArray.right.as[Int])
    assertEquals(Right(true), cursor.downArray.right.downArray.right.right.as[Boolean])
    assertEquals(Right("literal-element"), cursor.downArray.right.right.downField("label").as[String])
    assertEquals(Right("alpha"), cursor.downArray.right.right.downField("literal").downArray.as[String])
    assertEquals(Right(2), cursor.downArray.right.right.downField("literal").downArray.right.as[Int])
    assertEquals(Some(Json.Null), cursor.downArray.right.right.downField("literal").downArray.right.right.focus)
  }
}
