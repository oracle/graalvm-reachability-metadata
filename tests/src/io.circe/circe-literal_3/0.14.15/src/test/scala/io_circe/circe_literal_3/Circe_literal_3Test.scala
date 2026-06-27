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
import io.circe.Printer
import io.circe.literal._
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class Circe_literal_3Test {
  private final case class BuildInfo(name: String, number: Int, labels: List[String])

  private object BuildInfo {
    given Encoder[BuildInfo] = Encoder.forProduct3("name", "number", "labels") { buildInfo =>
      (buildInfo.name, buildInfo.number, buildInfo.labels)
    }

    given Decoder[BuildInfo] = Decoder.forProduct3("name", "number", "labels")(BuildInfo.apply)
  }

  private final case class MetricKey(namespace: String, name: String)

  private object MetricKey {
    given KeyEncoder[MetricKey] = KeyEncoder.instance { key =>
      s"${key.namespace}.${key.name}"
    }
  }

  @Test
  def buildsStaticObjectLiteralsWithNestedArraysObjectsAndScalars(): Unit = {
    val document: Json = json"""
      {
        "service": "literal",
        "enabled": true,
        "attempts": 3,
        "ratio": -12.50,
        "missing": null,
        "tags": ["json", "macro", "scala3"],
        "nested": {
          "emptyObject": {},
          "emptyArray": [],
          "flags": [true, false, null]
        }
      }
      """
    val cursor = document.hcursor

    assertTrue(document.isObject)
    assertEquals("literal", expectRight(cursor.get[String]("service")))
    assertTrue(expectRight(cursor.get[Boolean]("enabled")))
    assertEquals(3, expectRight(cursor.get[Int]("attempts")))
    assertEquals(BigDecimal("-12.50"), expectRight(cursor.get[BigDecimal]("ratio")))
    assertTrue(cursor.downField("missing").focus.exists(_.isNull))
    assertEquals(List("json", "macro", "scala3"), expectRight(cursor.get[List[String]]("tags")))
    assertTrue(cursor.downField("nested").downField("emptyObject").focus.exists(_.isObject))
    assertEquals(List(Some(true), Some(false), None), expectRight(cursor.downField("nested").get[List[Option[Boolean]]]("flags")))
  }

  @Test
  def buildsTopLevelScalarAndArrayLiterals(): Unit = {
    val nullJson: Json = json""" null """
    val trueJson: Json = json""" true """
    val stringJson: Json = json""" "standalone" """
    val numberJson: Json = json""" 9007199254740993 """
    val decimalJson: Json = json""" 6.022e23 """
    val arrayJson: Json = json""" [1, "two", false, null, {"five": 5}] """

    assertTrue(nullJson.isNull)
    assertEquals(Some(true), trueJson.asBoolean)
    assertEquals(Some("standalone"), stringJson.asString)
    assertEquals(Some(BigDecimal("9007199254740993")), numberJson.asNumber.flatMap(_.toBigDecimal))
    assertEquals(Some(BigDecimal("6.022E+23")), decimalJson.asNumber.flatMap(_.toBigDecimal))
    assertEquals(5, expectRight(arrayJson.hcursor.downN(4).get[Int]("five")))
  }

  @Test
  def preservesEscapedStringsUnicodeAndPreciseNumbersInLiteralJson(): Unit = {
    val document: Json = json"""
      {
        "text": "line\nsnowman \u2603 quote \" slash / tab\t",
        "small": -0.000001,
        "largeDecimal": 12345678901234567890.12345,
        "exponent": -1.25e2
      }
      """
    val cursor = document.hcursor

    assertEquals("line\nsnowman ☃ quote \" slash / tab\t", expectRight(cursor.get[String]("text")))
    assertEquals(BigDecimal("-0.000001"), expectRight(cursor.get[BigDecimal]("small")))
    assertEquals(BigDecimal("12345678901234567890.12345"), expectRight(cursor.get[BigDecimal]("largeDecimal")))
    assertEquals(BigDecimal("-125"), expectRight(cursor.get[BigDecimal]("exponent")))
  }

  @Test
  def interpolatesValuesThroughCirceEncodersInObjectsArraysAndTopLevelPositions(): Unit = {
    val build: BuildInfo = BuildInfo("native-image", 42, List("fast", "deterministic"))
    val ids: List[Int] = List(1, 3, 5)
    val optionalOwner: Option[String] = Some("ci")
    val absent: Option[String] = None
    val dynamicText: String = "quote \" newline\n snowman ☃"
    val rawJson: Json = json"""{"alreadyJson": [true, false]}"""

    val document: Json = json"""
      {
        "build": $build,
        "ids": $ids,
        "owner": $optionalOwner,
        "absent": $absent,
        "message": $dynamicText,
        "raw": $rawJson
      }
      """
    val cursor = document.hcursor

    assertEquals(build, expectRight(cursor.get[BuildInfo]("build")))
    assertEquals(ids, expectRight(cursor.get[List[Int]]("ids")))
    assertEquals(optionalOwner, expectRight(cursor.get[Option[String]]("owner")))
    assertEquals(None, expectRight(cursor.get[Option[String]]("absent")))
    assertEquals(dynamicText, expectRight(cursor.get[String]("message")))
    assertEquals(List(true, false), expectRight(cursor.downField("raw").get[List[Boolean]]("alreadyJson")))
  }

  @Test
  def interpolatesJsonObjectKeysThroughKeyEncoders(): Unit = {
    val requestCount: MetricKey = MetricKey("http", "requests")
    val errorCount: MetricKey = MetricKey("http", "errors")
    val nestedKey: MetricKey = MetricKey("cache", "hits")
    val requestValue: Int = 200
    val errorValue: Int = 3
    val nestedValue: Long = 9876543210L

    val metrics: Json = json"""
      {
        $requestCount: $requestValue,
        $errorCount: $errorValue,
        "nested": {
          $nestedKey: $nestedValue
        }
      }
      """
    val cursor = metrics.hcursor

    assertEquals(requestValue, expectRight(cursor.get[Int]("http.requests")))
    assertEquals(errorValue, expectRight(cursor.get[Int]("http.errors")))
    assertEquals(nestedValue, expectRight(cursor.downField("nested").get[Long]("cache.hits")))
    assertFalse(cursor.downField("requestCount").succeeded)
  }

  @Test
  def mixesLiteralAndInterpolatedValuesInsideNestedCollections(): Unit = {
    val firstBuild: BuildInfo = BuildInfo("jvm", 1, List("baseline"))
    val secondBuild: BuildInfo = BuildInfo("native", 2, List("graalvm", "release"))
    val enabled: Boolean = true
    val threshold: BigDecimal = BigDecimal("0.875")

    val document: Json = json"""
      {
        "matrix": [
          {"kind": "literal", "build": $firstBuild, "enabled": $enabled},
          {"kind": "interpolated", "build": $secondBuild, "threshold": $threshold}
        ],
        "summary": {
          "names": [${firstBuild.name}, ${secondBuild.name}],
          "total": ${firstBuild.number + secondBuild.number}
        }
      }
      """
    val cursor = document.hcursor

    assertEquals(firstBuild, expectRight(cursor.downField("matrix").downN(0).get[BuildInfo]("build")))
    assertTrue(expectRight(cursor.downField("matrix").downN(0).get[Boolean]("enabled")))
    assertEquals(secondBuild, expectRight(cursor.downField("matrix").downN(1).get[BuildInfo]("build")))
    assertEquals(threshold, expectRight(cursor.downField("matrix").downN(1).get[BigDecimal]("threshold")))
    assertEquals(List("jvm", "native"), expectRight(cursor.downField("summary").get[List[String]]("names")))
    assertEquals(3, expectRight(cursor.downField("summary").get[Int]("total")))
  }

  @Test
  def encodesInterpolatedValuesAsTheEntireTopLevelJsonDocument(): Unit = {
    val build: BuildInfo = BuildInfo("top-level", 11, List("single", "document"))
    val numbers: Vector[Int] = Vector(2, 4, 8)
    val missingValue: Option[String] = None

    val buildJson: Json = json"""$build"""
    val numbersJson: Json = json"""$numbers"""
    val missingJson: Json = json"""$missingValue"""

    assertEquals(build, expectRight(buildJson.as[BuildInfo]))
    assertEquals(numbers, expectRight(numbersJson.as[Vector[Int]]))
    assertTrue(missingJson.isNull)
  }

  @Test
  def producesRegularCirceJsonThatCanBeTransformedDecodedAndPrinted(): Unit = {
    val input: Json = json"""
      {
        "build": {"name": "agent", "number": 7, "labels": ["generated", "literal"]},
        "removeMe": null,
        "items": [{"id": 1}, {"id": 2}]
      }
      """

    val transformed: Json = input.deepDropNullValues.mapObject { jsonObject =>
      jsonObject.add("status", Json.fromString("ready"))
    }
    val decoded: BuildInfo = expectRight(transformed.hcursor.get[BuildInfo]("build"))
    val itemIds: List[Int] = expectRight(transformed.hcursor.downField("items").as[List[Json]]).map { item =>
      expectRight(item.hcursor.get[Int]("id"))
    }
    val printed: String = Printer.spaces2SortKeys.print(transformed)

    assertEquals(BuildInfo("agent", 7, List("generated", "literal")), decoded)
    assertEquals(List(1, 2), itemIds)
    assertFalse(transformed.hcursor.downField("removeMe").succeeded)
    assertTrue(printed.contains("\n  \"build\" : {"))
    assertTrue(transformed.noSpacesSortKeys.contains("\"status\":\"ready\""))
  }

  private def expectRight[A](result: Either[?, A]): A = {
    result match {
      case Right(value) => value
      case Left(error) => fail[A](s"Expected successful result, but got: $error")
    }
  }
}
