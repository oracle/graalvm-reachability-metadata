/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_literal_3

import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.Json
import io.circe.JsonNumber
import io.circe.KeyEncoder
import io.circe.literal._
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class Circe_literal_3Test {
  private final case class LiteralAccount(id: String, quota: Int, labels: List[String])

  private given Encoder[LiteralAccount] = Encoder.forProduct3("id", "quota", "labels") { account =>
    (account.id, account.quota, account.labels)
  }

  private final case class LiteralRegion(code: String)

  private given KeyEncoder[LiteralRegion] = KeyEncoder.instance(_.code)

  @Test
  def createsNestedObjectLiteralWithAllJsonValueKinds(): Unit = {
    val document: Json = json"""
      {
        "name": "Ada Lovelace",
        "active": true,
        "missing": null,
        "roles": ["analyst", "programmer"],
        "profile": {
          "age": 36,
          "score": 99.5,
          "negativeExponent": -1.25e2
        }
      }
      """

    val cursor = document.hcursor

    assertTrue(document.isObject)
    assertEquals("Ada Lovelace", expectRight(cursor.get[String]("name")))
    assertTrue(expectRight(cursor.get[Boolean]("active")))
    assertTrue(cursor.downField("missing").focus.exists(_.isNull))
    assertEquals(List("analyst", "programmer"), expectRight(cursor.get[List[String]]("roles")))
    assertEquals(36, expectRight(cursor.downField("profile").get[Int]("age")))
    assertEquals(BigDecimal("99.5"), expectRight(cursor.downField("profile").get[BigDecimal]("score")))
    assertEquals(BigDecimal("-125"), expectRight(cursor.downField("profile").get[BigDecimal]("negativeExponent")))
  }

  @Test
  def createsArrayLiteralWithEscapedTextAndPreciseNumbers(): Unit = {
    val values: Json = json"""
      [
        "line\nbreak snowman \u2603 quote \" slash /",
        12345678901234567890.125,
        -0.00000000000000000000042,
        true,
        false,
        null,
        { "nested": [1, 2, 3] }
      ]
      """

    val decodedValues: List[Json] = expectRight(values.as[List[Json]])
    val largeNumber: JsonNumber = decodedValues(1).asNumber.getOrElse(fail("Expected precise JSON number"))
    val smallNumber: JsonNumber = decodedValues(2).asNumber.getOrElse(fail("Expected precise JSON number"))

    assertEquals("line\nbreak snowman ☃ quote \" slash /", decodedValues.head.asString.getOrElse(fail("Expected string")))
    assertEquals(Some(BigDecimal("12345678901234567890.125")), largeNumber.toBigDecimal)
    assertEquals(Some(BigDecimal("-0.00000000000000000000042")), smallNumber.toBigDecimal)
    assertEquals(Some(true), decodedValues(3).asBoolean)
    assertEquals(Some(false), decodedValues(4).asBoolean)
    assertTrue(decodedValues(5).isNull)
    assertEquals(Right(List(1, 2, 3)), decodedValues(6).hcursor.get[List[Int]]("nested"))
  }

  @Test
  def createsTopLevelScalarLiterals(): Unit = {
    val text: Json = json""""standalone""""
    val number: Json = json"""42.125"""
    val boolean: Json = json"""true"""
    val empty: Json = json"""null"""

    assertEquals(Some("standalone"), text.asString)
    assertEquals(Some(BigDecimal("42.125")), number.asNumber.flatMap(_.toBigDecimal))
    assertEquals(Some(true), boolean.asBoolean)
    assertTrue(empty.isNull)
  }

  @Test
  def interpolatesValuesThroughCirceEncoders(): Unit = {
    val account: LiteralAccount = LiteralAccount("acct-1", 25, List("primary", "paid"))
    val enabled: Boolean = true
    val retries: Int = 3
    val ratio: BigDecimal = BigDecimal("0.875")
    val tags: List[String] = List("scala", "json")
    val optionalName: Option[String] = Some("literal")
    val missingName: Option[String] = None

    val document: Json = json"""
      {
        "account": $account,
        "enabled": $enabled,
        "retries": $retries,
        "ratio": $ratio,
        "tags": $tags,
        "optionalName": $optionalName,
        "missingName": $missingName
      }
      """

    val cursor = document.hcursor

    assertEquals(Right("acct-1"), cursor.downField("account").get[String]("id"))
    assertEquals(Right(25), cursor.downField("account").get[Int]("quota"))
    assertEquals(Right(List("primary", "paid")), cursor.downField("account").get[List[String]]("labels"))
    assertEquals(Right(true), cursor.get[Boolean]("enabled"))
    assertEquals(Right(3), cursor.get[Int]("retries"))
    assertEquals(Right(BigDecimal("0.875")), cursor.get[BigDecimal]("ratio"))
    assertEquals(Right(List("scala", "json")), cursor.get[List[String]]("tags"))
    assertEquals(Right(Some("literal")), cursor.get[Option[String]]("optionalName"))
    assertEquals(Right(None), cursor.get[Option[String]]("missingName"))
  }

  @Test
  def interpolatesExistingJsonValuesWithoutDoubleEncoding(): Unit = {
    val payload: Json = Json.obj(
      "message" -> Json.fromString("already-json"),
      "numbers" -> Json.arr(Json.fromInt(1), Json.fromInt(2))
    )
    val first: Json = Json.obj("id" -> Json.fromInt(1))
    val second: Json = Json.obj("id" -> Json.fromInt(2))

    val document: Json = json"""
      {
        "payload": $payload,
        "items": [$first, $second]
      }
      """

    assertEquals(Right("already-json"), document.hcursor.downField("payload").get[String]("message"))
    assertEquals(Right(List(1, 2)), document.hcursor.downField("payload").get[List[Int]]("numbers"))
    assertEquals(Right(List(1, 2)), document.hcursor.get[List[Json]]("items").map(_.map(item => expectRight(item.hcursor.get[Int]("id")))))
  }

  @Test
  def interpolatesObjectKeysThroughKeyEncoders(): Unit = {
    val featureKey: String = "feature.enabled"
    val region: LiteralRegion = LiteralRegion("eu-central")
    val nestedKey: LiteralRegion = LiteralRegion("retention-days")
    val limit: Int = 30

    val document: Json = json"""
      {
        $featureKey: true,
        $region: {
          $nestedKey: $limit
        }
      }
      """

    val cursor = document.hcursor

    assertEquals(Right(true), cursor.get[Boolean]("feature.enabled"))
    assertEquals(Right(30), cursor.downField("eu-central").get[Int]("retention-days"))
    assertEquals(Set("feature.enabled", "eu-central"), cursor.keys.map(_.toSet).getOrElse(Set.empty))
  }

  @Test
  def combinesLiteralAndInterpolatedSubtreesInArraysAndObjects(): Unit = {
    val owner: String = "Grace Hopper"
    val enabledFeatures: List[String] = List("compiler", "debugger")
    val account: LiteralAccount = LiteralAccount("acct-2", 100, Nil)

    val document: Json = json"""
      {
        "owner": $owner,
        "systems": [
          { "name": "compiler", "enabled": true },
          { "name": "debugger", "enabled": false }
        ],
        "enabledFeatures": $enabledFeatures,
        "account": $account
      }
      """

    val cursor = document.hcursor

    assertEquals(Right("Grace Hopper"), cursor.get[String]("owner"))
    assertEquals(Right(List("compiler", "debugger")), cursor.get[List[String]]("enabledFeatures"))
    assertEquals(Right(List(true, false)), cursor.downField("systems").as[List[Json]].map(_.map(system => expectRight(system.hcursor.get[Boolean]("enabled")))))
    assertEquals(Right("acct-2"), cursor.downField("account").get[String]("id"))
  }

  @Test
  def generatedJsonWorksWithStandardCirceTransformationsAndDecoders(): Unit = {
    val source: Json = json"""
      {
        "keep": "value",
        "drop": null,
        "nested": { "drop": null, "count": 2 },
        "items": [{ "value": 1 }, { "value": 2 }, { "value": 3 }]
      }
      """

    val transformed: Json = source.deepDropNullValues.mapObject(_.add("added", Json.fromString("literal")))
    val values: Either[DecodingFailure, List[Int]] = transformed.hcursor.downField("items").as[List[Json]].map { items =>
      items.map(item => expectRight(item.hcursor.get[Int]("value")))
    }

    assertFalse(transformed.hcursor.downField("drop").succeeded)
    assertFalse(transformed.hcursor.downField("nested").downField("drop").succeeded)
    assertEquals(Right(2), transformed.hcursor.downField("nested").get[Int]("count"))
    assertEquals(Right(List(1, 2, 3)), values)
    assertEquals(Some("literal"), transformed.hcursor.downField("added").focus.flatMap(_.asString))
  }

  private def expectRight[A](result: Either[?, A]): A = {
    result match {
      case Right(value) => value
      case Left(error) => fail[A](s"Expected successful result, but got: $error")
    }
  }
}
