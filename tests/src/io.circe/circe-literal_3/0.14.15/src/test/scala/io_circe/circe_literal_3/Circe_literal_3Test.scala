/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_literal_3

import io.circe.Decoder
import io.circe.Encoder
import io.circe.Error
import io.circe.HCursor
import io.circe.Json
import io.circe.KeyEncoder
import io.circe.literal._
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

final case class LiteralWidget(id: String, quantity: Int, tags: List[String])

object LiteralWidget {
  given Encoder[LiteralWidget] = Encoder.forProduct3("id", "quantity", "tags") { widget =>
    (widget.id, widget.quantity, widget.tags)
  }

  given Decoder[LiteralWidget] = Decoder.forProduct3("id", "quantity", "tags")(LiteralWidget.apply)
}

final case class LiteralMetricName(value: String)

object LiteralMetricName {
  given KeyEncoder[LiteralMetricName] = KeyEncoder.instance(metric => s"metric:${metric.value}")
}

class Circe_literal_3Test {
  @Test
  def buildsStaticJsonDocumentsWithAllJsonKinds(): Unit = {
    val document: Json = json"""
      {
        "message": "hello\nworld",
        "unicode": "\u03bb",
        "integer": 42,
        "negative": -7,
        "decimal": 12.50,
        "exponent": 6.02e23,
        "enabled": true,
        "missing": null,
        "array": [1, "two", false, null],
        "object": { "first": 1, "second": 2 }
      }
    """

    assertThat(document.isObject).isTrue
    assertThat(document.hcursor.get[String]("message")).isEqualTo(Right("hello\nworld"))
    assertThat(document.hcursor.get[String]("unicode")).isEqualTo(Right("λ"))
    assertThat(document.hcursor.get[Int]("integer")).isEqualTo(Right(42))
    assertThat(document.hcursor.get[Int]("negative")).isEqualTo(Right(-7))
    assertThat(decodeOrFail(document.hcursor.get[BigDecimal]("decimal")).bigDecimal)
      .isEqualByComparingTo("12.50")
    assertThat(document.hcursor.get[BigDecimal]("exponent").map(_.bigDecimal.toPlainString))
      .isEqualTo(Right("602000000000000000000000"))
    assertThat(document.hcursor.get[Boolean]("enabled")).isEqualTo(Right(true))
    assertThat(document.hcursor.downField("missing").focus).isEqualTo(Some(Json.Null))
    assertThat(document.hcursor.downField("array").downN(1).as[String]).isEqualTo(Right("two"))
    assertThat(document.hcursor.downField("array").downN(2).as[Boolean]).isEqualTo(Right(false))
    assertThat(document.hcursor.downField("object").get[Int]("second")).isEqualTo(Right(2))
  }

  @Test
  def createsRootLevelJsonLiterals(): Unit = {
    val array: Json = json"""[1, 2, 3]"""
    val string: Json = json""" "root string" """
    val number: Json = json"""12345678901234567890"""
    val trueValue: Json = json"""true"""
    val falseValue: Json = json"""false"""
    val nullValue: Json = json"""null"""

    assertThat(array.as[List[Int]]).isEqualTo(Right(List(1, 2, 3)))
    assertThat(string.asString).isEqualTo(Some("root string"))
    assertThat(number.as[BigInt]).isEqualTo(Right(BigInt("12345678901234567890")))
    assertThat(trueValue).isEqualTo(Json.True)
    assertThat(falseValue).isEqualTo(Json.False)
    assertThat(nullValue).isEqualTo(Json.Null)
  }

  @Test
  def interpolatesPrimitiveAndCollectionValuesThroughCirceEncoders(): Unit = {
    val name: String = "Ada Lovelace"
    val age: Int = 36
    val active: Boolean = true
    val score: BigDecimal = BigDecimal("99.75")
    val middleName: Option[String] = None
    val favoriteNumbers: List[Int] = List(2, 3, 5)

    val document: Json = json"""
      {
        "name": $name,
        "age": $age,
        "active": $active,
        "score": $score,
        "middleName": $middleName,
        "favoriteNumbers": $favoriteNumbers
      }
    """

    val cursor: HCursor = document.hcursor
    assertThat(cursor.get[String]("name")).isEqualTo(Right(name))
    assertThat(cursor.get[Int]("age")).isEqualTo(Right(age))
    assertThat(cursor.get[Boolean]("active")).isEqualTo(Right(active))
    assertThat(decodeOrFail(cursor.get[BigDecimal]("score")).bigDecimal).isEqualByComparingTo("99.75")
    assertThat(cursor.get[Option[String]]("middleName")).isEqualTo(Right(None))
    assertThat(cursor.get[List[Int]]("favoriteNumbers")).isEqualTo(Right(favoriteNumbers))
  }

