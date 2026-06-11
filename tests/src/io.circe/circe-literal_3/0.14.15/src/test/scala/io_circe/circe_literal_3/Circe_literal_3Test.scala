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
  private final case class Metric(name: String, value: Int)
  private final case class FieldName(value: String)

  private given Encoder[Metric] = Encoder.instance { metric =>
    Json.obj(
      "name" -> Json.fromString(metric.name),
      "value" -> Json.fromInt(metric.value)
    )
  }

  private given KeyEncoder[FieldName] = KeyEncoder.instance { fieldName =>
    s"metric:${fieldName.value}"
  }

  @Test
  def buildsNestedJsonLiteralWithEveryJsonValueKind(): Unit = {
    val document: Json = json"""
      {
        "message": "hello",
        "enabled": true,
        "nothing": null,
        "count": 42,
        "decimal": 12345678901234567890.12345,
        "scientific": -6.02e2,
        "items": [1, "two", false, null, { "nested": "value" }]
      }
      """

    val cursor = document.hcursor

    assertEquals("hello", expectRight(cursor.get[String]("message")))
    assertTrue(expectRight(cursor.get[Boolean]("enabled")))
    assertTrue(cursor.downField("nothing").focus.exists(_.isNull))
    assertEquals(42, expectRight(cursor.get[Int]("count")))
    assertEquals(BigDecimal("12345678901234567890.12345"), expectRight(cursor.get[BigDecimal]("decimal")))
    assertEquals(BigDecimal("-602"), expectRight(cursor.get[BigDecimal]("scientific")))
    assertEquals(1, expectRight(cursor.downField("items").downN(0).as[Int]))
    assertEquals("two", expectRight(cursor.downField("items").downN(1).as[String]))
    assertFalse(expectRight(cursor.downField("items").downN(2).as[Boolean]))
    assertTrue(cursor.downField("items").downN(3).focus.exists(_.isNull))
    assertEquals("value", expectRight(cursor.downField("items").downN(4).get[String]("nested")))
  }

  @Test
  def decodesEscapedStringContentAndPreciseNumbersFromLiteral(): Unit = {
    val document: Json = json"""
      {
        "text": "line\nsnowman \u2603 quote \" slash /",
        "bigInteger": 123456789012345678901234567890,
        "smallDecimal": 0.00000000012345
      }
      """

    val cursor = document.hcursor

    assertEquals("line\nsnowman ☃ quote \" slash /", expectRight(cursor.get[String]("text")))
    assertEquals(BigDecimal("123456789012345678901234567890"), expectRight(cursor.get[BigDecimal]("bigInteger")))
    assertEquals(BigDecimal("0.00000000012345"), expectRight(cursor.get[BigDecimal]("smallDecimal")))
  }

  @Test
  def interpolatesValuesThroughCirceEncoders(): Unit = {
    val name: String = "Ada"
    val score: Int = 99
    val ratio: BigDecimal = BigDecimal("12.50")
    val active: Boolean = true
    val profile: Json = Json.obj("language" -> Json.fromString("Scala"))

    val document: Json = json"""
      {
        "name": $name,
        "score": $score,
        "ratio": $ratio,
        "active": $active,
        "profile": $profile
      }
      """

    val cursor = document.hcursor

    assertEquals("Ada", expectRight(cursor.get[String]("name")))
    assertEquals(99, expectRight(cursor.get[Int]("score")))
    assertEquals(BigDecimal("12.50"), expectRight(cursor.get[BigDecimal]("ratio")))
    assertTrue(expectRight(cursor.get[Boolean]("active")))
    assertEquals("Scala", expectRight(cursor.downField("profile").get[String]("language")))
  }

  @Test
  def interpolatesDomainValuesWithCustomEncoders(): Unit = {
    val metric: Metric = Metric("latency", 37)

    val document: Json = json"""
      {
        "metric": $metric,
        "metrics": [$metric]
      }
      """

    val metricCursor = document.hcursor.downField("metric")
    val arrayMetricCursor = document.hcursor.downField("metrics").downN(0)

    assertEquals("latency", expectRight(metricCursor.get[String]("name")))
    assertEquals(37, expectRight(metricCursor.get[Int]("value")))
    assertEquals("latency", expectRight(arrayMetricCursor.get[String]("name")))
    assertEquals(37, expectRight(arrayMetricCursor.get[Int]("value")))
  }

  @Test
  def interpolatesObjectKeysThroughCirceKeyEncoders(): Unit = {
    val primary: FieldName = FieldName("p50")
    val secondary: FieldName = FieldName("p99")

    val document: Json = json"""
      {
        $primary: 12,
        $secondary: { "unit": "milliseconds" }
      }
      """

    val cursor = document.hcursor

    assertEquals(12, expectRight(cursor.get[Int]("metric:p50")))
    assertEquals("milliseconds", expectRight(cursor.downField("metric:p99").get[String]("unit")))
  }

  @Test
  def buildsTopLevelScalarAndArrayLiterals(): Unit = {
    val nullJson: Json = json"null"
    val trueJson: Json = json"true"
    val stringJson: Json = json""" "standalone" """
    val numberJson: Json = json"42.125"
    val arrayJson: Json = json"""[1, 2, 3]"""

    assertTrue(nullJson.isNull)
    assertEquals(Some(true), trueJson.asBoolean)
    assertEquals(Some("standalone"), stringJson.asString)
    assertTrue(numberJson.asNumber.exists(_.toBigDecimal.contains(BigDecimal("42.125"))))
    assertEquals(List(1, 2, 3), expectRight(arrayJson.as[List[Int]]))
  }

  @Test
  def buildsTopLevelJsonFromInterpolatedCollections(): Unit = {
    val numbers: List[Int] = List(2, 4, 6)
    val labels: Map[String, String] = Map("tier" -> "gold", "region" -> "eu")

    val arrayJson: Json = json"$numbers"
    val objectJson: Json = json"$labels"

    assertEquals(List(2, 4, 6), expectRight(arrayJson.as[List[Int]]))
    assertEquals("gold", expectRight(objectJson.hcursor.get[String]("tier")))
    assertEquals("eu", expectRight(objectJson.hcursor.get[String]("region")))
  }

  @Test
  def interpolatesInlineScalaExpressionsInKeysAndValues(): Unit = {
    val document: Json = json"""
      {
        ${"stat" + "us"}: ${List("o", "k").mkString},
        "answer": ${40 + 2},
        "derived": ${List(1, 2, 3).map(_ * 2)}
      }
      """

    val cursor = document.hcursor

    assertEquals("ok", expectRight(cursor.get[String]("status")))
    assertEquals(42, expectRight(cursor.get[Int]("answer")))
    assertEquals(List(2, 4, 6), expectRight(cursor.get[List[Int]]("derived")))
  }

  @Test
  def composesLiteralJsonWithCirceTransformations(): Unit = {
    val source: Json = json"""
      {
        "items": [
          { "id": 1, "enabled": true },
          { "id": 2, "enabled": false },
          { "id": 3, "enabled": true }
        ],
        "meta": { "source": "literal" }
      }
      """

    val enabledIds: List[Int] = expectRight(source.hcursor.downField("items").as[List[Json]]).flatMap { item =>
      val cursor = item.hcursor
      val enabled = expectRight(cursor.get[Boolean]("enabled"))
      if enabled then Some(expectRight(cursor.get[Int]("id"))) else None
    }
    val printed: String = source.noSpaces

    assertEquals(List(1, 3), enabledIds)
    assertTrue(printed.contains("\"source\":\"literal\""))
    assertTrue(printed.startsWith("{"))
  }

  private def expectRight[A](result: Either[?, A]): A = {
    result match {
      case Right(value) => value
      case Left(error) => fail[A](s"Expected successful result, but got: $error")
    }
  }
}
