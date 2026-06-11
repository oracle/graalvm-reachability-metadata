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
import java.net.URI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Circe_literal_3Test {
  @Test
  def literalBuildsNestedJsonWithPrimitiveValues(): Unit = {
    val document: Json = json"""
      {
        "name": "circe literal",
        "active": true,
        "count": 3,
        "ratio": 12.5,
        "nothing": null,
        "tags": ["scala", "json", { "nested": false }]
      }
      """

    val expected: Json = Json.obj(
      "name" -> Json.fromString("circe literal"),
      "active" -> Json.fromBoolean(true),
      "count" -> Json.fromInt(3),
      "ratio" -> Json.fromBigDecimal(BigDecimal("12.5")),
      "nothing" -> Json.Null,
      "tags" -> Json.arr(
        Json.fromString("scala"),
        Json.fromString("json"),
        Json.obj("nested" -> Json.fromBoolean(false))
      )
    )

    assertThat(document).isEqualTo(expected)
  }

  @Test
  def interpolationEncodesValuesAndObjectKeys(): Unit = {
    val dynamicKey: String = "user-id"
    val identifier: Int = 42
    val enabled: Boolean = true
    val aliases: List[String] = List("admin", "operator")
    val optionalLabel: Option[String] = Some("primary")
    val missingScore: Option[Int] = None

    val document: Json = json"""
      {
        $dynamicKey: $identifier,
        "enabled": $enabled,
        "aliases": $aliases,
        "label": $optionalLabel,
        "score": $missingScore
      }
      """

    val expected: Json = Json.obj(
      "user-id" -> Json.fromInt(42),
      "enabled" -> Json.fromBoolean(true),
      "aliases" -> Json.arr(Json.fromString("admin"), Json.fromString("operator")),
      "label" -> Json.fromString("primary"),
      "score" -> Json.Null
    )

    assertThat(document).isEqualTo(expected)
  }

  @Test
  def interpolationUsesCustomKeyEncodersForObjectKeys(): Unit = {
    final case class TenantSegment(region: String, shard: Int)
    given KeyEncoder[TenantSegment] = KeyEncoder.instance { segment =>
      s"${segment.region}/shard-${segment.shard}"
    }

    val primary: TenantSegment = TenantSegment("eu", 2)
    val fallback: TenantSegment = TenantSegment("us", 7)

    val document: Json = json"""
      {
        $primary: "active",
        $fallback: { "status": "standby" }
      }
      """

    val expected: Json = Json.obj(
      "eu/shard-2" -> Json.fromString("active"),
      "us/shard-7" -> Json.obj("status" -> Json.fromString("standby"))
    )

    assertThat(document).isEqualTo(expected)
  }

  @Test
  def topLevelInterpolationEmbedsExistingJsonValues(): Unit = {
    val payload: Json = Json.obj(
      "kind" -> Json.fromString("event"),
      "sequence" -> Json.arr(Json.fromInt(1), Json.fromInt(2), Json.fromInt(3))
    )

    val document: Json = json"""$payload"""

    assertThat(document).isEqualTo(payload)
  }

  @Test
  def arrayInterpolationCombinesEncodedScalarsAndLiteralElements(): Unit = {
    val first: Int = 7
    val second: BigDecimal = BigDecimal("99.125")
    val third: Boolean = false

    val document: Json = json"""[$first, $second, $third, "literal", null]"""

    val expected: Json = Json.arr(
      Json.fromInt(7),
      Json.fromBigDecimal(BigDecimal("99.125")),
      Json.fromBoolean(false),
      Json.fromString("literal"),
      Json.Null
    )

    assertThat(document).isEqualTo(expected)
  }

  @Test
  def interpolationUsesCustomEncodersInScope(): Unit = {
    given Encoder[URI] = Encoder.encodeString.contramap[URI](_.toASCIIString)

    val endpoint: URI = URI.create("https://example.com/services/search?q=circe%20literal")
    val document: Json = json"""
      {
        "endpoint": $endpoint
      }
      """

    val expected: Json = Json.obj(
      "endpoint" -> Json.fromString("https://example.com/services/search?q=circe%20literal")
    )

    assertThat(document).isEqualTo(expected)
  }

  @Test
  def literalPreservesJsonEscapesAndInterpolatedStrings(): Unit = {
    val interpolated: String = "line one\nline two with \"quotes\""

    val document: Json = json"""
      {
        "escaped": "tab\tnewline\nquote\"backslash\\",
        "unicode": "λ",
        "interpolated": $interpolated
      }
      """

    val expected: Json = Json.obj(
      "escaped" -> Json.fromString("tab\tnewline\nquote\"backslash\\"),
      "unicode" -> Json.fromString("λ"),
      "interpolated" -> Json.fromString("line one\nline two with \"quotes\"")
    )

    assertThat(document).isEqualTo(expected)
  }
}