  @Test
  def escapesInterpolatedStringsAndKeysRatherThanTreatingThemAsJsonSyntax(): Unit = {
    val payload: String = """{"admin":true,"name":"quoted"}"""
    val quotedText: String = "line 1\nline \"2\" \\ slash"
    val dynamicKey: String = "user \"input\"\nkey"

    val document: Json = json"""
      {
        "payload": $payload,
        "quotedText": $quotedText,
        $dynamicKey: "dynamic value"
      }
    """

    val cursor: HCursor = document.hcursor
    assertThat(cursor.get[String]("payload")).isEqualTo(Right(payload))
    assertThat(cursor.downField("payload").focus.exists(_.isString)).isTrue
    assertThat(cursor.get[String]("quotedText")).isEqualTo(Right(quotedText))
    assertThat(cursor.get[String](dynamicKey)).isEqualTo(Right("dynamic value"))
    assertThat(document.asObject.map(_.keys.toSet.contains(dynamicKey))).isEqualTo(Some(true))
  }

  @Test
  def interpolatesJsonFragmentsWithoutStringQuotingThem(): Unit = {
    val payload: Json = json"""{ "kind": "embedded", "count": 2 }"""
    val tags: List[String] = List("compile-time", "json")

    val document: Json = json"""
      {
        "payload": $payload,
        "tags": $tags,
        "literal": "kept as a normal string"
      }
    """

    assertThat(document.hcursor.downField("payload").get[String]("kind")).isEqualTo(Right("embedded"))
    assertThat(document.hcursor.downField("payload").get[Int]("count")).isEqualTo(Right(2))
    assertThat(document.hcursor.get[List[String]]("tags")).isEqualTo(Right(tags))
    assertThat(document.hcursor.get[String]("literal")).isEqualTo(Right("kept as a normal string"))
  }

  @Test
  def usesCustomEncodersAndKeyEncodersForInterpolatedTerms(): Unit = {
    val widget: LiteralWidget = LiteralWidget("widget-1", 7, List("blue", "small"))
    val plainKey: String = "owner"
    val metricKey: LiteralMetricName = LiteralMetricName("latency.ms")

    val document: Json = json"""
      {
        "widget": $widget,
        $plainKey: "test-suite",
        $metricKey: 125
      }
    """

    assertThat(document.hcursor.get[LiteralWidget]("widget")).isEqualTo(Right(widget))
    assertThat(document.hcursor.get[String]("owner")).isEqualTo(Right("test-suite"))
    assertThat(document.hcursor.get[Int]("metric:latency.ms")).isEqualTo(Right(125))
    assertThat(document.asObject.map(_.keys.toSet)).isEqualTo(Some(Set("widget", "owner", "metric:latency.ms")))
  }

  @Test
  def supportsSingleValueInterpolationAndNormalCirceNavigation(): Unit = {
    val widget: LiteralWidget = LiteralWidget("root-widget", 11, List("root", "interpolated"))
    val root: Json = json"""$widget"""
    val wrapped: Json = json"""
      {
        "items": [$root, { "id": "literal-widget", "quantity": 1, "tags": [] }]
      }
    """

    assertThat(root.as[LiteralWidget]).isEqualTo(Right(widget))
    assertThat(wrapped.hcursor.downField("items").downN(0).as[LiteralWidget]).isEqualTo(Right(widget))
    assertThat(wrapped.hcursor.downField("items").downN(1).get[String]("id")).isEqualTo(Right("literal-widget"))
    assertThat(wrapped.noSpacesSortKeys)
      .isEqualTo(
        """{"items":[{"id":"root-widget","quantity":11,"tags":["root","interpolated"]},{"id":"literal-widget","quantity":1,"tags":[]}]}"""
      )
  }

  @Test
  def acceptsInlineExpressionsInValueAndKeyInterpolationPositions(): Unit = {
    val baseKey: Int = 20
    val label: String = "inline"

    val document: Json = json"""
      {
        ${baseKey + 2}: ${List(1, 2, 3).map(_ * 10)},
        ${"computed-" + label}: ${LiteralWidget(s"${label}-widget", 5 + 2, List(label, "expression"))}
      }
    """

    assertThat(document.hcursor.get[List[Int]]("22")).isEqualTo(Right(List(10, 20, 30)))
    assertThat(document.hcursor.get[LiteralWidget]("computed-inline"))
      .isEqualTo(Right(LiteralWidget("inline-widget", 7, List("inline", "expression"))))
    assertThat(document.asObject.map(_.keys.toSet)).isEqualTo(Some(Set("22", "computed-inline")))
  }

  private def decodeOrFail[A](result: Either[Error, A]): A = {
    result.fold(error => fail(s"Expected decoding success but got: $error"), identity)
  }
}
