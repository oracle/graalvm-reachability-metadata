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
import io.circe.KeyEncoder
import io.circe.literal.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

final case class LiteralUser(name: String, roles: List[String], score: BigDecimal)

object LiteralUser {
  given Encoder[LiteralUser] = Encoder.forProduct3("name", "roles", "score") { user =>
    (user.name, user.roles, user.score)
  }
}

final case class LiteralField(namespace: String, name: String)

object LiteralField {
  given KeyEncoder[LiteralField] = KeyEncoder.instance { field =>
    s"${field.namespace}:${field.name}"
  }
}

class Circe_literal_3Test {
  @Test
  def createsJsonValuesFromLiteralSyntax(): Unit = {
    val document: Json = json"""
      {
        "nullValue": null,
        "booleanValue": true,
        "integerValue": -42,
        "decimalValue": 12345678901234567890.125,
        "exponentValue": -1.25e10,
        "escapedText": "line one\nline two \"quoted\" \u2603",
        "arrayValue": [false, 0, "text", { "nested": [1, 2, 3] }]
      }
    """

    assertEquals(Json.Null, decodeOrFail(document.hcursor.get[Json]("nullValue")))
    assertEquals(Right(true), document.hcursor.get[Boolean]("booleanValue"))
    assertEquals(Right(-42), document.hcursor.get[Int]("integerValue"))
    assertEquals(Right(BigDecimal("12345678901234567890.125")), document.hcursor.get[BigDecimal]("decimalValue"))
    assertEquals(Right(BigDecimal("-12500000000")), document.hcursor.get[BigDecimal]("exponentValue"))
    assertEquals(Right("line one\nline two \"quoted\" ☃"), document.hcursor.get[String]("escapedText"))
    assertEquals(Right(List(1, 2, 3)), document.hcursor.downField("arrayValue").downN(3).downField("nested").as[List[Int]])
  }

  @Test
  def supportsRootScalarsArraysAndObjects(): Unit = {
    val stringJson: Json = json""""plain text""""
    val numberJson: Json = json"""12345"""
    val booleanJson: Json = json"""false"""
    val nullJson: Json = json"""null"""
    val arrayJson: Json = json"""["first", { "second": 2 }, true]"""
    val objectJson: Json = json"""{ "a": 1, "b": [2, 3] }"""

    assertEquals(Some("plain text"), stringJson.asString)
    assertEquals(Some(12345), numberJson.asNumber.flatMap(_.toInt))
    assertEquals(Some(false), booleanJson.asBoolean)
    assertEquals(Json.Null, nullJson)
    assertEquals("[\"first\",{\"second\":2},true]", arrayJson.noSpaces)
    assertEquals("{\"a\":1,\"b\":[2,3]}", objectJson.noSpaces)
  }

  @Test
  def interpolatesPrimitiveAndStructuredValuesWithEncoders(): Unit = {
    val user: LiteralUser = LiteralUser("Ada", List("admin", "operator"), BigDecimal("99.5"))
    val message: String = "hello \"Ada\"\nwelcome"
    val metadata: Json = Json.obj("verified" -> Json.True, "attempts" -> Json.fromInt(2))
    val missing: Option[String] = Option.empty[String]

    val document: Json = json"""
      {
        "id": ${42},
        "active": ${true},
        "message": $message,
        "user": $user,
        "metadata": $metadata,
        "tags": ${List("native", "json", "literal")},
        "missing": $missing
      }
    """

    assertEquals(Right(42), document.hcursor.get[Int]("id"))
    assertEquals(Right(true), document.hcursor.get[Boolean]("active"))
    assertEquals(Right(message), document.hcursor.get[String]("message"))
    assertEquals(Right("Ada"), document.hcursor.downField("user").get[String]("name"))
    assertEquals(Right(List("admin", "operator")), document.hcursor.downField("user").get[List[String]]("roles"))
    assertEquals(Right(BigDecimal("99.5")), document.hcursor.downField("user").get[BigDecimal]("score"))
    assertEquals(Right(true), document.hcursor.downField("metadata").get[Boolean]("verified"))
    assertEquals(Right(List("native", "json", "literal")), document.hcursor.get[List[String]]("tags"))
    assertEquals(Json.Null, decodeOrFail(document.hcursor.get[Json]("missing")))
  }

  @Test
  def interpolatesValuesInsideArraysAndAtTheRoot(): Unit = {
    val nested: Json = Json.arr(Json.fromString("generated"), Json.fromInt(7))
    val present: Option[String] = Some("value")

    val array: Json = json"""[${1}, $nested, $present, ${Option.empty[String]}, "literal"]"""
    val rootObject: Json = json"""${Json.obj("root" -> Json.fromString("interpolated"))}"""
    val rootString: Json = json"""${"encoded as a JSON string"}"""

    assertEquals("[1,[\"generated\",7],\"value\",null,\"literal\"]", array.noSpaces)
    assertEquals(Right("interpolated"), rootObject.hcursor.get[String]("root"))
    assertEquals(Some("encoded as a JSON string"), rootString.asString)
  }

  @Test
  def interpolatesObjectKeysWithKeyEncoders(): Unit = {
    val featureFlag: LiteralField = LiteralField("feature", "enabled")
    val rollout: LiteralField = LiteralField("rollout", "percentage")
    val staticKeyValue: String = "static-value"

    val document: Json = json"""
      {
        $featureFlag: true,
        $rollout: ${25},
        "static": $staticKeyValue
      }
    """

    assertEquals(Right(true), document.hcursor.get[Boolean]("feature:enabled"))
    assertEquals(Right(25), document.hcursor.get[Int]("rollout:percentage"))
    assertEquals(Right("static-value"), document.hcursor.get[String]("static"))
    assertEquals("{\"feature:enabled\":true,\"rollout:percentage\":25,\"static\":\"static-value\"}", document.noSpaces)
  }

  @Test
  def interpolatesStringObjectKeysThatRequireEscaping(): Unit = {
    val quotedKey: String = "line\none \"quoted\" key"
    val unicodeKey: String = "snowman-☃"

    val document: Json = json"""
      {
        $quotedKey: ${1},
        $unicodeKey: "present"
      }
    """

    assertEquals(Right(1), document.hcursor.get[Int](quotedKey))
    assertEquals(Right("present"), document.hcursor.get[String](unicodeKey))
    assertEquals(Set(quotedKey, unicodeKey), document.asObject.map(_.keys.toSet).getOrElse(Set.empty))
  }

  @Test
  def keepsRepeatedInterpolationsIndependent(): Unit = {
    val repeatedText: String = "same runtime value"
    val firstKey: LiteralField = LiteralField("key", "one")
    val secondKey: LiteralField = LiteralField("key", "two")

    val document: Json = json"""
      {
        $firstKey: $repeatedText,
        $secondKey: $repeatedText,
        "array": [$repeatedText, $repeatedText]
      }
    """

    assertEquals(Right(repeatedText), document.hcursor.get[String]("key:one"))
    assertEquals(Right(repeatedText), document.hcursor.get[String]("key:two"))
    assertEquals(Right(List(repeatedText, repeatedText)), document.hcursor.get[List[String]]("array"))
  }

  private def decodeOrFail[A](result: Either[DecodingFailure, A]): A = {
    result.fold(error => fail(s"Expected decoding success but got: $error"), identity)
  }
}
