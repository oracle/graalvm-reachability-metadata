/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_literal_3

import io.circe.Encoder
import io.circe.Json
import io.circe.JsonNumber
import io.circe.KeyEncoder
import io.circe.literal._
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters._

class Circe_literal_3Test {
  private final case class ServiceConfig(name: String, retries: Int)

  private given Encoder[ServiceConfig] = Encoder.forProduct2("name", "retries") { config =>
    (config.name, config.retries)
  }

  private final case class EnvironmentKey(value: String)

  private given KeyEncoder[EnvironmentKey] = KeyEncoder.instance(key => s"env:${key.value}")

  @Test
  def materializesNestedObjectLiteralsWithEveryJsonValueKind(): Unit = {
    val document: Json = json"""
      {
        "service": "literal-api",
        "enabled": true,
        "disabled": false,
        "missing": null,
        "ports": [8080, 8443, 9000],
        "limits": {
          "requests": 12345678901234567890,
          "ratio": 0.125
        }
      }
    """

    assertThat(document.isObject).isTrue
    assertThat(document.hcursor.get[String]("service")).isEqualTo(Right("literal-api"))
    assertThat(document.hcursor.get[Boolean]("enabled")).isEqualTo(Right(true))
    assertThat(document.hcursor.get[Boolean]("disabled")).isEqualTo(Right(false))
    assertThat(document.hcursor.downField("missing").focus.exists(_.isNull)).isTrue
    assertThat(expectRight(document.hcursor.get[List[Int]]("ports")).asJava).containsExactly(8080, 8443, 9000)
    assertThat(document.hcursor.downField("limits").get[BigInt]("requests"))
      .isEqualTo(Right(BigInt("12345678901234567890")))
    assertThat(document.hcursor.downField("limits").get[BigDecimal]("ratio"))
      .isEqualTo(Right(BigDecimal("0.125")))
  }

  @Test
  def materializesTopLevelScalarAndArrayLiterals(): Unit = {
    val stringJson: Json = json""""line\nbreak snowman \u2603 quote \" slash /""""
    val numberJson: Json = json"""-1.25e2"""
    val trueJson: Json = json"""true"""
    val nullJson: Json = json"""null"""
    val arrayJson: Json = json"""["alpha", {"nested": [1, 2, 3]}, false]"""

    assertThat(stringJson.asString).isEqualTo(Some("line\nbreak snowman ☃ quote \" slash /"))
    assertThat(numberJson.asNumber.flatMap(_.toBigDecimal)).isEqualTo(Some(BigDecimal("-125")))
    assertThat(trueJson.asBoolean).isEqualTo(Some(true))
    assertThat(nullJson.isNull).isTrue
    assertThat(arrayJson.hcursor.downN(1).downField("nested").as[List[Int]]).isEqualTo(Right(List(1, 2, 3)))
  }

  @Test
  def interpolatesValuesUsingInScopeCirceEncoders(): Unit = {
    val config: ServiceConfig = ServiceConfig("payments", 3)
    val enabled: Boolean = true
    val threshold: BigDecimal = BigDecimal("99.95")
    val tags: Vector[String] = Vector("critical", "customer-facing")
    val port: Option[Int] = Some(8443)
    val disabledReason: Option[String] = None

    val document: Json = json"""
      {
        "config": $config,
        "enabled": $enabled,
        "threshold": $threshold,
        "tags": $tags,
        "port": $port,
        "disabledReason": $disabledReason
      }
    """

    assertThat(document.hcursor.downField("config").get[String]("name")).isEqualTo(Right("payments"))
    assertThat(document.hcursor.downField("config").get[Int]("retries")).isEqualTo(Right(3))
    assertThat(document.hcursor.get[Boolean]("enabled")).isEqualTo(Right(true))
    assertThat(document.hcursor.get[BigDecimal]("threshold")).isEqualTo(Right(BigDecimal("99.95")))
    assertThat(expectRight(document.hcursor.get[Vector[String]]("tags")).asJava)
      .containsExactly("critical", "customer-facing")
    assertThat(document.hcursor.get[Option[Int]]("port")).isEqualTo(Right(Some(8443)))
    assertThat(document.hcursor.get[Option[String]]("disabledReason")).isEqualTo(Right(None))
  }

  @Test
  def interpolatesJsonValuesWithoutStringifyingThem(): Unit = {
    val firstItem: Json = Json.obj(
      "id" -> Json.fromInt(1),
      "state" -> Json.fromString("ready")
    )
    val metadata: Json = Json.obj(
      "source" -> Json.fromString("generated"),
      "attempt" -> Json.fromInt(2)
    )

    val document: Json = json"""
      {
        "items": [$firstItem, {"id": 2, "state": "queued"}],
        "metadata": $metadata
      }
    """

    assertThat(document.hcursor.downField("items").downN(0).get[Int]("id")).isEqualTo(Right(1))
    assertThat(document.hcursor.downField("items").downN(1).get[String]("state")).isEqualTo(Right("queued"))
    assertThat(document.hcursor.downField("metadata").get[String]("source")).isEqualTo(Right("generated"))
    assertThat(document.noSpaces).doesNotContain("\\\"id\\\"")
  }

