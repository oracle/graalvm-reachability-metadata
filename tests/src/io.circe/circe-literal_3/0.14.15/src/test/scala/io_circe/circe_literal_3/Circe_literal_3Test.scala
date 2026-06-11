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
  def jsonInterpolatorBuildsNestedJsonWithPrimitiveValues(): Unit =
    val actual: Json = json"""{
      "message": "hello",
      "count": 3,
      "price": 12.50,
      "active": true,
      "archived": false,
      "nothing": null,
      "items": ["first", 2, true, null, { "nested": "value" }]
    }"""

    val expected: Json = Json.obj(
      "message" -> Json.fromString("hello"),
      "count" -> Json.fromInt(3),
      "price" -> Json.fromBigDecimal(BigDecimal("12.50")),
      "active" -> Json.True,
      "archived" -> Json.False,
      "nothing" -> Json.Null,
      "items" -> Json.arr(
        Json.fromString("first"),
        Json.fromInt(2),
        Json.True,
        Json.Null,
        Json.obj("nested" -> Json.fromString("value"))
      )
    )

    assert(actual == expected)

  @Test
  def jsonInterpolatorDecodesEscapedStringLiterals(): Unit =
    val actual: Json = json"""{
      "quote": "She said \"hi\"",
      "unicode": "\u2603",
      "newline": "first\nsecond"
    }"""

    val expected: Json = Json.obj(
      "quote" -> Json.fromString("She said \"hi\""),
      "unicode" -> Json.fromString("☃"),
      "newline" -> Json.fromString("first\nsecond")
    )

    assert(actual == expected)

  @Test
  def jsonInterpolatorSupportsSingleRootValues(): Unit =
    val stringValue: Json = json""" "plain text" """
    val numberValue: Json = json""" -42 """
    val trueValue: Json = json""" true """
    val falseValue: Json = json""" false """
    val nullValue: Json = json""" null """

    assert(stringValue == Json.fromString("plain text"))
    assert(numberValue == Json.fromInt(-42))
    assert(trueValue == Json.True)
    assert(falseValue == Json.False)
    assert(nullValue == Json.Null)

  @Test
  def jsonInterpolatorEncodesInterpolatedValuesAndKeys(): Unit =
    val featureKey: FeatureKey = FeatureKey("search")
    val enabled: Boolean = true
    val widget: Widget = Widget("console", 7)
    val labels: List[String] = List("native", "scala")
    val suppliedJson: Json = Json.obj("source" -> Json.fromString("prebuilt"))

    val actual: Json = json"""{
      $featureKey: $enabled,
      "widget": $widget,
      "labels": $labels,
      "directJson": $suppliedJson,
      "array": [$widget, $enabled, $labels]
    }"""

    val encodedWidget: Json = Widget.encoder(widget)
    val expected: Json = Json.obj(
      "feature-search" -> Json.True,
      "widget" -> encodedWidget,
      "labels" -> Json.arr(Json.fromString("native"), Json.fromString("scala")),
      "directJson" -> suppliedJson,
      "array" -> Json.arr(
        encodedWidget,
        Json.True,
        Json.arr(Json.fromString("native"), Json.fromString("scala"))
      )
    )

    assert(actual == expected)

  @Test
  def jsonInterpolatorEncodesAnInterpolatedRootValue(): Unit =
    val widget: Widget = Widget("dashboard", 11)
    val actual: Json = json""" $widget """

    assert(actual == Widget.encoder(widget))

  private final case class FeatureKey(value: String)

  private object FeatureKey:
    given KeyEncoder[FeatureKey] = KeyEncoder.instance { (featureKey: FeatureKey) =>
      s"feature-${featureKey.value}"
    }

  private final case class Widget(name: String, priority: Int)

  private object Widget:
    given encoder: Encoder[Widget] = Encoder.instance { (widget: Widget) =>
      Json.obj(
        "name" -> Json.fromString(widget.name),
        "priority" -> Json.fromInt(widget.priority)
      )
    }
