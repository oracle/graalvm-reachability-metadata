/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_literal_3

import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.Json
import io.circe.KeyEncoder
import io.circe.literal._
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

import java.util.UUID
import scala.jdk.CollectionConverters._

final case class LiteralAccountId(value: UUID)

object LiteralAccountId {
  given KeyEncoder[LiteralAccountId] = KeyEncoder.instance(_.value.toString)
}

final case class LiteralPayload(name: String, score: Int, enabled: Boolean)

object LiteralPayload {
  given Encoder[LiteralPayload] = Encoder.forProduct3("name", "score", "enabled") { payload =>
    (payload.name, payload.score, payload.enabled)
  }

  given Decoder[LiteralPayload] = Decoder.forProduct3("name", "score", "enabled")(LiteralPayload.apply)
}

class Circe_literal_3Test {
  @Test
  def createsNestedJsonDocumentsFromLiteralSyntax(): Unit = {
    val document: Json = json"""
      {
        "service": "circe-literal",
        "enabled": true,
        "missing": null,
        "limits": {
          "min": -12,
          "max": 12345678901234567890.125,
          "scientific": 6.022e23
        },
        "items": [1, false, "line\nquote \" slash /", { "nested": [null, true] }]
      }
      """

    assertThat(document.isObject).isTrue
    assertThat(document.hcursor.get[String]("service")).isEqualTo(Right("circe-literal"))
    assertThat(document.hcursor.get[Boolean]("enabled")).isEqualTo(Right(true))
    assertThat(document.hcursor.downField("missing").focus.exists(_.isNull)).isTrue
    assertThat(document.hcursor.downField("limits").get[Int]("min")).isEqualTo(Right(-12))
    assertThat(document.hcursor.downField("limits").get[BigDecimal]("max"))
      .isEqualTo(Right(BigDecimal("12345678901234567890.125")))
    assertThat(document.hcursor.downField("limits").get[BigDecimal]("scientific"))
      .isEqualTo(Right(BigDecimal("6.022E+23")))
    assertThat(document.hcursor.downField("items").downN(2).as[String])
      .isEqualTo(Right("line\nquote \" slash /"))
    assertThat(document.noSpacesSortKeys).contains("\"nested\":[null,true]")
  }

  @Test
  def createsTopLevelScalarJsonValues(): Unit = {
    val nullJson: Json = json""" null """
    val falseJson: Json = json""" false """
    val numberJson: Json = json""" -42.50 """
    val stringJson: Json = json""" "standalone text" """

    assertThat(nullJson.isNull).isTrue
    assertThat(falseJson.asBoolean).isEqualTo(Some(false))
    assertThat(numberJson.asNumber.flatMap(_.toBigDecimal)).isEqualTo(Some(BigDecimal("-42.50")))
    assertThat(stringJson.asString).isEqualTo(Some("standalone text"))
  }

  @Test
  def interpolatesPrimitiveCollectionOptionAndProductValuesWithEncoders(): Unit = {
    val count: Int = 3
    val ratio: BigDecimal = BigDecimal("12.75")
    val labels: List[String] = List("compile-time", "json", "native")
    val optionalValue: Option[String] = None
    val payload: LiteralPayload = LiteralPayload("worker", 99, enabled = true)

    val document: Json = json"""
      {
        "count": $count,
        "ratio": $ratio,
        "labels": $labels,
        "optional": $optionalValue,
        "payload": $payload
      }
      """

    assertThat(document.hcursor.get[Int]("count")).isEqualTo(Right(3))
    assertThat(document.hcursor.get[BigDecimal]("ratio")).isEqualTo(Right(BigDecimal("12.75")))
    assertThat(expectRight(document.hcursor.get[List[String]]("labels")).asJava)
      .containsExactly("compile-time", "json", "native")
    assertThat(document.hcursor.downField("optional").focus.exists(_.isNull)).isTrue
    assertThat(document.hcursor.get[LiteralPayload]("payload")).isEqualTo(Right(payload))
  }

  @Test
  def interpolatesJsonValuesWithoutQuotingThemAsStrings(): Unit = {
    val nested: Json = Json.obj(
      "numbers" -> Json.arr(Json.fromInt(1), Json.fromInt(2), Json.fromInt(3)),
      "flag" -> Json.True
    )
    val arrayElement: Json = Json.obj("id" -> Json.fromString("embedded"))

    val document: Json = json"""
      {
        "nested": $nested,
        "items": [$arrayElement, { "literal": true }]
      }
      """

    assertThat(document.hcursor.downField("nested").downField("numbers").as[List[Int]])
      .isEqualTo(Right(List(1, 2, 3)))
    assertThat(document.hcursor.downField("nested").get[Boolean]("flag")).isEqualTo(Right(true))
    assertThat(document.hcursor.downField("items").downArray.get[String]("id")).isEqualTo(Right("embedded"))
    assertThat(document.hcursor.downField("items").downN(1).get[Boolean]("literal")).isEqualTo(Right(true))
  }

