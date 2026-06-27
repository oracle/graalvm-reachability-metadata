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
import io.circe.HCursor
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
  private final case class Deployment(name: String, version: String, replicas: Int)

  private final case class FeatureKey(namespace: String, name: String)

  private given Encoder[Deployment] = Encoder.instance { deployment =>
    Json.obj(
      "name" -> Json.fromString(deployment.name),
      "version" -> Json.fromString(deployment.version),
      "replicas" -> Json.fromInt(deployment.replicas)
    )
  }

  private given Decoder[Deployment] = Decoder.instance { cursor =>
    for {
      name <- cursor.get[String]("name")
      version <- cursor.get[String]("version")
      replicas <- cursor.get[Int]("replicas")
    } yield Deployment(name, version, replicas)
  }

  private given KeyEncoder[FeatureKey] = KeyEncoder.instance { key =>
    s"${key.namespace}/${key.name}"
  }

  @Test
  def buildsNestedJsonObjectsArraysAndScalarFieldsFromLiterals(): Unit = {
    val document: Json = json"""
      {
        "service": "catalog",
        "enabled": true,
        "replicas": 3,
        "limits": { "cpu": 2.5, "memory": null },
        "ports": [8080, 8443],
        "labels": ["public", "blue"]
      }
      """

    val cursor: HCursor = document.hcursor

    assertEquals("catalog", expectRight(cursor.get[String]("service")))
    assertTrue(expectRight(cursor.get[Boolean]("enabled")))
    assertEquals(3, expectRight(cursor.get[Int]("replicas")))
    assertEquals(BigDecimal("2.5"), expectRight(cursor.downField("limits").get[BigDecimal]("cpu")))
    assertTrue(cursor.downField("limits").downField("memory").focus.exists(_.isNull))
    assertEquals(List(8080, 8443), expectRight(cursor.get[List[Int]]("ports")))
    assertEquals(List("public", "blue"), expectRight(cursor.get[List[String]]("labels")))
  }

  @Test
  def createsTopLevelScalarJsonValues(): Unit = {
    val stringJson: Json = json""" "standalone" """
    val trueJson: Json = json""" true """
    val falseJson: Json = json""" false """
    val nullJson: Json = json""" null """
    val integerJson: Json = json""" 42 """
    val decimalJson: Json = json""" -12.50e2 """

    assertEquals(Some("standalone"), stringJson.asString)
    assertEquals(Some(true), trueJson.asBoolean)
    assertEquals(Some(false), falseJson.asBoolean)
    assertTrue(nullJson.isNull)
    assertEquals(Right(42), integerJson.as[Int])
    assertEquals(Right(BigDecimal("-1250")), decimalJson.as[BigDecimal])
  }

  @Test
  def preservesEscapedStringsUnicodeAndPreciseNumbers(): Unit = {
    val document: Json = json"""
      {
        "text": "line\nsnowman \u2603 quote \" slash /",
        "bigDecimal": 12345678901234567890.12345,
        "scientific": -1.25e2
      }
      """
    val cursor: HCursor = document.hcursor

    assertEquals("line\nsnowman ☃ quote \" slash /", expectRight(cursor.get[String]("text")))
    assertEquals(BigDecimal("12345678901234567890.12345"), expectRight(cursor.get[BigDecimal]("bigDecimal")))
    assertEquals(BigDecimal("-125"), expectRight(cursor.get[BigDecimal]("scientific")))

    val jsonNumber: JsonNumber = expectSome(document.hcursor.downField("bigDecimal").focus.flatMap(_.asNumber))
    assertEquals(Some(BigDecimal("12345678901234567890.12345")), jsonNumber.toBigDecimal)
  }

  @Test
  def interpolatesPrimitiveCollectionsAndJsonValuesAsObjectFields(): Unit = {
    val owner: String = "Ada"
    val retries: Int = 4
    val enabled: Boolean = true
    val tags: List[String] = List("canary", "json-literal")
    val weights: Map[String, BigDecimal] = Map("blue" -> BigDecimal("0.75"), "green" -> BigDecimal("0.25"))
    val metadata: Json = Json.obj(
      "source" -> Json.fromString("test"),
      "ordinal" -> Json.fromInt(7)
    )

    val document: Json = json"""
      {
        "owner": $owner,
        "retries": $retries,
        "enabled": $enabled,
        "tags": $tags,
        "weights": $weights,
        "metadata": $metadata
      }
      """
    val cursor: HCursor = document.hcursor

    assertEquals("Ada", expectRight(cursor.get[String]("owner")))
    assertEquals(4, expectRight(cursor.get[Int]("retries")))
    assertTrue(expectRight(cursor.get[Boolean]("enabled")))
    assertEquals(tags, expectRight(cursor.get[List[String]]("tags")))
    assertEquals(weights, expectRight(cursor.get[Map[String, BigDecimal]]("weights")))
    assertEquals("test", expectRight(cursor.downField("metadata").get[String]("source")))
    assertEquals(7, expectRight(cursor.downField("metadata").get[Int]("ordinal")))
  }

  @Test
  def interpolatesDomainValuesWithCustomEncoders(): Unit = {
    val primary: Deployment = Deployment("catalog", "2026.06", 3)
    val fallback: Deployment = Deployment("catalog-fallback", "2026.05", 1)

    val document: Json = json"""
      {
        "primary": $primary,
        "history": [$fallback, $primary]
      }
      """

    assertEquals(Right(primary), document.hcursor.downField("primary").as[Deployment])
    assertEquals(Right(List(fallback, primary)), document.hcursor.downField("history").as[List[Deployment]])
  }

  @Test
  def interpolatesDynamicObjectKeysWithKeyEncoders(): Unit = {
    val firstKey: FeatureKey = FeatureKey("payments", "refunds")
    val secondKey: FeatureKey = FeatureKey("search", "ranking")
    val firstValue: String = "enabled"
    val secondValue: Int = 10

    val document: Json = json"""
      {
        $firstKey: $firstValue,
        $secondKey: $secondValue,
        "static": true
      }
      """
    val cursor: HCursor = document.hcursor

    assertEquals("enabled", expectRight(cursor.get[String]("payments/refunds")))
    assertEquals(10, expectRight(cursor.get[Int]("search/ranking")))
    assertTrue(expectRight(cursor.get[Boolean]("static")))
    assertEquals(Set("payments/refunds", "search/ranking", "static"), cursor.keys.map(_.toSet).getOrElse(Set.empty))
  }

  @Test
  def supportsInterpolatedTopLevelArraysAndObjects(): Unit = {
    val service: String = "billing"
    val deployment: Deployment = Deployment(service, "1.2.3", 2)
    val extra: Json = json""" { "region": "eu", "active": true } """

    val array: Json = json""" [$service, $deployment, $extra, null] """
    val values: List[Json] = expectSome(array.asArray).toList

    assertEquals(Some("billing"), values(0).asString)
    assertEquals(Right(deployment), values(1).as[Deployment])
    assertEquals(Right("eu"), values(2).hcursor.get[String]("region"))
    assertTrue(values(3).isNull)
  }

  @Test
  def composesLiteralOutputWithCirceCursorEditingAndPrinting(): Unit = {
    val document: Json = json"""
      {
        "items": [
          { "id": 1, "status": "new" },
          { "id": 2, "status": "new" }
        ],
        "metadata": { "page": 1 }
      }
      """

    val updated: Json = expectSome(
      document.hcursor
        .downField("items")
        .downN(1)
        .downField("status")
        .withFocus(_.mapString(_ => "processed"))
        .top
    )

    assertEquals(Right("processed"), updated.hcursor.downField("items").downN(1).get[String]("status"))
    assertEquals(
      """{"items":[{"id":1,"status":"new"},{"id":2,"status":"processed"}],"metadata":{"page":1}}""",
      updated.noSpaces
    )
  }

  @Test
  def decodesLiteralDocumentsThroughPublicDecoderApi(): Unit = {
    val document: Json = json"""
      {
        "name": "gateway",
        "version": "9.1.0",
        "replicas": 5
      }
      """

    val decoded: Either[DecodingFailure, Deployment] = document.as[Deployment]

    assertEquals(Right(Deployment("gateway", "9.1.0", 5)), decoded)
  }

  @Test
  def reportsDecoderFailuresForLiteralJsonWithWrongShape(): Unit = {
    val document: Json = json"""
      {
        "name": "gateway",
        "version": 910,
        "replicas": "many"
      }
      """

    document.as[Deployment] match {
      case Left(failure) =>
        assertFalse(failure.message.isBlank)
        assertTrue(failure.history.nonEmpty)
      case Right(deployment) =>
        fail[Unit](s"Expected literal document decoding to fail, but decoded: $deployment")
    }
  }

  private def expectRight[A](result: Either[?, A]): A = {
    result match {
      case Right(value) => value
      case Left(error) => fail[A](s"Expected successful result, but got: $error")
    }
  }

  private def expectSome[A](option: Option[A]): A = {
    option.getOrElse(fail[A]("Expected value to be present"))
  }
}
