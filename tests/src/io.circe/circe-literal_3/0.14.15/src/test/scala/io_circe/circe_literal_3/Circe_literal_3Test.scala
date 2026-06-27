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

final case class LiteralUser(name: String, scores: List[Int], active: Boolean)

object LiteralUser {
  given Encoder.AsObject[LiteralUser] = Encoder.AsObject.instance { user =>
    Json.obj(
      "name" -> Json.fromString(user.name),
      "scores" -> Encoder[List[Int]].apply(user.scores),
      "active" -> Json.fromBoolean(user.active)
    ).asObject.getOrElse(fail("Expected encoded user to be a JSON object"))
  }

  given Decoder[LiteralUser] = Decoder.forProduct3("name", "scores", "active")(LiteralUser.apply)
}

final case class FeatureKey(value: String)

object FeatureKey {
  given KeyEncoder[FeatureKey] = KeyEncoder.instance(key => s"feature:${key.value}")
}

final case class AuditEvent(id: Long, message: String)

object AuditEvent {
  given Encoder[AuditEvent] = Encoder.instance { event =>
    Json.obj(
      "id" -> Json.fromLong(event.id),
      "message" -> Json.fromString(event.message)
    )
  }
}

class Circe_literal_3Test {
  @Test
  def buildsNestedJsonLiteralWithEveryJsonType(): Unit = {
    val document: Json = json"""
      {
        "text": "hello\nworld",
        "count": 3,
        "negative": -2,
        "decimal": 12.50,
        "flag": true,
        "missing": null,
        "items": [1, "two", false, { "nested": [null, true] }]
      }
    """

    assertThat(document.isObject).isTrue
    assertThat(document.hcursor.get[String]("text")).isEqualTo(Right("hello\nworld"))
    assertThat(document.hcursor.get[Int]("count")).isEqualTo(Right(3))
    assertThat(document.hcursor.get[Int]("negative")).isEqualTo(Right(-2))
    assertThat(document.hcursor.get[BigDecimal]("decimal")).isEqualTo(Right(BigDecimal("12.50")))
    assertThat(document.hcursor.get[Boolean]("flag")).isEqualTo(Right(true))
    assertThat(document.hcursor.get[Option[String]]("missing")).isEqualTo(Right(None))
    assertThat(document.hcursor.downField("items").downN(3).downField("nested").downN(1).as[Boolean])
      .isEqualTo(Right(true))
  }

  @Test
  def interpolatesPrimitiveValuesCollectionsAndJsonSubtrees(): Unit = {
    val name: String = "Ada Lovelace"
    val attempts: Int = 4
    val ratio: BigDecimal = BigDecimal("0.875")
    val enabled: Boolean = true
    val tags: List[String] = List("analyst", "programmer")
    val metadata: Json = json"""{"language": "scala", "rank": 1}"""

    val document: Json = json"""
      {
        "name": $name,
        "attempts": $attempts,
        "ratio": $ratio,
        "enabled": $enabled,
        "tags": $tags,
        "metadata": $metadata
      }
    """

    assertThat(document.hcursor.get[String]("name")).isEqualTo(Right(name))
    assertThat(document.hcursor.get[Int]("attempts")).isEqualTo(Right(attempts))
    assertThat(document.hcursor.get[BigDecimal]("ratio")).isEqualTo(Right(ratio))
    assertThat(document.hcursor.get[Boolean]("enabled")).isEqualTo(Right(enabled))
    assertThat(document.hcursor.get[List[String]]("tags")).isEqualTo(Right(tags))
    assertThat(document.hcursor.downField("metadata").get[String]("language")).isEqualTo(Right("scala"))
    assertThat(document.noSpacesSortKeys).contains("\"metadata\":{\"language\":\"scala\",\"rank\":1}")
  }

  @Test
  def interpolatesValuesWithCustomEncodersInsideObjectsAndArrays(): Unit = {
    val primaryUser: LiteralUser = LiteralUser("Grace Hopper", List(10, 9, 10), active = true)
    val secondaryUser: LiteralUser = LiteralUser("Katherine Johnson", List(8, 9), active = false)
    val event: AuditEvent = AuditEvent(42L, "created")

    val document: Json = json"""
      {
        "primary": $primaryUser,
        "participants": [$primaryUser, $secondaryUser],
        "audit": { "last": $event }
      }
    """

    assertThat(document.hcursor.get[LiteralUser]("primary")).isEqualTo(Right(primaryUser))
    assertThat(document.hcursor.downField("participants").downN(1).as[LiteralUser]).isEqualTo(Right(secondaryUser))
    assertThat(document.hcursor.downField("audit").downField("last").get[Long]("id")).isEqualTo(Right(42L))
    assertThat(document.hcursor.downField("audit").downField("last").get[String]("message")).isEqualTo(Right("created"))
  }

