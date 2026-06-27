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
import io.circe.JsonNumber
import io.circe.KeyEncoder
import io.circe.literal._
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters._

final case class ServiceName(value: String)

object ServiceName {
  given KeyEncoder[ServiceName] = KeyEncoder.instance(_.value)
}

final case class Release(version: String, build: Int, stable: Boolean)

object Release {
  given Encoder[Release] = Encoder.forProduct3("version", "build", "stable") { release =>
    (release.version, release.build, release.stable)
  }

  given Decoder[Release] = Decoder.forProduct3("version", "build", "stable")(Release.apply)
}

class Circe_literal_3Test {
  @Test
  def buildsNestedDocumentsFromCompileTimeJsonLiterals(): Unit = {
    val document: Json = json"""
      {
        "service": "catalog",
        "enabled": true,
        "replicas": 3,
        "limits": {
          "cpu": 1.5,
          "memoryMiB": 512
        },
        "endpoints": ["/health", "/metrics"],
        "optional": null
      }
      """

    assertThat(document.isObject).isTrue
    assertThat(document.hcursor.get[String]("service")).isEqualTo(Right("catalog"))
    assertThat(document.hcursor.get[Boolean]("enabled")).isEqualTo(Right(true))
    assertThat(document.hcursor.get[Int]("replicas")).isEqualTo(Right(3))
    assertThat(document.hcursor.downField("limits").get[Double]("cpu")).isEqualTo(Right(1.5d))
    assertThat(decodeOrFail(document.hcursor.get[List[String]]("endpoints")).asJava)
      .containsExactly("/health", "/metrics")
    assertThat(document.hcursor.downField("optional").focus).isEqualTo(Some(Json.Null))
  }

  @Test
  def preservesJsonEscapesUnicodeAndNumberPrecisionInLiterals(): Unit = {
    val document: Json = json"""
      {
        "text": "line one\nline two\t☃",
        "escapedQuote": "She said \"hello\"",
        "bigDecimal": 12345678901234567890.125,
        "scientific": -1.25e6,
        "small": 0.000000000000000000123
      }
      """

    val bigDecimal: JsonNumber = decodeOptionOrFail(document.hcursor.downField("bigDecimal").focus.flatMap(_.asNumber))
    val small: JsonNumber = decodeOptionOrFail(document.hcursor.downField("small").focus.flatMap(_.asNumber))

    assertThat(document.hcursor.get[String]("text")).isEqualTo(Right("line one\nline two\t☃"))
    assertThat(document.hcursor.get[String]("escapedQuote")).isEqualTo(Right("She said \"hello\""))
    assertThat(bigDecimal.toBigDecimal).isEqualTo(Some(BigDecimal("12345678901234567890.125")))
    assertThat(document.hcursor.get[BigDecimal]("scientific")).isEqualTo(Right(BigDecimal("-1.25E+6")))
    assertThat(small.toBigDecimal).isEqualTo(Some(BigDecimal("0.000000000000000000123")))
  }

  @Test
  def interpolatesPrimitiveCollectionAndJsonValuesWithEncoders(): Unit = {
    val owner: String = "team-a"
    val revision: Long = 42L
    val enabled: Boolean = true
    val labels: List[String] = List("json", "literal", "macro")
    val nested: Json = json"""{"threshold": 0.75, "mode": "strict"}"""

    val document: Json = json"""
      {
        "owner": $owner,
        "revision": $revision,
        "enabled": $enabled,
        "labels": $labels,
        "settings": $nested
      }
      """

    assertThat(document.hcursor.get[String]("owner")).isEqualTo(Right(owner))
    assertThat(document.hcursor.get[Long]("revision")).isEqualTo(Right(revision))
    assertThat(document.hcursor.get[Boolean]("enabled")).isEqualTo(Right(enabled))
    assertThat(decodeOrFail(document.hcursor.get[List[String]]("labels")).asJava)
      .containsExactly("json", "literal", "macro")
    assertThat(document.hcursor.downField("settings").get[Double]("threshold")).isEqualTo(Right(0.75d))
    assertThat(document.hcursor.downField("settings").get[String]("mode")).isEqualTo(Right("strict"))
  }

  @Test
  def interpolatesCustomEncodedValuesAndDecodesThemBack(): Unit = {
    val release: Release = Release("2026.06", 17, stable = true)
    val releases: List[Release] = List(release, Release("2026.07", 18, stable = false))

    val document: Json = json"""
      {
        "current": $release,
        "history": $releases
      }
      """

    assertThat(document.hcursor.downField("current").as[Release]).isEqualTo(Right(release))
    assertThat(document.hcursor.downField("history").as[List[Release]]).isEqualTo(Right(releases))
    assertThat(document.noSpacesSortKeys).isEqualTo(
      """{"current":{"build":17,"stable":true,"version":"2026.06"},"history":[{"build":17,"stable":true,"version":"2026.06"},{"build":18,"stable":false,"version":"2026.07"}]}"""
    )
  }

  @Test
  def interpolatesObjectKeysWithKeyEncoders(): Unit = {
    val primary: ServiceName = ServiceName("service.alpha")
    val secondary: ServiceName = ServiceName("service/beta")
    val primaryConfig: Json = json"""{"weight": 80, "active": true}"""
    val secondaryConfig: Json = json"""{"weight": 20, "active": false}"""

    val routing: Json = json"""
      {
        $primary: $primaryConfig,
        $secondary: $secondaryConfig,
        "fallback": "service.alpha"
      }
      """

    assertThat(routing.hcursor.downField("service.alpha").get[Int]("weight")).isEqualTo(Right(80))
    assertThat(routing.hcursor.downField("service.alpha").get[Boolean]("active")).isEqualTo(Right(true))
    assertThat(routing.hcursor.downField("service/beta").get[Int]("weight")).isEqualTo(Right(20))
    assertThat(routing.hcursor.get[String]("fallback")).isEqualTo(Right("service.alpha"))
  }

  @Test
  def supportsRootLevelArrayObjectAndPrimitiveLiterals(): Unit = {
    val array: Json = json"""[1, {"two": 2}, false, null]"""
    val string: Json = json""""literal string""""
    val boolean: Json = json"""true"""
    val nul: Json = json"""null"""

    assertThat(decodeOrFail(array.hcursor.downArray.as[Int])).isEqualTo(1)
    assertThat(array.hcursor.downN(1).get[Int]("two")).isEqualTo(Right(2))
    assertThat(array.hcursor.downN(2).as[Boolean]).isEqualTo(Right(false))
    assertThat(string.asString).isEqualTo(Some("literal string"))
    assertThat(boolean.asBoolean).isEqualTo(Some(true))
    assertThat(nul.isNull).isTrue
  }

  private def decodeOrFail[A](result: Either[DecodingFailure, A]): A = {
    result.fold(error => fail(s"Expected decoding success but got: $error"), identity)
  }

  private def decodeOptionOrFail[A](option: Option[A]): A = {
    option.getOrElse(fail("Expected value to be present"))
  }
}
