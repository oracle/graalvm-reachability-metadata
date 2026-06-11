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
import io.circe.JsonObject
import io.circe.KeyEncoder
import io.circe.jawn.parse
import io.circe.literal.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

final case class LiteralAddress(city: String, postalCode: String)

object LiteralAddress {
  given Decoder[LiteralAddress] = Decoder.forProduct2("city", "postalCode")(LiteralAddress.apply)
  given Encoder[LiteralAddress] = Encoder.forProduct2("city", "postalCode")(address =>
    (address.city, address.postalCode)
  )
}

final case class LiteralUser(
    id: Long,
    name: String,
    active: Boolean,
    address: LiteralAddress,
    tags: List[String],
    score: Option[BigDecimal]
)

object LiteralUser {
  given Decoder[LiteralUser] = Decoder.forProduct6("id", "name", "active", "address", "tags", "score")(
    LiteralUser.apply
  )
  given Encoder[LiteralUser] = Encoder.forProduct6("id", "name", "active", "address", "tags", "score")(user =>
    (user.id, user.name, user.active, user.address, user.tags, user.score)
  )
}

final case class LiteralFieldKey(section: String, name: String)

object LiteralFieldKey {
  given KeyEncoder[LiteralFieldKey] = KeyEncoder.instance(key => s"${key.section}.${key.name}")
}

class Circe_literal_3Test {
  @Test
  def createsJsonFromStaticMultiLineLiteral(): Unit = {
    val document: Json = json"""
      {
        "name": "Ada Lovelace",
        "active": true,
        "roles": ["analyst", "programmer"],
        "profile": {
          "age": 36,
          "nickname": null,
          "unicode": "snowman ☃",
          "escaped": "line\nbreak \"quoted\""
        },
        "precision": 12345678901234567890.125
      }
    """

    assertThat(document.isObject).isTrue
    assertThat(document.hcursor.get[String]("name")).isEqualTo(Right("Ada Lovelace"))
    assertThat(document.hcursor.get[Boolean]("active")).isEqualTo(Right(true))
    assertThat(document.hcursor.downField("roles").as[List[String]]).isEqualTo(Right(List("analyst", "programmer")))
    assertThat(document.hcursor.downField("profile").get[Option[String]]("nickname")).isEqualTo(Right(None))
    assertThat(document.hcursor.downField("profile").get[String]("unicode")).isEqualTo(Right("snowman ☃"))
    assertThat(document.hcursor.downField("profile").get[String]("escaped")).isEqualTo(Right("line\nbreak \"quoted\""))
    assertThat(document.hcursor.get[BigDecimal]("precision")).isEqualTo(Right(BigDecimal("12345678901234567890.125")))
  }

  @Test
  def decodesAdditionalJsonStringEscapesInLiterals(): Unit = {
    val document: Json = json"""
      {
        "tab": "alpha\tbeta",
        "carriageReturn": "alpha\rbeta",
        "backspace": "alpha\bbeta",
        "formFeed": "alpha\fbeta",
        "solidus": "https:\/\/example.com\/api",
        "backslash": "C:\\Users\\Ada"
      }
    """

    assertThat(document.hcursor.get[String]("tab")).isEqualTo(Right("alpha\tbeta"))
    assertThat(document.hcursor.get[String]("carriageReturn")).isEqualTo(Right("alpha\rbeta"))
    assertThat(document.hcursor.get[String]("backspace")).isEqualTo(Right("alpha\bbeta"))
    assertThat(document.hcursor.get[String]("formFeed")).isEqualTo(Right("alpha\fbeta"))
    assertThat(document.hcursor.get[String]("solidus")).isEqualTo(Right("https://example.com/api"))
    assertThat(document.hcursor.get[String]("backslash")).isEqualTo(Right("C:\\Users\\Ada"))
  }

  @Test
  def producesTheSameJsonAsRuntimeParsingForLiteralDocuments(): Unit = {
    val literalDocument: Json = json"""
      {
        "inventory": [
          { "sku": "book", "quantity": 2, "price": 12.50 },
          { "sku": "pen", "quantity": 5, "price": 1.20 }
        ],
        "metadata": {
          "source": "literal-test",
          "valid": true
        }
      }
    """

    val parsedDocument: Json = expectRight(parse("""
      {
        "inventory": [
          { "sku": "book", "quantity": 2, "price": 12.50 },
          { "sku": "pen", "quantity": 5, "price": 1.20 }
        ],
        "metadata": {
          "source": "literal-test",
          "valid": true
        }
      }
    """))

    assertThat(literalDocument).isEqualTo(parsedDocument)
    assertThat(literalDocument.noSpacesSortKeys).isEqualTo(parsedDocument.noSpacesSortKeys)
  }

