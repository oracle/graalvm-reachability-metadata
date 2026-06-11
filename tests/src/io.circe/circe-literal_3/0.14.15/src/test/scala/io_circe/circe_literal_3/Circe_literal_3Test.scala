/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_literal_3

import io.circe.Decoder
import io.circe.Encoder
import io.circe.HCursor
import io.circe.Json
import io.circe.KeyEncoder
import io.circe.literal._
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class Circe_literal_3Test {
  private final case class FeatureFlag(name: String, enabled: Boolean, rolloutPercent: Int)

  private given Encoder[FeatureFlag] = Encoder.forProduct3("name", "enabled", "rolloutPercent") { flag =>
    (flag.name, flag.enabled, flag.rolloutPercent)
  }

  private given Decoder[FeatureFlag] = Decoder.forProduct3("name", "enabled", "rolloutPercent")(FeatureFlag.apply)

  private final case class DashboardKey(value: String)

  @Test
  def createsNestedJsonLiteralContainingEveryJsonValueKind(): Unit = {
    val document: Json = json"""
      {
        "name": "circe literal",
        "enabled": true,
        "disabled": false,
        "missing": null,
        "numbers": [0, -12, 42.125, 6.02e3],
        "escaped": "line\nbullet \u2022 quote \" slash /",
        "nested": {
          "items": ["alpha", "beta"],
          "emptyObject": {},
          "emptyArray": []
        }
      }
      """
    val cursor: HCursor = document.hcursor

    assertEquals(Right("circe literal"), cursor.get[String]("name"))
    assertEquals(Right(true), cursor.get[Boolean]("enabled"))
    assertEquals(Right(false), cursor.get[Boolean]("disabled"))
    assertTrue(cursor.downField("missing").focus.exists(_.isNull))
    assertEquals(
      Right(List(BigDecimal("0"), BigDecimal("-12"), BigDecimal("42.125"), BigDecimal("6020"))),
      cursor.get[List[BigDecimal]]("numbers")
    )
    assertEquals(Right("line\nbullet • quote \" slash /"), cursor.get[String]("escaped"))
    assertEquals(Right(List("alpha", "beta")), cursor.downField("nested").get[List[String]]("items"))
    assertTrue(cursor.downField("nested").downField("emptyObject").focus.exists(_.isObject))
    assertTrue(cursor.downField("nested").downField("emptyArray").focus.exists(_.isArray))
  }

  @Test
  def createsTopLevelScalarLiterals(): Unit = {
    val nullJson: Json = json""" null """
    val booleanJson: Json = json""" true """
    val stringJson: Json = json""" "standalone" """
    val numberJson: Json = json""" 12345678901234567890.125 """

    assertTrue(nullJson.isNull)
    assertEquals(Some(true), booleanJson.asBoolean)
    assertEquals(Some("standalone"), stringJson.asString)
    assertEquals(Some(BigDecimal("12345678901234567890.125")), numberJson.asNumber.flatMap(_.toBigDecimal))
  }

  @Test
  def interpolatesTopLevelValuesThroughCirceEncoders(): Unit = {
    val flag: FeatureFlag = FeatureFlag("instant-rollout", enabled = true, rolloutPercent = 100)
    val tags: List[String] = List("stable", "public")
    val absentLabel: Option[String] = None

    val flagJson: Json = json""" $flag """
    val tagsJson: Json = json""" $tags """
    val absentLabelJson: Json = json""" $absentLabel """

    assertEquals(Right(flag), flagJson.as[FeatureFlag])
    assertEquals(Right(tags), tagsJson.as[List[String]])
    assertTrue(absentLabelJson.isNull)
  }

  @Test
  def interpolatesPrimitiveCollectionAndOptionalValuesWithCirceEncoders(): Unit = {
    val name: String = "Ada Lovelace"
    val age: Int = 36
    val aliases: List[String] = List("analyst", "programmer")
    val scores: Vector[BigDecimal] = Vector(BigDecimal("98.5"), BigDecimal("100"))
    val primaryEmail: Option[String] = Some("ada@example.test")
    val secondaryEmail: Option[String] = None

    val document: Json = json"""
      {
        "name": $name,
        "age": $age,
        "aliases": $aliases,
        "scores": $scores,
        "primaryEmail": $primaryEmail,
        "secondaryEmail": $secondaryEmail
      }
      """
    val cursor: HCursor = document.hcursor

    assertEquals(Right(name), cursor.get[String]("name"))
    assertEquals(Right(age), cursor.get[Int]("age"))
    assertEquals(Right(aliases), cursor.get[List[String]]("aliases"))
    assertEquals(Right(scores), cursor.get[Vector[BigDecimal]]("scores"))
    assertEquals(Right(primaryEmail), cursor.get[Option[String]]("primaryEmail"))
    assertEquals(Right(None), cursor.get[Option[String]]("secondaryEmail"))
    assertTrue(cursor.downField("secondaryEmail").focus.exists(_.isNull))
  }

  @Test
  def interpolatesJsonFragmentsInsideObjectsAndArrays(): Unit = {
    val metadata: Json = Json.obj(
      "owner" -> Json.fromString("testing"),
      "priority" -> Json.fromInt(7)
    )
    val firstItem: Json = Json.obj("id" -> Json.fromInt(1), "active" -> Json.True)
    val secondItem: Json = Json.obj("id" -> Json.fromInt(2), "active" -> Json.False)

    val document: Json = json"""
      {
        "metadata": $metadata,
        "items": [$firstItem, $secondItem],
        "wrapper": { "payload": $firstItem }
      }
      """

    assertEquals(Right("testing"), document.hcursor.downField("metadata").get[String]("owner"))
    assertEquals(Right(7), document.hcursor.downField("metadata").get[Int]("priority"))
    val itemIds: Either[?, List[Int]] = document.hcursor
      .downField("items")
      .as[List[Json]]
      .map(_.map(item => decodeOrFail(item.hcursor.get[Int]("id"))))

    assertEquals(Right(List(1, 2)), itemIds)
    assertEquals(Right(true), document.hcursor.downField("wrapper").downField("payload").get[Boolean]("active"))
  }

  @Test
  def interpolatesObjectKeysWithStringAndCustomKeyEncoders(): Unit = {
    given KeyEncoder[DashboardKey] = KeyEncoder.instance(_.value)

    val simpleKey: String = "plain"
    val metricKey: DashboardKey = DashboardKey("requests.total")
    val nestedKey: DashboardKey = DashboardKey("latency.p95")

    val document: Json = json"""
      {
        $simpleKey: "value",
        $metricKey: 42,
        "nested": {
          $nestedKey: 125.5
        }
      }
      """

    assertEquals(Right("value"), document.hcursor.get[String]("plain"))
    assertEquals(Right(42), document.hcursor.get[Int]("requests.total"))
    assertEquals(
      Right(BigDecimal("125.5")),
      document.hcursor.downField("nested").get[BigDecimal]("latency.p95")
    )
    assertFalse(document.hcursor.downField("metricKey").succeeded)
  }

  @Test
  def interpolatesInlineExpressionsInValueAndKeyPositions(): Unit = {
    val document: Json = json"""
      {
        ${"computed" + "Key"}: ${List(1, 2, 3).map(_ * 2)},
        ${40 + 2}: ${Option("available")},
        "summary": ${FeatureFlag("inline-expression", enabled = true, rolloutPercent = 50)}
      }
      """

    assertEquals(Right(List(2, 4, 6)), document.hcursor.get[List[Int]]("computedKey"))
    assertEquals(Right(Some("available")), document.hcursor.get[Option[String]]("42"))
    assertEquals(
      Right(FeatureFlag("inline-expression", enabled = true, rolloutPercent = 50)),
      document.hcursor.get[FeatureFlag]("summary")
    )
  }

  @Test
  def interpolatesDomainObjectsThroughUserProvidedEncoders(): Unit = {
    val enabledFlag: FeatureFlag = FeatureFlag("new-search", enabled = true, rolloutPercent = 25)
    val disabledFlag: FeatureFlag = FeatureFlag("legacy-checkout", enabled = false, rolloutPercent = 0)

    val document: Json = json"""
      {
        "flags": [$enabledFlag, $disabledFlag],
        "defaultFlag": $disabledFlag
      }
      """

    assertEquals(
      Right(List(enabledFlag, disabledFlag)),
      document.hcursor.downField("flags").as[List[FeatureFlag]]
    )
    assertEquals(Right(disabledFlag), document.hcursor.get[FeatureFlag]("defaultFlag"))
    assertEquals(
      """{"defaultFlag":{"enabled":false,"name":"legacy-checkout","rolloutPercent":0},"flags":[{"enabled":true,"name":"new-search","rolloutPercent":25},{"enabled":false,"name":"legacy-checkout","rolloutPercent":0}]}""",
      document.noSpacesSortKeys
    )
  }

  @Test
  def preservesLiteralFieldOrderWhenPrintingCompactJson(): Unit = {
    val second: String = "second"
    val third: Json = Json.obj("nested" -> Json.fromBoolean(true))

    val document: Json = json"""
      {
        "first": 1,
        "second": $second,
        "third": $third,
        "fourth": [4]
      }
      """

    assertEquals("""{"first":1,"second":"second","third":{"nested":true},"fourth":[4]}""", document.noSpaces)
  }

  private def decodeOrFail[A](result: Either[?, A]): A = {
    result match {
      case Right(value) => value
      case Left(error) => fail[A](s"Expected successful decoding, but got: $error")
    }
  }
}
