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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.compiletime.testing.typeCheckErrors
import scala.jdk.CollectionConverters.*

class Circe_literal_3Test {
  @Test
  def createsNestedLiteralDocumentsWithoutRuntimeParsing(): Unit = {
    val document: Json = json"""
      {
        "project": "circe-literal",
        "enabled": true,
        "disabled": false,
        "empty": null,
        "counts": [0, 1, -2, 3.5, 6.022e23],
        "nested": {
          "escaped": "line\nbreak and snowman ☃",
          "array": [{ "id": 1 }, { "id": 2 }]
        }
      }
    """

    assertThat(document.isObject).isTrue
    assertThat(document.hcursor.get[String]("project")).isEqualTo(Right("circe-literal"))
    assertThat(document.hcursor.get[Boolean]("enabled")).isEqualTo(Right(true))
    assertThat(document.hcursor.get[Boolean]("disabled")).isEqualTo(Right(false))
    assertThat(document.hcursor.downField("empty").focus.exists(_.isNull)).isTrue
    assertThat(document.hcursor.downField("counts").downN(3).as[BigDecimal]).isEqualTo(Right(BigDecimal("3.5")))
    assertThat(document.hcursor.downField("counts").downN(4).as[BigDecimal]).isEqualTo(Right(BigDecimal("6.022E+23")))
    assertThat(document.hcursor.downField("nested").get[String]("escaped"))
      .isEqualTo(Right("line\nbreak and snowman ☃"))
    assertThat(document.hcursor.downField("nested").downField("array").downN(1).get[Int]("id"))
      .isEqualTo(Right(2))
  }

  @Test
  def createsTopLevelScalarLiterals(): Unit = {
    val nullJson: Json = json"null"
    val trueJson: Json = json"true"
    val falseJson: Json = json"false"
    val stringJson: Json = json""""standalone""""
    val integerJson: Json = json"42"
    val decimalJson: Json = json"-12.125"
    val arrayJson: Json = json"""[true, null, "text"]"""

    assertThat(nullJson).isEqualTo(Json.Null)
    assertThat(trueJson).isEqualTo(Json.True)
    assertThat(falseJson).isEqualTo(Json.False)
    assertThat(stringJson.asString).isEqualTo(Some("standalone"))
    assertThat(integerJson.asNumber.flatMap(_.toInt)).isEqualTo(Some(42))
    assertThat(decimalJson.asNumber.flatMap(_.toBigDecimal)).isEqualTo(Some(BigDecimal("-12.125")))
    assertThat(arrayJson.asArray.map(_.size)).isEqualTo(Some(3))
  }

  @Test
  def interpolatesEncodedValuesIntoObjectsArraysAndScalars(): Unit = {
    val identifier: Long = 9007199254740993L
    val labels: List[String] = List("native", "json", "literal")
    val attributes: Map[String, List[Int]] = Map("even" -> List(2, 4), "odd" -> List(1, 3))
    val optionalNote: Option[String] = None
    val embedded: Json = Json.obj("source" -> Json.fromString("prebuilt"), "ok" -> Json.True)

    val document: Json = json"""
      {
        "id": $identifier,
        "labels": $labels,
        "attributes": $attributes,
        "note": $optionalNote,
        "copies": [$embedded, $embedded],
        "literalValue": ${1 + 2}
      }
    """

    assertThat(document.hcursor.get[Long]("id")).isEqualTo(Right(identifier))
    assertThat(document.hcursor.get[List[String]]("labels")).isEqualTo(Right(labels))
    assertThat(document.hcursor.downField("attributes").get[List[Int]]("even")).isEqualTo(Right(List(2, 4)))
    assertThat(document.hcursor.downField("note").focus.exists(_.isNull)).isTrue
    assertThat(document.hcursor.downField("copies").downN(0).get[String]("source")).isEqualTo(Right("prebuilt"))
    assertThat(document.hcursor.get[Int]("literalValue")).isEqualTo(Right(3))
  }

