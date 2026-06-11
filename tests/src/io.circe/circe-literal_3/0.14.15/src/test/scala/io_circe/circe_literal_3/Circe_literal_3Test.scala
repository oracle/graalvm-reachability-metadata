/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_literal_3

import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.KeyEncoder
import io.circe.literal.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Circe_literal_3Test {
  @Test
  def parsesNestedJsonLiteralAtCompileTime(): Unit = {
    val document: Json = json"""{
      "name": "circe",
      "active": true,
      "missing": null,
      "numbers": [1, -2, 3.5, 6.02e23],
      "nested": {
        "message": "hello",
        "items": ["alpha", "beta"]
      }
    }"""

    assertThat(decodeField[String](document, "name")).isEqualTo("circe")
    assertThat(decodeField[Boolean](document, "active")).isTrue()
    assertThat(document.hcursor.downField("missing").focus.exists(_.isNull)).isTrue()
    val numberCount: Option[Int] = document.hcursor.downField("numbers").focus.flatMap(_.asArray).map(_.size)
    assertThat(numberCount).isEqualTo(Some(4))
    assertThat(document.hcursor.downField("nested").get[String]("message")).isEqualTo(Right("hello"))
    assertThat(document.hcursor.downField("nested").get[List[String]]("items")).isEqualTo(Right(List("alpha", "beta")))
  }

  @Test
  def preservesEscapesAndPrimitiveRootValues(): Unit = {
    val escaped: Json = json"""{
      "quote": "He said \"hello\"",
      "path": "C:\\tmp\\file.json",
      "line": "first\nsecond",
      "unicode": "\u2603"
    }"""

    assertThat(decodeField[String](escaped, "quote")).isEqualTo("He said \"hello\"")
    assertThat(decodeField[String](escaped, "path")).isEqualTo("C:\\tmp\\file.json")
    assertThat(decodeField[String](escaped, "line")).isEqualTo("first\nsecond")
    assertThat(decodeField[String](escaped, "unicode")).isEqualTo("☃")
    assertThat(json"""true""").isEqualTo(Json.True)
    assertThat(json"""null""").isEqualTo(Json.Null)
    assertThat(json""""root string"""").isEqualTo(Json.fromString("root string"))
  }

  @Test
  def preservesExactJsonNumberValues(): Unit = {
    val document: Json = json"""{
      "large": 9007199254740993,
      "fraction": 1234567890.12345678901234567890,
      "exponent": 1.234567890123456789e5
    }"""

    assertThat(document.hcursor.downField("large").focus.flatMap(_.asNumber).flatMap(_.toBigInt)).isEqualTo(
      Some(BigInt("9007199254740993"))
    )
    assertThat(document.hcursor.downField("fraction").focus.flatMap(_.asNumber).flatMap(_.toBigDecimal)).isEqualTo(
      Some(BigDecimal("1234567890.12345678901234567890"))
    )
    assertThat(document.hcursor.downField("exponent").focus.flatMap(_.asNumber).flatMap(_.toBigDecimal)).isEqualTo(
      Some(BigDecimal("123456.7890123456789"))
    )
  }

  @Test
  def interpolatesEncodedValuesInObjectsArraysAndRootPositions(): Unit = {
    val name: String = "Ada"
    val count: Int = 3
    val enabled: Boolean = true
    val tags: List[String] = List("scala", "json")
    val payload: Json = Json.obj(
      "source" -> Json.fromString("prebuilt"),
      "score" -> Json.fromInt(99)
    )

    val document: Json = json"""{
      "name": $name,
      "count": $count,
      "enabled": $enabled,
      "tags": $tags,
      "payload": $payload,
      "array": [$name, $count, $enabled, $payload]
    }"""

    assertThat(decodeField[String](document, "name")).isEqualTo(name)
    assertThat(decodeField[Int](document, "count")).isEqualTo(count)
    assertThat(decodeField[Boolean](document, "enabled")).isEqualTo(enabled)
    assertThat(decodeField[List[String]](document, "tags")).isEqualTo(tags)
    assertThat(document.hcursor.downField("payload").focus).isEqualTo(Some(payload))
    val interpolatedArraySize: Option[Int] = document.hcursor.downField("array").focus.flatMap(_.asArray).map(_.size)
    assertThat(interpolatedArraySize).isEqualTo(Some(4))
    assertThat(json"""$payload""").isEqualTo(payload)
  }

  @Test
  def interpolatesKeysWithCirceKeyEncoders(): Unit = {
    val plainKey: String = "plain"
    val numericKey: Int = 42

    val document: Json = json"""{
      $plainKey: "value",
      $numericKey: true
    }"""

    assertThat(decodeField[String](document, "plain")).isEqualTo("value")
    assertThat(decodeField[Boolean](document, "42")).isTrue()
    assertThat(document.noSpacesSortKeys).isEqualTo("{\"42\":true,\"plain\":\"value\"}")
  }

  @Test
  def interpolatesComputedExpressionsInValueAndKeyPositions(): Unit = {
    val suffix: String = "requests"
    val base: Int = 40

    val document: Json = json"""{
      ${"metric-" + suffix}: ${base + 2},
      "values": [${base * 2}, ${List(base, base + 1)}],
      "rootValue": ${Option.empty[String]}
    }"""

    assertThat(decodeField[Int](document, "metric-requests")).isEqualTo(42)
    assertThat(decodeField[List[Json]](document, "values")).isEqualTo(
      List(Json.fromInt(80), Json.arr(Json.fromInt(40), Json.fromInt(41)))
    )
    assertThat(document.hcursor.downField("rootValue").focus).isEqualTo(Some(Json.Null))
  }

  @Test
  def usesCustomValueAndKeyEncodersDuringInterpolation(): Unit = {
    final case class Temperature(celsius: Int)

    given Encoder[Temperature] = Encoder.instance { value =>
      Json.obj(
        "celsius" -> Json.fromInt(value.celsius),
        "fahrenheit" -> Json.fromInt(value.celsius * 9 / 5 + 32)
      )
    }
    given KeyEncoder[Temperature] = KeyEncoder.instance { value =>
      s"temperature-${value.celsius}"
    }

    val key: Temperature = Temperature(21)
    val value: Temperature = Temperature(100)
    val document: Json = json"""{
      $key: $value,
      "readings": [$value]
    }"""

    val encodedValue: Json = Json.obj(
      "celsius" -> Json.fromInt(100),
      "fahrenheit" -> Json.fromInt(212)
    )

    assertThat(document.hcursor.downField("temperature-21").focus).isEqualTo(Some(encodedValue))
    val firstReading: Option[Json] = document.hcursor.downField("readings").focus.flatMap(_.asArray).map(_.head)
    assertThat(firstReading).isEqualTo(Some(encodedValue))
  }

  private def decodeField[A: Decoder](json: Json, fieldName: String): A = {
    json.hcursor.get[A](fieldName) match {
      case Right(value) => value
      case Left(error)  => throw new AssertionError(error.message)
    }
  }
}
