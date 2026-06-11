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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class Circe_literal_3Test {
  private final case class Widget(id: Int, label: String, enabled: Boolean)
  private final case class SectionName(value: String)

  private given Encoder[Widget] = Encoder.instance { widget =>
    Json.obj(
      "id" -> Json.fromInt(widget.id),
      "label" -> Json.fromString(widget.label),
      "enabled" -> Json.fromBoolean(widget.enabled)
    )
  }

  private given KeyEncoder[SectionName] = KeyEncoder.instance(_.value)

  @Test
  def createsNestedJsonObjectsArraysAndScalarsFromLiteralSyntax(): Unit = {
    val document: Json = json"""
      {
        "name": "literal",
        "active": true,
        "missing": null,
        "numbers": [1, -2, 3.50, 6.022e23],
        "nested": {
          "tags": ["json", "scala", "circe"],
          "emptyObject": {},
          "emptyArray": []
        }
      }
      """

    val cursor = document.hcursor

    assertEquals("literal", expectRight(cursor.get[String]("name")))
    assertTrue(expectRight(cursor.get[Boolean]("active")))
    assertTrue(cursor.downField("missing").focus.exists(_.isNull))
    assertEquals(
      List(BigDecimal(1), BigDecimal(-2), BigDecimal("3.50"), BigDecimal("6.022E+23")),
      expectRight(cursor.get[List[BigDecimal]]("numbers"))
    )
    assertEquals(
      List("json", "scala", "circe"),
      expectRight(cursor.downField("nested").get[List[String]]("tags"))
    )
    assertTrue(cursor.downField("nested").downField("emptyObject").focus.exists(_.isObject))
    assertTrue(cursor.downField("nested").downField("emptyArray").focus.exists(_.isArray))
  }

  @Test
  def preservesEscapedStringAndUnicodeContents(): Unit = {
    val document: Json = json"""
      {
        "text": "line one\nline two\tquoted \"value\"",
        "unicode": "snowman \u2603 and lambda \u03bb",
        "slashes": "a/b\\c"
      }
      """
    val cursor = document.hcursor

    assertEquals("line one\nline two\tquoted \"value\"", expectRight(cursor.get[String]("text")))
    assertEquals("snowman ☃ and lambda λ", expectRight(cursor.get[String]("unicode")))
    assertEquals("a/b\\c", expectRight(cursor.get[String]("slashes")))
  }

  @Test
  def buildsTopLevelScalarValues(): Unit = {
    val stringJson: Json = json""""standalone""""
    val numberJson: Json = json"""-12345.6789"""
    val trueJson: Json = json"""true"""
    val falseJson: Json = json"""false"""
    val nullJson: Json = json"""null"""

    assertEquals(Some("standalone"), stringJson.asString)
    assertTrue(numberJson.asNumber.exists(_.toBigDecimal.contains(BigDecimal("-12345.6789"))))
    assertEquals(Some(true), trueJson.asBoolean)
    assertEquals(Some(false), falseJson.asBoolean)
    assertTrue(nullJson.isNull)
  }

  @Test
  def interpolatesPrimitiveValuesWithStandardEncoders(): Unit = {
    val name: String = "Ada"
    val count: Int = 3
    val score: BigDecimal = BigDecimal("98.75")
    val visible: Boolean = true
    val optionalNickname: Option[String] = None
    val aliases: List[String] = List("compiler", "analytical engine")

    val document: Json = json"""
      {
        "name": $name,
        "count": $count,
        "score": $score,
        "visible": $visible,
        "nickname": $optionalNickname,
        "aliases": $aliases
      }
      """
    val cursor = document.hcursor

    assertEquals("Ada", expectRight(cursor.get[String]("name")))
    assertEquals(3, expectRight(cursor.get[Int]("count")))
    assertEquals(BigDecimal("98.75"), expectRight(cursor.get[BigDecimal]("score")))
    assertTrue(expectRight(cursor.get[Boolean]("visible")))
    assertTrue(cursor.downField("nickname").focus.exists(_.isNull))
    assertEquals(aliases, expectRight(cursor.get[List[String]]("aliases")))
  }

  @Test
  def interpolatesJsonValuesInsideObjectsAndArrays(): Unit = {
    val metadata: Json = Json.obj("source" -> Json.fromString("test"), "retries" -> Json.fromInt(2))
    val first: Json = Json.obj("id" -> Json.fromInt(1))
    val second: Json = Json.obj("id" -> Json.fromInt(2))

    val document: Json = json"""
      {
        "metadata": $metadata,
        "items": [$first, $second],
        "status": "ok"
      }
      """

    assertEquals("test", expectRight(document.hcursor.downField("metadata").get[String]("source")))
    assertEquals(2, expectRight(document.hcursor.downField("metadata").get[Int]("retries")))
    assertEquals(
      List(1, 2),
      expectRight(document.hcursor.get[List[Json]]("items"))
        .map(item => expectRight(item.hcursor.get[Int]("id")))
    )
    assertEquals("ok", expectRight(document.hcursor.get[String]("status")))
  }

  @Test
  def interpolatesCustomEncodedValuesAndDynamicObjectKeys(): Unit = {
    val sectionName: SectionName = SectionName("primary")
    val widget: Widget = Widget(id = 7, label = "native-image", enabled = true)

    val document: Json = json"""
      {
        $sectionName: $widget,
        "static": "kept"
      }
      """
    val widgetCursor = document.hcursor.downField("primary")

    assertEquals(7, expectRight(widgetCursor.get[Int]("id")))
    assertEquals("native-image", expectRight(widgetCursor.get[String]("label")))
    assertTrue(expectRight(widgetCursor.get[Boolean]("enabled")))
    assertEquals("kept", expectRight(document.hcursor.get[String]("static")))
  }

  @Test
  def supportsInterpolatedCollectionsAndMaps(): Unit = {
    val counts: Map[String, Int] = Map("red" -> 2, "blue" -> 4)
    val matrix: Vector[Vector[Int]] = Vector(Vector(1, 2), Vector(3, 4))
    val flags: List[Option[Boolean]] = List(Some(true), None, Some(false))

    val document: Json = json"""
      {
        "counts": $counts,
        "matrix": $matrix,
        "flags": $flags
      }
      """
    val cursor = document.hcursor

    assertEquals(counts, expectRight(cursor.get[Map[String, Int]]("counts")))
    assertEquals(matrix.map(_.toList).toList, expectRight(cursor.get[List[List[Int]]]("matrix")))
    assertEquals(flags, expectRight(cursor.get[List[Option[Boolean]]]("flags")))
  }

  @Test
  def supportsInlineExpressionsInInterpolatedKeysAndValues(): Unit = {
    val first: Int = 21
    val second: Int = 2

    val document: Json = json"""
      {
        ${first * second}: ${List(1, 2, 3).map(_ * second)},
        "computed": ${("circe" + "-literal").toUpperCase},
        "enabled": ${first < second}
      }
      """
    val cursor = document.hcursor

    assertEquals(List(2, 4, 6), expectRight(cursor.get[List[Int]]("42")))
    assertEquals("CIRCE-LITERAL", expectRight(cursor.get[String]("computed")))
    assertFalse(expectRight(cursor.get[Boolean]("enabled")))
  }

  @Test
  def supportsTopLevelInterpolatedEncodedValues(): Unit = {
    val widgets: Vector[Widget] = Vector(
      Widget(id = 1, label = "first", enabled = true),
      Widget(id = 2, label = "second", enabled = false)
    )
    val maybeLabel: Option[String] = None

    val arrayDocument: Json = json"""$widgets"""
    val nullDocument: Json = json"""$maybeLabel"""
    val decodedWidgets: List[Json] = expectRight(arrayDocument.as[List[Json]])

    assertTrue(arrayDocument.isArray)
    assertEquals(List(1, 2), decodedWidgets.map(widget => expectRight(widget.hcursor.get[Int]("id"))))
    assertEquals(List("first", "second"), decodedWidgets.map(widget => expectRight(widget.hcursor.get[String]("label"))))
    assertEquals(List(true, false), decodedWidgets.map(widget => expectRight(widget.hcursor.get[Boolean]("enabled"))))
    assertTrue(nullDocument.isNull)
  }

  @Test
  def producedJsonValuesRemainUsableThroughCirceCursorAndPrintingApis(): Unit = {
    val base: Json = json"""{"items":[{"id":1},{"id":2}],"ok":true}"""
    val ids: List[Int] = expectRight(base.hcursor.downField("items").as[List[Json]])
      .map(item => expectRight(item.hcursor.get[Int]("id")))
    val compact: String = base.noSpaces

    assertEquals(List(1, 2), ids)
    assertTrue(compact.contains("\"items\""))
    assertTrue(compact.contains("\"ok\":true"))
    assertFalse(compact.contains(" "))
  }

  private def expectRight[A](result: Either[?, A]): A = {
    result match {
      case Right(value) => value
      case Left(error) => fail[A](s"Expected successful result, but got: $error")
    }
  }
}