  @Test
  def interpolatesObjectKeysWithKeyEncoders(): Unit = {
    val featureKey: FeatureKey = FeatureKey("search")
    val ownerKey: String = "owner"
    val priorityKey: String = "priority"

    val document: Json = json"""
      {
        $featureKey: { "enabled": true, "rollout": 25 },
        $ownerKey: "platform",
        $priorityKey: 7
      }
    """

    val cursor = document.hcursor
    assertThat(cursor.downField("feature:search").get[Boolean]("enabled")).isEqualTo(Right(true))
    assertThat(cursor.downField("feature:search").get[Int]("rollout")).isEqualTo(Right(25))
    assertThat(cursor.get[String]("owner")).isEqualTo(Right("platform"))
    assertThat(cursor.get[Int]("priority")).isEqualTo(Right(7))
    assertThat(document.noSpacesSortKeys)
      .isEqualTo("""{"feature:search":{"enabled":true,"rollout":25},"owner":"platform","priority":7}""")
  }

  @Test
  def composesLiteralTemplatesWithCirceObjectOperations(): Unit = {
    val dynamicFeature: FeatureKey = FeatureKey("recommendations")
    val base: Json = json"""
      {
        "retry": { "max": 3, "backoff": "linear" },
        "features": { "alpha": false }
      }
    """
    val overrideJson: Json = json"""
      {
        "retry": { "backoff": "exponential" },
        "features": { $dynamicFeature: true }
      }
    """

    val merged: Json = base.deepMerge(overrideJson)

    assertThat(merged.hcursor.downField("retry").get[Int]("max")).isEqualTo(Right(3))
    assertThat(merged.hcursor.downField("retry").get[String]("backoff")).isEqualTo(Right("exponential"))
    assertThat(merged.hcursor.downField("features").get[Boolean]("alpha")).isEqualTo(Right(false))
    assertThat(merged.hcursor.downField("features").get[Boolean]("feature:recommendations"))
      .isEqualTo(Right(true))
  }

  @Test
  def preservesInterpolatedStringEscapingAndJsonNumberPrecision(): Unit = {
    val quotedText: String = "quote=\"yes\", slash=\\, newline=\n"
    val preciseDecimal: BigDecimal = BigDecimal("12345678901234567890.125")
    val preciseInteger: BigInt = BigInt("987654321098765432109876543210")

    val document: Json = json"""
      {
        "quotedText": $quotedText,
        "preciseDecimal": $preciseDecimal,
        "preciseInteger": $preciseInteger
      }
    """

    assertThat(document.hcursor.get[String]("quotedText")).isEqualTo(Right(quotedText))
    assertThat(document.hcursor.get[BigDecimal]("preciseDecimal")).isEqualTo(Right(preciseDecimal))
    assertThat(document.hcursor.get[BigInt]("preciseInteger")).isEqualTo(Right(preciseInteger))
    assertThat(document.noSpaces).contains("\\\"yes\\\"")
    assertThat(document.noSpaces).contains("987654321098765432109876543210")
  }

  @Test
  def literalDocumentsDecodeThroughStandardCirceDecoders(): Unit = {
    val document: Json = json"""
      {
        "name": "Hedy Lamarr",
        "scores": [7, 8, 9],
        "active": true
      }
    """

    val decoded: Either[DecodingFailure, LiteralUser] = document.as[LiteralUser]

    assertThat(decoded).isEqualTo(Right(LiteralUser("Hedy Lamarr", List(7, 8, 9), active = true)))
  }

  @Test
  def buildsTopLevelScalarLiteralsAndInterpolatedValues(): Unit = {
    val status: Json = json"""
      "ready"
    """
    val sequenceNumber: Json = json"""12345"""
    val emptyValue: Json = json"""null"""
    val stages: List[String] = List("queued", "running", "done")

    val encodedStages: Json = json"""$stages"""

    assertThat(status.asString).isEqualTo(Some("ready"))
    assertThat(sequenceNumber.asNumber.flatMap(_.toInt)).isEqualTo(Some(12345))
    assertThat(emptyValue.isNull).isTrue
    assertThat(encodedStages.as[List[String]]).isEqualTo(Right(stages))
  }
}