  @Test
  def interpolatesObjectKeysUsingInScopeKeyEncoders(): Unit = {
    val primaryKey: EnvironmentKey = EnvironmentKey("primary")
    val secondaryKey: EnvironmentKey = EnvironmentKey("secondary")
    val primaryValue: String = "blue"
    val secondaryValue: Int = 2

    val document: Json = json"""
      {
        $primaryKey: $primaryValue,
        $secondaryKey: $secondaryValue
      }
    """

    val fields = document.asObject.getOrElse(fail("Expected a JSON object")).toMap
    assertThat(fields.keySet.asJava).containsExactlyInAnyOrder("env:primary", "env:secondary")
    assertThat(document.hcursor.get[String]("env:primary")).isEqualTo(Right("blue"))
    assertThat(document.hcursor.get[Int]("env:secondary")).isEqualTo(Right(2))
  }

  @Test
  def interpolatesEncodedValuesAsTheEntireJsonDocument(): Unit = {
    val config: ServiceConfig = ServiceConfig("scheduler", 5)
    val environmentWeights: Map[EnvironmentKey, Int] = Map(
      EnvironmentKey("production") -> 10,
      EnvironmentKey("staging") -> 2
    )

    val configDocument: Json = json"""$config"""
    val environmentDocument: Json = json"""$environmentWeights"""

    assertThat(configDocument.isObject).isTrue
    assertThat(configDocument.hcursor.get[String]("name")).isEqualTo(Right("scheduler"))
    assertThat(configDocument.hcursor.get[Int]("retries")).isEqualTo(Right(5))

    val fields = environmentDocument.asObject.getOrElse(fail("Expected a JSON object")).toMap
    assertThat(fields.keySet.asJava).containsExactlyInAnyOrder("env:production", "env:staging")
    assertThat(environmentDocument.hcursor.get[Int]("env:production")).isEqualTo(Right(10))
    assertThat(environmentDocument.hcursor.get[Int]("env:staging")).isEqualTo(Right(2))
  }

  @Test
  def preservesPreciseNumbersCreatedByTheLiteralFacade(): Unit = {
    val document: Json = json"""
      {
        "bigDecimal": 12345678901234567890.123456789,
        "negativeExponent": -6.022e23,
        "smallExponent": 1e-9
      }
    """

    val bigDecimal: JsonNumber = expectOption(document.hcursor.downField("bigDecimal").focus.flatMap(_.asNumber))
    val negativeExponent: JsonNumber = expectOption(document.hcursor.downField("negativeExponent").focus.flatMap(_.asNumber))
    val smallExponent: JsonNumber = expectOption(document.hcursor.downField("smallExponent").focus.flatMap(_.asNumber))

    assertThat(bigDecimal.toBigDecimal).isEqualTo(Some(BigDecimal("12345678901234567890.123456789")))
    assertThat(negativeExponent.toBigDecimal).isEqualTo(Some(BigDecimal("-6.022E+23")))
    assertThat(smallExponent.toBigDecimal).isEqualTo(Some(BigDecimal("1E-9")))
  }

  @Test
  def returnsNormalCirceJsonThatCanBeTransformedWithPublicApis(): Unit = {
    val document: Json = json"""
      {
        "users": [
          {"name": "Ada", "score": 10, "drop": null},
          {"name": "Grace", "score": 20, "drop": null}
        ],
        "meta": {"source": "literal"}
      }
    """

    val cleaned: Json = document.deepDropNullValues.mapObject { fields =>
      fields.add("verified", Json.True)
    }
    val updated: Json = expectOption(
      cleaned.hcursor
        .downField("users")
        .downN(1)
        .downField("score")
        .withFocus(_ => Json.fromInt(25))
        .top
    )

    assertThat(updated.hcursor.downField("users").downN(0).downField("drop").succeeded).isFalse
    assertThat(updated.hcursor.downField("users").downN(1).get[Int]("score")).isEqualTo(Right(25))
    assertThat(updated.hcursor.get[Boolean]("verified")).isEqualTo(Right(true))
    assertThat(updated.noSpacesSortKeys)
      .isEqualTo(
        """{"meta":{"source":"literal"},"users":[{"name":"Ada","score":10},{"name":"Grace","score":25}],"verified":true}"""
      )
  }

  private def expectRight[A](result: Either[?, A]): A = {
    result match {
      case Right(value) => value
      case Left(error) => fail[A](s"Expected successful result, but got: $error")
    }
  }

  private def expectOption[A](option: Option[A]): A = {
    option.getOrElse(fail("Expected value to be present"))
  }
}