  @Test
  def interpolatesTopLevelValuesWithAvailableEncoders(): Unit = {
    val text: String = "quoted \"value\" and snowman ☃"
    val number: BigDecimal = BigDecimal("12345678901234567890.125")
    val flags: Vector[Boolean] = Vector(true, false, true)

    val textJson: Json = json"$text"
    val numberJson: Json = json"$number"
    val flagsJson: Json = json"$flags"

    assertThat(textJson).isEqualTo(Json.fromString(text))
    assertThat(numberJson.asNumber.flatMap(_.toBigDecimal)).isEqualTo(Some(number))
    assertThat(flagsJson.as[Vector[Boolean]]).isEqualTo(Right(flags))
  }

  @Test
  def interpolatesStringAndNonStringObjectKeys(): Unit = {
    val escapedKey: String = "key with spaces \"and quotes\""
    val numericKey: Int = 2026
    val secondNumericKey: Int = 99
    val value: Json = Json.obj("status" -> Json.fromString("ok"))

    val document: Json = json"""
      {
        $escapedKey: $value,
        $numericKey: "year",
        $secondNumericKey: "number",
        ${"literal-key"}: 7
      }
    """

    val fields: Map[String, Json] = document.asObject.toList.flatMap(_.toMap).toMap
    assertThat(fields.keySet.asJava).containsExactlyInAnyOrder(
      "key with spaces \"and quotes\"",
      "2026",
      "99",
      "literal-key"
    )
    assertThat(document.hcursor.downField(escapedKey).get[String]("status")).isEqualTo(Right("ok"))
    assertThat(document.hcursor.get[String](numericKey.toString)).isEqualTo(Right("year"))
    assertThat(document.hcursor.get[String](secondNumericKey.toString)).isEqualTo(Right("number"))
    assertThat(document.hcursor.get[Int]("literal-key")).isEqualTo(Right(7))
  }

  @Test
  def honorsCustomEncodersForInterpolatedValuesAndKeys(): Unit = {
    final case class ServiceId(value: String)
    final case class ServiceStatus(healthy: Boolean, checks: List[String])

    given Encoder[ServiceId] = Encoder.encodeString.contramap(_.value)
    given KeyEncoder[ServiceId] = KeyEncoder.instance(_.value)
    given Encoder[ServiceStatus] = Encoder.instance { status =>
      Json.obj(
        "healthy" -> Json.fromBoolean(status.healthy),
        "checks" -> Json.fromValues(status.checks.map(Json.fromString))
      )
    }

    val serviceId: ServiceId = ServiceId("service/api")
    val status: ServiceStatus = ServiceStatus(healthy = true, checks = List("database", "cache"))

    val document: Json = json"""
      {
        $serviceId: $status,
        "mirrored": $serviceId
      }
    """

    assertThat(document.hcursor.downField("service/api").get[Boolean]("healthy")).isEqualTo(Right(true))
    assertThat(document.hcursor.downField("service/api").get[List[String]]("checks"))
      .isEqualTo(Right(List("database", "cache")))
    assertThat(document.hcursor.get[String]("mirrored")).isEqualTo(Right("service/api"))
  }

  @Test
  def reportsCompileTimeErrorsForInvalidJsonAndMissingEncoders(): Unit = {
    val invalidJsonErrors = typeCheckErrors("""
      import io.circe.literal.*
      json"{ invalid json }"
    """)
    val missingValueEncoderErrors = typeCheckErrors("""
      import io.circe.literal.*
      final case class NotEncoded(value: String)
      val notEncoded = NotEncoded("value")
      json"$notEncoded"
    """)
    val missingKeyEncoderErrors = typeCheckErrors("""
      import io.circe.literal.*
      final case class NotEncoded(value: String)
      val notEncoded = NotEncoded("value")
      json"{ $notEncoded: true }"
    """)

    assertThat(invalidJsonErrors.asJava).isNotEmpty
    assertThat(missingValueEncoderErrors.asJava).isNotEmpty
    assertThat(missingKeyEncoderErrors.asJava).isNotEmpty
  }
}