  @Test
  def interpolatesObjectKeysWithKeyEncoders(): Unit = {
    val first: LiteralAccountId = LiteralAccountId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
    val second: LiteralAccountId = LiteralAccountId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
    val plainKey: String = "plain-key"

    val document: Json = json"""
      {
        $first: { "active": true },
        $second: 17,
        $plainKey: "string-key-value"
      }
      """

    assertThat(document.hcursor.downField(first.value.toString).get[Boolean]("active")).isEqualTo(Right(true))
    assertThat(document.hcursor.get[Int](second.value.toString)).isEqualTo(Right(17))
    assertThat(document.hcursor.get[String](plainKey)).isEqualTo(Right("string-key-value"))
    assertThat(document.asObject.map(_.keys.toList).getOrElse(Nil).asJava)
      .containsExactly(first.value.toString, second.value.toString, plainKey)
  }

  @Test
  def interpolatesMapsWhoseKeysRequireCustomKeyEncoders(): Unit = {
    val first: LiteralAccountId = LiteralAccountId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
    val second: LiteralAccountId = LiteralAccountId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"))
    val balances: Map[LiteralAccountId, BigDecimal] = Map(first -> BigDecimal("10.5"), second -> BigDecimal("20"))

    val document: Json = json"""{ "balances": $balances }"""

    assertThat(document.noSpacesSortKeys)
      .isEqualTo(
        """{"balances":{"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa":10.5,"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb":20}}"""
      )
    assertThat(document.hcursor.downField("balances").get[BigDecimal](first.value.toString))
      .isEqualTo(Right(BigDecimal("10.5")))
    assertThat(document.hcursor.downField("balances").get[BigDecimal](second.value.toString))
      .isEqualTo(Right(BigDecimal("20")))
  }

  @Test
  def keepsLiteralStringsDistinctFromInterpolatedValues(): Unit = {
    val literalUuidText: String = "11111111-1111-1111-1111-111111111111"
    val replacementValue: String = "replacement"
    val firstNumber: Int = 1
    val secondNumber: Int = 2

    val document: Json = json"""
      {
        "literalUuidText": "11111111-1111-1111-1111-111111111111",
        "replacementValue": $replacementValue,
        "numbers": [$firstNumber, "1", $secondNumber, "2"]
      }
      """

    assertThat(document.hcursor.get[String]("literalUuidText")).isEqualTo(Right(literalUuidText))
    assertThat(document.hcursor.get[String]("replacementValue")).isEqualTo(Right("replacement"))
    assertThat(document.hcursor.downField("numbers").downN(0).as[Int]).isEqualTo(Right(1))
    assertThat(document.hcursor.downField("numbers").downN(1).as[String]).isEqualTo(Right("1"))
    assertThat(document.hcursor.downField("numbers").downN(2).as[Int]).isEqualTo(Right(2))
    assertThat(document.hcursor.downField("numbers").downN(3).as[String]).isEqualTo(Right("2"))
  }

  @Test
  def escapesInterpolatedStringsAndObjectKeysAsJsonData(): Unit = {
    val dynamicKey: String = "line\nquote \" backslash \\"
    val dynamicValue: String = "text with \"quotes\", backslash \\, newline\n, and JSON-looking text: true, \"extra\": 1"

    val document: Json = json"""
      {
        $dynamicKey: $dynamicValue,
        "constant": "present"
      }
      """

    assertThat(document.hcursor.get[String](dynamicKey)).isEqualTo(Right(dynamicValue))
    assertThat(document.hcursor.get[String]("constant")).isEqualTo(Right("present"))
    assertThat(document.hcursor.downField("extra").succeeded).isFalse
    assertThat(document.asObject.map(_.keys.toList).getOrElse(Nil).asJava)
      .containsExactly(dynamicKey, "constant")
  }

  @Test
  def producedJsonSupportsStandardCirceCursorAndTransformationApi(): Unit = {
    val source: Json = json"""
      {
        "users": [
          { "name": "Ada", "scores": [1, 2], "deleted": null },
          { "name": "Grace", "scores": [3, 5], "deleted": null }
        ],
        "metadata": { "source": "literal" }
      }
      """

    val cleaned: Json = source.deepDropNullValues.mapObject(_.add("processed", Json.fromBoolean(true)))
    val updated: Json = expectOption(
      cleaned.hcursor
        .downField("users")
        .downN(1)
        .downField("scores")
        .downN(1)
        .withFocus(json => Json.fromInt(expectRight(json.as[Int]) + 10))
        .top
    )

    assertThat(updated.hcursor.downField("users").downN(0).downField("deleted").succeeded).isFalse
    assertThat(updated.hcursor.downField("users").downN(1).downField("scores").downN(1).as[Int])
      .isEqualTo(Right(15))
    assertThat(updated.hcursor.get[Boolean]("processed")).isEqualTo(Right(true))
    assertThat(updated.noSpacesSortKeys)
      .isEqualTo(
        """{"metadata":{"source":"literal"},"processed":true,"users":[{"name":"Ada","scores":[1,2]},{"name":"Grace","scores":[3,15]}]}"""
      )
  }

  private def expectRight[A](result: Either[DecodingFailure, A]): A = {
    result match {
      case Right(value) => value
      case Left(error)  => fail[A](s"Expected successful decoding, but got: $error")
    }
  }

  private def expectOption[A](option: Option[A]): A = {
    option.getOrElse(fail[A]("Expected value to be present"))
  }
}
