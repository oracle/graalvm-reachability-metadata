/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_literal_3

import io.circe.Encoder
import io.circe.Json
import io.circe.literal.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Circe_literal_3Test {
  private final case class GeoPoint(latitude: BigDecimal, longitude: BigDecimal)

  private given Encoder[GeoPoint] = Encoder.forProduct2("latitude", "longitude") { point =>
    (point.latitude, point.longitude)
  }

  @Test
  def createsNestedJsonObjectsArraysAndScalarValuesFromLiterals(): Unit = {
    val document: Json = json"""
      {
        "name": "circe literal",
        "active": true,
        "count": 3,
        "ratio": -12.5e2,
        "missing": null,
        "tags": ["json", "scala", "native"],
        "details": {
          "unicode": "snowman \u2603",
          "escaped": "line\nquote \" slash /"
        }
      }
      """
    val cursor = document.hcursor

    assertEquals("circe literal", expectRight(cursor.get[String]("name")))
    assertTrue(expectRight(cursor.get[Boolean]("active")))
    assertEquals(3, expectRight(cursor.get[Int]("count")))
    assertEquals(BigDecimal("-1250"), expectRight(cursor.get[BigDecimal]("ratio")))
    assertTrue(cursor.downField("missing").focus.exists(_.isNull))
    assertEquals(List("json", "scala", "native"), expectRight(cursor.get[List[String]]("tags")))
    assertEquals("snowman ☃", expectRight(cursor.downField("details").get[String]("unicode")))
    assertEquals("line\nquote \" slash /", expectRight(cursor.downField("details").get[String]("escaped")))
  }

  @Test
  def createsTopLevelScalarJsonValues(): Unit = {
    val stringJson: Json = json""" "standalone" """
    val numberJson: Json = json""" 9007199254740993 """
    val trueJson: Json = json""" true """
    val falseJson: Json = json""" false """
    val nullJson: Json = json""" null """

    assertEquals(Some("standalone"), stringJson.asString)
    assertTrue(numberJson.asNumber.exists(_.toBigDecimal.contains(BigDecimal("9007199254740993"))))
    assertEquals(Some(true), trueJson.asBoolean)
    assertEquals(Some(false), falseJson.asBoolean)
    assertTrue(nullJson.isNull)
  }

  @Test
  def interpolatesPrimitiveAndCollectionValuesThroughCirceEncoders(): Unit = {
    val name: String = "Ada"
    val age: Int = 36
    val enabled: Boolean = true
    val languages: List[String] = List("scala", "json")
    val measurements: Vector[BigDecimal] = Vector(BigDecimal("1.25"), BigDecimal("2.50"))

    val document: Json = json"""
      {
        "name": $name,
        "age": $age,
        "enabled": $enabled,
        "languages": $languages,
        "measurements": $measurements
      }
      """
    val cursor = document.hcursor

    assertEquals("Ada", expectRight(cursor.get[String]("name")))
    assertEquals(36, expectRight(cursor.get[Int]("age")))
    assertTrue(expectRight(cursor.get[Boolean]("enabled")))
    assertEquals(List("scala", "json"), expectRight(cursor.get[List[String]]("languages")))
    assertEquals(Vector(BigDecimal("1.25"), BigDecimal("2.50")), expectRight(cursor.get[Vector[BigDecimal]]("measurements")))
  }

  @Test
  def interpolatesDomainValuesWithCustomEncoders(): Unit = {
    val origin: GeoPoint = GeoPoint(BigDecimal("45.0"), BigDecimal("15.5"))
    val destination: GeoPoint = GeoPoint(BigDecimal("46.25"), BigDecimal("16.75"))

    val route: Json = json"""
      {
        "origin": $origin,
        "destination": $destination,
        "waypoints": [$origin, $destination]
      }
      """
    val cursor = route.hcursor

    assertEquals(BigDecimal("45.0"), expectRight(cursor.downField("origin").get[BigDecimal]("latitude")))
    assertEquals(BigDecimal("15.5"), expectRight(cursor.downField("origin").get[BigDecimal]("longitude")))
    assertEquals(BigDecimal("46.25"), expectRight(cursor.downField("destination").get[BigDecimal]("latitude")))
    assertEquals(2, cursor.downField("waypoints").focus.flatMap(_.asArray).map(_.size).getOrElse(0))
  }

  @Test
  def interpolatesPrebuiltJsonValuesInsideLiterals(): Unit = {
    val payload: Json = Json.obj(
      "kind" -> Json.fromString("prebuilt"),
      "items" -> Json.arr(Json.fromInt(1), Json.fromInt(2), Json.fromInt(3))
    )

    val document: Json = json"""
      {
        "payload": $payload,
        "copies": [$payload, $payload]
      }
      """
    val cursor = document.hcursor

    assertEquals("prebuilt", expectRight(cursor.downField("payload").get[String]("kind")))
    assertEquals(List(1, 2, 3), expectRight(cursor.downField("payload").get[List[Int]]("items")))
    assertEquals(2, cursor.downField("copies").focus.flatMap(_.asArray).map(_.size).getOrElse(0))
    assertEquals(document.hcursor.downField("payload").focus, cursor.downField("copies").downArray.focus)
  }

  @Test
  def interpolatesObjectKeysWithKeyEncoders(): Unit = {
    val dynamicKey: String = "selected-field"
    val nestedKey: String = "nested-field"

    val document: Json = json"""
      {
        ${dynamicKey}: "value",
        "object": {
          ${nestedKey}: 42
        },
        "fixed": true
      }
      """
    val cursor = document.hcursor

    assertEquals("value", expectRight(cursor.get[String](dynamicKey)))
    assertEquals(42, expectRight(cursor.downField("object").get[Int](nestedKey)))
    assertTrue(expectRight(cursor.get[Boolean]("fixed")))
    assertFalse(cursor.downField("dynamicKey").succeeded)
  }

  @Test
  def encodesInterpolatedStringValuesWithJsonEscaping(): Unit = {
    val fieldName: String = "message"
    val text: String = "line one\nline two with \"quotes\" and snowman ☃"

    val document: Json = json"""
      {
        ${fieldName}: $text,
        "array": [$text]
      }
      """
    val cursor = document.hcursor

    assertEquals(text, expectRight(cursor.get[String](fieldName)))
    assertEquals(List(text), expectRight(cursor.get[List[String]]("array")))
    assertTrue(document.noSpaces.contains("\\n"))
    assertTrue(document.noSpaces.contains("\\\"quotes\\\""))
  }

  private def expectRight[A](result: Either[?, A]): A = result match {
    case Right(value) => value
    case Left(error)  => throw new AssertionError(s"Expected successful Circe result, but got: $error")
  }
}