  @Test
  def supportsTopLevelScalarLiteralsAndInterpolatedScalars(): Unit = {
    val enabled: Boolean = true
    val message: String = "hello \"circe\" ☕"
    val count: Int = 42
    val amount: BigDecimal = BigDecimal("19.875")

    assertThat(json"null").isEqualTo(Json.Null)
    assertThat(json"false").isEqualTo(Json.False)
    assertThat(json""""constant"""").isEqualTo(Json.fromString("constant"))
    assertThat(json"12345678901234567890").isEqualTo(Json.fromBigInt(BigInt("12345678901234567890")))
    assertThat(json"$enabled").isEqualTo(Json.fromBoolean(enabled))
    assertThat(json"$message").isEqualTo(Json.fromString(message))
    assertThat(json"$count").isEqualTo(Json.fromInt(count))
    assertThat(json"$amount").isEqualTo(Json.fromBigDecimal(amount))
  }

  @Test
  def interpolatesPrimitiveCollectionsOptionsAndExistingJsonValues(): Unit = {
    val id: Long = 1001L
    val tags: List[String] = List("admin", "billing")
    val scores: Vector[Int] = Vector(10, 20, 30)
    val maybeNote: Option[String] = Some("created from interpolation")
    val absent: Option[String] = None
    val extra: Json = Json.obj("verified" -> Json.True, "level" -> Json.fromInt(3))

    val document: Json = json"""
      {
        "id": $id,
        "tags": $tags,
        "scores": $scores,
        "note": $maybeNote,
        "missing": $absent,
        "extra": $extra
      }
    """

    assertThat(document.hcursor.get[Long]("id")).isEqualTo(Right(1001L))
    assertThat(document.hcursor.get[List[String]]("tags")).isEqualTo(Right(tags))
    assertThat(document.hcursor.get[Vector[Int]]("scores")).isEqualTo(Right(scores))
    assertThat(document.hcursor.get[Option[String]]("note")).isEqualTo(Right(maybeNote))
    assertThat(document.hcursor.get[Option[String]]("missing")).isEqualTo(Right(None))
    assertThat(document.hcursor.downField("extra").get[Boolean]("verified")).isEqualTo(Right(true))
    assertThat(document.hcursor.downField("extra").get[Int]("level")).isEqualTo(Right(3))
  }

  @Test
  def interpolatesDomainObjectsThroughPublicEncodersAndDecoders(): Unit = {
    val user: LiteralUser = LiteralUser(
      id = 7L,
      name = "Grace Hopper",
      active = true,
      address = LiteralAddress("Arlington", "22201"),
      tags = List("compiler", "navy"),
      score = Some(BigDecimal("99.5"))
    )

    val envelope: Json = json"""
      {
        "kind": "user-created",
        "payload": $user,
        "audit": [ $user, $user ]
      }
    """

    assertThat(envelope.hcursor.get[String]("kind")).isEqualTo(Right("user-created"))
    assertThat(envelope.hcursor.downField("payload").as[LiteralUser]).isEqualTo(Right(user))
    assertThat(envelope.hcursor.downField("payload").downField("address").get[String]("city"))
      .isEqualTo(Right("Arlington"))
    assertThat(envelope.hcursor.downField("audit").as[List[LiteralUser]]).isEqualTo(Right(List(user, user)))
  }

  @Test
  def interpolatesStringAndNonStringValuesInObjectKeyPositions(): Unit = {
    val stringKey: String = "field with \"quotes\" and newline\ninside"
    val numericKey: Int = 404
    val stringValue: String = "escaped value"
    val jsonValue: Json = Json.arr(Json.fromInt(1), Json.fromInt(2))

    val document: Json = json"""
      {
        $stringKey: $stringValue,
        $numericKey: $jsonValue,
        ${"literal-expression-key"}: ${BigDecimal("1.25")}
      }
    """

    val obj: JsonObject = expectObject(document)
    assertThat(obj(stringKey)).isEqualTo(Some(Json.fromString(stringValue)))
    assertThat(obj("404")).isEqualTo(Some(jsonValue))
    assertThat(obj("literal-expression-key")).isEqualTo(Some(Json.fromBigDecimal(BigDecimal("1.25"))))
    assertThat(obj.keys.toList.asJava).containsExactly(stringKey, "404", "literal-expression-key")
  }

  @Test
  def interpolatesObjectKeysWithCustomKeyEncoders(): Unit = {
    val displayName: LiteralFieldKey = LiteralFieldKey("profile", "displayName")
    val auditTrail: LiteralFieldKey = LiteralFieldKey("system", "auditTrail")

    val document: Json = json"""
      {
        $displayName: "Ada Lovelace",
        $auditTrail: ["created", "verified"]
      }
    """

    val obj: JsonObject = expectObject(document)
    assertThat(obj.keys.toList.asJava).containsExactly("profile.displayName", "system.auditTrail")
    assertThat(obj("profile.displayName")).isEqualTo(Some(Json.fromString("Ada Lovelace")))
    assertThat(document.hcursor.downField("system.auditTrail").as[List[String]])
      .isEqualTo(Right(List("created", "verified")))
  }

  @Test
  def supportsNestedInterpolationsInArraysAndObjects(): Unit = {
    val firstName: String = "Ada"
    val secondName: String = "Linus"
    val nestedNumbers: List[Int] = List(1, 1, 2, 3, 5)
    val preferences: Map[String, Boolean] = Map("email" -> true, "sms" -> false)

    val document: Json = json"""
      {
        "users": [
          { "name": $firstName, "numbers": $nestedNumbers },
          { "name": $secondName, "numbers": [8, 13, 21] }
        ],
        "preferences": $preferences,
        "matrix": [ [1, 2], $nestedNumbers, [] ]
      }
    """

    assertThat(document.hcursor.downField("users").downN(0).get[String]("name")).isEqualTo(Right("Ada"))
    assertThat(document.hcursor.downField("users").downN(0).get[List[Int]]("numbers")).isEqualTo(Right(nestedNumbers))
    assertThat(document.hcursor.downField("users").downN(1).get[List[Int]]("numbers")).isEqualTo(Right(List(8, 13, 21)))
    assertThat(document.hcursor.downField("preferences").get[Boolean]("email")).isEqualTo(Right(true))
    assertThat(document.hcursor.downField("preferences").get[Boolean]("sms")).isEqualTo(Right(false))
    assertThat(document.hcursor.downField("matrix").downN(1).as[List[Int]]).isEqualTo(Right(nestedNumbers))
  }

  @Test
  def preservesJsonObjectOrderingForLiteralAndInterpolatedFields(): Unit = {
    val dynamicKey: String = "second"
    val dynamicValue: Json = Json.obj("nested" -> Json.fromString("value"))

    val document: Json = json"""
      {
        "first": 1,
        $dynamicKey: $dynamicValue,
        "third": [true, false],
        ${4}: "fourth"
      }
    """

    val obj: JsonObject = expectObject(document)
    assertThat(obj.keys.toList.asJava).containsExactly("first", "second", "third", "4")
    assertThat(obj("first")).isEqualTo(Some(Json.fromInt(1)))
    assertThat(obj("second")).isEqualTo(Some(dynamicValue))
    assertThat(obj("third").flatMap(_.asArray).map(_.toList)).isEqualTo(Some(List(Json.True, Json.False)))
    assertThat(obj("4")).isEqualTo(Some(Json.fromString("fourth")))
  }

  @Test
  def literalResultsWorkWithStandardCirceTransformationsAndDecodingFailures(): Unit = {
    val document: Json = json"""
      {
        "payload": {
          "id": "not-a-long",
          "name": true,
          "active": "yes",
          "address": { "city": 123, "postalCode": false },
          "tags": "not-a-list",
          "score": "not-a-decimal"
        },
        "removeMe": null
      }
    """

    val cleaned: Json = document.deepDropNullValues.mapObject(_.add("processed", Json.True))
    assertThat(cleaned.hcursor.downField("removeMe").succeeded).isFalse
    assertThat(cleaned.hcursor.get[Boolean]("processed")).isEqualTo(Right(true))

    val failures: List[DecodingFailure] = document.hcursor.downField("payload").focus
      .getOrElse(fail("Expected payload object to be present"))
      .asAccumulating[LiteralUser]
      .fold(
        _.toList,
        value => fail(s"Expected literal payload decoding to fail, but decoded: $value")
      )

    assertThat(failures.size).isGreaterThanOrEqualTo(5)
    assertThat(failures.map(_.history.nonEmpty).asJava).contains(true)
  }

  private def expectRight[A](result: Either[?, A]): A = {
    result match {
      case Right(value) => value
      case Left(error) => fail[A](s"Expected successful result, but got: $error")
    }
  }

  private def expectObject(json: Json): JsonObject = {
    json.asObject.getOrElse(fail("Expected JSON object"))
  }
}
