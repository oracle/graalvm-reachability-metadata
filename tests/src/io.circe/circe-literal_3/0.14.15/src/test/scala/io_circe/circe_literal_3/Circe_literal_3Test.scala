/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_literal_3

import io.circe.Json
import io.circe.literal.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class Circe_literal_3Test {
  @Test
  def createsPrimitiveJsonValuesAtCompileTime(): Unit = {
    val nullValue: Json = json"""null"""
    val trueValue: Json = json"""true"""
    val falseValue: Json = json"""false"""
    val integerValue: Json = json"""12345"""
    val decimalValue: Json = json"""-9876.54321"""
    val stringValue: Json = json"""
      "circe literal \"quotes\" and unicode \u263a"
    """

    assertThat(nullValue.isNull).isTrue()
    assertThat(trueValue.asBoolean).isEqualTo(Some(true))
    assertThat(falseValue.asBoolean).isEqualTo(Some(false))
    assertThat(integerValue.asNumber.flatMap(_.toInt)).isEqualTo(Some(12345))
    assertThat(decimalValue.asNumber.flatMap(_.toBigDecimal)).isEqualTo(Some(BigDecimal("-9876.54321")))
    assertThat(stringValue.asString).isEqualTo(Some("circe literal \"quotes\" and unicode ☺"))
  }

  @Test
  def buildsNestedObjectsAndArraysFromJsonLiterals(): Unit = {
    val document: Json = json"""
      {
        "service": "catalog",
        "release": 7,
        "enabled": true,
        "limits": {
          "maxItems": 100,
          "timeoutSeconds": 15.5
        },
        "routes": [
          { "method": "GET", "path": "/items", "cacheable": true },
          { "method": "POST", "path": "/items", "cacheable": false }
        ],
        "optional": null
      }
    """

    val cursor = document.hcursor
    val routes: Vector[Json] = cursor.downField("routes").focus.flatMap(_.asArray).getOrElse(Vector.empty)

    assertThat(document.isObject).isTrue()
    assertThat(cursor.downField("service").as[String]).isEqualTo(Right("catalog"))
    assertThat(cursor.downField("release").as[Int]).isEqualTo(Right(7))
    assertThat(cursor.downField("limits").downField("maxItems").as[Int]).isEqualTo(Right(100))
    assertThat(cursor.downField("limits").downField("timeoutSeconds").as[Double]).isEqualTo(Right(15.5))
    assertThat(cursor.downField("optional").focus.exists(_.isNull)).isTrue()
    assertThat(routes.asJava).hasSize(2)
    assertThat(routes.head.hcursor.downField("method").as[String]).isEqualTo(Right("GET"))
    assertThat(routes.head.hcursor.downField("cacheable").as[Boolean]).isEqualTo(Right(true))
    assertThat(routes.last.hcursor.downField("method").as[String]).isEqualTo(Right("POST"))
    assertThat(routes.last.hcursor.downField("cacheable").as[Boolean]).isEqualTo(Right(false))
  }

  @Test
  def encodesInterpolatedValuesWithCirceEncoders(): Unit = {
    val requestFieldName: String = "requestId"
    val requestId: String = "req-123"
    val retryCount: Int = 3
    val enabled: Boolean = true
    val tags: List[String] = List("compile-time", "native-image", "json")
    val optionalOwner: Option[String] = None
    val nestedConfig: Json = json"""{ "mode": "strict", "sampleRate": 0.25 }"""
    val statusCounts: Map[String, Int] = Map("success" -> 10, "failure" -> 2)

    val payload: Json = json"""
      {
        $requestFieldName: $requestId,
        "retryCount": $retryCount,
        "enabled": $enabled,
        "tags": $tags,
        "owner": $optionalOwner,
        "config": $nestedConfig,
        "statusCounts": $statusCounts
      }
    """

    val cursor = payload.hcursor
    val tagValues: Vector[Json] = cursor.downField("tags").focus.flatMap(_.asArray).getOrElse(Vector.empty)

    assertThat(cursor.downField("requestId").as[String]).isEqualTo(Right("req-123"))
    assertThat(cursor.downField("retryCount").as[Int]).isEqualTo(Right(3))
    assertThat(cursor.downField("enabled").as[Boolean]).isEqualTo(Right(true))
    assertThat(tagValues.flatMap(_.asString).asJava).containsExactly("compile-time", "native-image", "json")
    assertThat(cursor.downField("owner").focus.exists(_.isNull)).isTrue()
    assertThat(cursor.downField("config").downField("mode").as[String]).isEqualTo(Right("strict"))
    assertThat(cursor.downField("config").downField("sampleRate").as[Double]).isEqualTo(Right(0.25))
    assertThat(cursor.downField("statusCounts").downField("success").as[Int]).isEqualTo(Right(10))
    assertThat(cursor.downField("statusCounts").downField("failure").as[Int]).isEqualTo(Right(2))
  }

  @Test
  def supportsTopLevelInterpolatedCollectionsAndLiteralComposition(): Unit = {
    val first: Json = json"""{ "name": "Ada", "scores": [10, 9, 10] }"""
    val second: Json = json"""{ "name": "Grace", "scores": [8, 10, 9] }"""
    val generatedBy: String = "circe-literal"
    val records: List[Json] = List(first, second)

    val report: Json = json"""
      {
        "generatedBy": $generatedBy,
        "records": $records,
        "summary": {
          "count": 2,
          "complete": true
        }
      }
    """

    val cursor = report.hcursor
    val recordValues: Vector[Json] = cursor.downField("records").focus.flatMap(_.asArray).getOrElse(Vector.empty)

    assertThat(cursor.downField("generatedBy").as[String]).isEqualTo(Right("circe-literal"))
    assertThat(cursor.downField("summary").downField("count").as[Int]).isEqualTo(Right(2))
    assertThat(cursor.downField("summary").downField("complete").as[Boolean]).isEqualTo(Right(true))
    assertThat(recordValues.asJava).hasSize(2)
    val firstScores: Vector[Json] = recordValues.head.hcursor
      .downField("scores")
      .focus
      .flatMap(_.asArray)
      .getOrElse(Vector.empty)
    val secondScores: Vector[Json] = recordValues.last.hcursor
      .downField("scores")
      .focus
      .flatMap(_.asArray)
      .getOrElse(Vector.empty)

    assertThat(recordValues.head.hcursor.downField("name").as[String]).isEqualTo(Right("Ada"))
    assertThat(firstScores.head.asNumber.flatMap(_.toInt)).isEqualTo(Some(10))
    assertThat(recordValues.last.hcursor.downField("name").as[String]).isEqualTo(Right("Grace"))
    assertThat(secondScores(1).asNumber.flatMap(_.toInt)).isEqualTo(Some(10))
  }

  @Test
  def producesStableCirceJsonThatCanBeTransformedAfterLiteralCreation(): Unit = {
    val base: Json = json"""
      {
        "name": "initial",
        "metadata": {
          "attempt": 1,
          "active": true
        },
        "items": ["alpha", "beta"]
      }
    """

    val transformed: Json = base
      .mapObject(_.add("createdBy", Json.fromString("test-suite")))
      .hcursor
      .downField("metadata")
      .withFocus(_.mapObject(_.add("attempt", Json.fromInt(2))))
      .top
      .getOrElse(Json.Null)

    val cursor = transformed.hcursor

    assertThat(cursor.downField("name").as[String]).isEqualTo(Right("initial"))
    assertThat(cursor.downField("createdBy").as[String]).isEqualTo(Right("test-suite"))
    assertThat(cursor.downField("metadata").downField("attempt").as[Int]).isEqualTo(Right(2))
    assertThat(cursor.downField("metadata").downField("active").as[Boolean]).isEqualTo(Right(true))
    val itemValues: Vector[Json] = cursor.downField("items").focus.flatMap(_.asArray).getOrElse(Vector.empty)

    assertThat(itemValues(1).asString).isEqualTo(Some("beta"))
  }
}
