/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_literal_3

import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.KeyEncoder
import io.circe.literal.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class Circe_literal_3Test {
  private final case class MetricValue(amount: BigDecimal, unit: String)

  private final case class MetricName(value: String)

  private final case class ServiceConfig(name: String, enabled: Boolean, ports: List[Int])

  private given Encoder[MetricValue] = Encoder.forProduct2("amount", "unit") { metric =>
    (metric.amount, metric.unit)
  }

  private given KeyEncoder[MetricName] = KeyEncoder.instance(_.value)

  private given Decoder[ServiceConfig] = Decoder.forProduct3("name", "enabled", "ports")(ServiceConfig.apply)

  @Test
  def buildsCompileTimeJsonObjectsWithAllJsonValueKinds(): Unit = {
    val document: Json = json"""
      {
        "name": "circe literal",
        "enabled": true,
        "count": 3,
        "ratio": 12.5,
        "nothing": null,
        "tags": ["json", "scala", { "nested": false }],
        "escaped": "snowman \u2603 and newline\ntext"
      }
    """

    val cursor = document.hcursor

    assertTrue(document.isObject)
    assertEquals("circe literal", expectRight(cursor.get[String]("name")))
    assertTrue(expectRight(cursor.get[Boolean]("enabled")))
    assertEquals(3, expectRight(cursor.get[Int]("count")))
    assertEquals(BigDecimal("12.5"), expectRight(cursor.get[BigDecimal]("ratio")))
    assertTrue(cursor.downField("nothing").focus.exists(_.isNull))
    assertEquals(List("json", "scala"), expectRight(cursor.get[List[Json]]("tags")).take(2).map(expectString))
    assertFalse(expectRight(cursor.downField("tags").downN(2).get[Boolean]("nested")))
    assertEquals("snowman ☃ and newline\ntext", expectRight(cursor.get[String]("escaped")))
  }

  @Test
  def supportsTopLevelScalarAndArrayLiterals(): Unit = {
    val nullValue: Json = json""" null """
    val booleanValue: Json = json""" false """
    val numberValue: Json = json""" -1234567890.125 """
    val stringValue: Json = json""" "standalone" """
    val arrayValue: Json = json""" [1, true, null, { "name": "entry" }] """

    assertTrue(nullValue.isNull)
    assertEquals(Some(false), booleanValue.asBoolean)
    assertTrue(numberValue.asNumber.exists(_.toBigDecimal.contains(BigDecimal("-1234567890.125"))))
    assertEquals(Some("standalone"), stringValue.asString)
    assertEquals(4, arrayValue.asArray.map(_.size).getOrElse(0))
    assertEquals("entry", expectRight(arrayValue.hcursor.downN(3).get[String]("name")))
  }

  @Test
  def interpolatesPrimitiveCollectionsOptionsAndJsonFragmentsWithEncoders(): Unit = {
    val owner: String = "Ada \"Compiler\""
    val enabled: Boolean = true
    val retries: Int = 4
    val maxEvents: Long = 9007199254740991L
    val tags: List[String] = List("macro", "literal", "json")
    val thresholds: Map[String, BigDecimal] = Map("warning" -> BigDecimal("0.75"), "critical" -> BigDecimal("0.90"))
    val missingOwner: Option[String] = None
    val nested: Json = json""" { "source": "prebuilt", "values": [1, 2, 3] } """

    val document: Json = json"""
      {
        "owner": $owner,
        "enabled": $enabled,
        "retries": $retries,
        "maxEvents": $maxEvents,
        "tags": $tags,
        "thresholds": $thresholds,
        "missingOwner": $missingOwner,
        "nested": $nested
      }
    """
    val cursor = document.hcursor

    assertEquals(owner, expectRight(cursor.get[String]("owner")))
    assertTrue(expectRight(cursor.get[Boolean]("enabled")))
    assertEquals(retries, expectRight(cursor.get[Int]("retries")))
    assertEquals(maxEvents, expectRight(cursor.get[Long]("maxEvents")))
    assertEquals(tags, expectRight(cursor.get[List[String]]("tags")))
    assertEquals(BigDecimal("0.75"), expectRight(cursor.downField("thresholds").get[BigDecimal]("warning")))
    assertEquals(BigDecimal("0.90"), expectRight(cursor.downField("thresholds").get[BigDecimal]("critical")))
    assertTrue(cursor.downField("missingOwner").focus.exists(_.isNull))
    assertEquals(List(1, 2, 3), expectRight(cursor.downField("nested").get[List[Int]]("values")))
  }

  @Test
  def interpolatesObjectKeysAndDomainValuesThroughKeyEncoderAndEncoder(): Unit = {
    val cpuKey: MetricName = MetricName("runtime.cpu.load")
    val memoryKey: MetricName = MetricName("runtime.memory.used")
    val cpuMetric: MetricValue = MetricValue(BigDecimal("0.42"), "ratio")
    val memoryMetric: MetricValue = MetricValue(BigDecimal("1536"), "megabytes")

    val document: Json = json"""
      {
        "metrics": {
          $cpuKey: $cpuMetric,
          $memoryKey: $memoryMetric
        }
      }
    """
    val metrics = document.hcursor.downField("metrics")

    assertEquals(BigDecimal("0.42"), expectRight(metrics.downField("runtime.cpu.load").get[BigDecimal]("amount")))
    assertEquals("ratio", expectRight(metrics.downField("runtime.cpu.load").get[String]("unit")))
    assertEquals(BigDecimal("1536"), expectRight(metrics.downField("runtime.memory.used").get[BigDecimal]("amount")))
    assertEquals("megabytes", expectRight(metrics.downField("runtime.memory.used").get[String]("unit")))
  }

  @Test
  def producesJsonThatCanBeDecodedAndTransformedWithCircePublicApis(): Unit = {
    val serviceName: String = "edge-gateway"
    val primaryPort: Int = 8443
    val fallbackPorts: List[Int] = List(8080, 8081)

    val document: Json = json"""
      {
        "name": $serviceName,
        "enabled": true,
        "ports": [$primaryPort, $fallbackPorts],
        "metadata": {
          "createdBy": "circe-literal",
          "features": ["compile-time-parse", "interpolation"]
        }
      }
    """

    val flattenedPorts = document.hcursor.downField("ports").as[List[Json]].map { values =>
      values.flatMap {
        case value if value.isNumber => value.asNumber.flatMap(_.toInt).toList
        case value => value.asArray.toList.flatten.flatMap(_.asNumber.flatMap(_.toInt))
      }
    }
    val decodedConfig: ServiceConfig = expectRight(
      document.mapObject(_.add("ports", Json.fromValues(expectRight(flattenedPorts).map(Json.fromInt)))).as[ServiceConfig]
    )
    val featureNames: List[String] = expectRight(document.hcursor.downField("metadata").get[List[String]]("features"))

    assertEquals(ServiceConfig("edge-gateway", enabled = true, List(8443, 8080, 8081)), decodedConfig)
    assertEquals(List("compile-time-parse", "interpolation"), featureNames)
    assertTrue(document.noSpacesSortKeys.contains("\"createdBy\":\"circe-literal\""))
  }

  private def expectString(json: Json): String = {
    json.asString.getOrElse(fail[String](s"Expected JSON string, but got: $json"))
  }

  private def expectRight[A](result: Either[?, A]): A = {
    result match {
      case Right(value) => value
      case Left(error) => fail[A](s"Expected successful result, but got: $error")
    }
  }
}
