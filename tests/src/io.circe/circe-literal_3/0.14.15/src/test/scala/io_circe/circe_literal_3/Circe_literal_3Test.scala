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
import io.circe.JsonNumber
import io.circe.KeyDecoder
import io.circe.KeyEncoder
import io.circe.literal._
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

import java.util.UUID
import scala.util.Try

final case class ProjectId(value: String)

object ProjectId {
  given Encoder[ProjectId] = Encoder.encodeString.contramap(_.value)

  given Decoder[ProjectId] = Decoder.decodeString.map(ProjectId.apply)

  given KeyEncoder[ProjectId] = KeyEncoder.instance(_.value)

  given KeyDecoder[ProjectId] = KeyDecoder.instance(key => Some(ProjectId(key)))
}

final case class BuildMetric(name: String, count: Int, ratio: BigDecimal, passed: Boolean)

object BuildMetric {
  given Encoder[BuildMetric] = Encoder.forProduct4("name", "count", "ratio", "passed") { metric =>
    (metric.name, metric.count, metric.ratio, metric.passed)
  }

  given Decoder[BuildMetric] = Decoder.forProduct4("name", "count", "ratio", "passed")(BuildMetric.apply)
}

final case class ReleaseNote(title: String, labels: List[String], metadata: Map[ProjectId, BuildMetric])

object ReleaseNote {
  given Encoder[ReleaseNote] = Encoder.forProduct3("title", "labels", "metadata") { note =>
    (note.title, note.labels, note.metadata)
  }

  given Decoder[ReleaseNote] = Decoder.forProduct3("title", "labels", "metadata")(ReleaseNote.apply)
}

final class Circe_literal_3Test {
  @Test
  def buildsNestedLiteralDocumentsWithAllJsonValueKinds(): Unit = {
    val document: Json = json"""
      {
        "name": "circe-literal",
        "active": true,
        "missing": null,
        "counts": [0, 1, -2, 3000000000],
        "nested": {
          "escaped": "line\nbreak and snowman \u2603",
          "decimal": 12345678901234567890.125,
          "exponent": -1.25e2
        }
      }
      """

    assertThat(document.isObject).isTrue
    assertThat(document.hcursor.get[String]("name")).isEqualTo(Right("circe-literal"))
    assertThat(document.hcursor.get[Boolean]("active")).isEqualTo(Right(true))
    assertThat(document.hcursor.downField("missing").focus.exists(_.isNull)).isTrue
    assertThat(document.hcursor.get[List[Long]]("counts")).isEqualTo(Right(List(0L, 1L, -2L, 3000000000L)))
    assertThat(document.hcursor.downField("nested").get[String]("escaped"))
      .isEqualTo(Right("line\nbreak and snowman ☃"))
    assertThat(document.hcursor.downField("nested").get[BigDecimal]("decimal"))
      .isEqualTo(Right(BigDecimal("12345678901234567890.125")))
    assertThat(document.hcursor.downField("nested").get[BigDecimal]("exponent"))
      .isEqualTo(Right(BigDecimal("-125")))
    assertThat(document.noSpacesSortKeys)
      .contains("\"decimal\":12345678901234567890.125")
  }

  @Test
  def buildsTopLevelScalarArrayAndObjectLiterals(): Unit = {
    val nullValue: Json = json"null"
    val booleanValue: Json = json"true"
    val stringValue: Json = json""" "standalone" """
    val numberValue: Json = json"42.125"
    val arrayValue: Json = json"""[1, { "two": 2 }, false]"""
    val objectValue: Json = json"""{ "outer": { "inner": ["value"] } }"""

    assertThat(nullValue.isNull).isTrue
    assertThat(booleanValue.asBoolean).isEqualTo(Some(true))
    assertThat(stringValue.asString).isEqualTo(Some("standalone"))
    assertThat(numberValue.asNumber.flatMap(_.toBigDecimal)).isEqualTo(Some(BigDecimal("42.125")))
    assertThat(arrayValue.hcursor.downN(1).get[Int]("two")).isEqualTo(Right(2))
    assertThat(objectValue.hcursor.downField("outer").downField("inner").as[List[String]])
      .isEqualTo(Right(List("value")))
  }

  @Test
  def interpolatesValuesUsingCirceEncoders(): Unit = {
    val title: String = "native-image metadata"
    val successful: Boolean = true
    val attempts: Int = 3
    val ratio: BigDecimal = BigDecimal("98.75")
    val labels: List[String] = List("scala-3", "macro", "json")
    val metric: BuildMetric = BuildMetric("coverage", 17, BigDecimal("0.875"), passed = true)
    val optionalOwner: Option[String] = Some("forge")
    val absentValue: Option[String] = None

    val document: Json = json"""
      {
        "title": $title,
        "successful": $successful,
        "attempts": $attempts,
        "ratio": $ratio,
        "labels": $labels,
        "metric": $metric,
        "owner": $optionalOwner,
        "absent": $absentValue
      }
      """

    val cursor = document.hcursor
    assertThat(cursor.get[String]("title")).isEqualTo(Right(title))
    assertThat(cursor.get[Boolean]("successful")).isEqualTo(Right(successful))
    assertThat(cursor.get[Int]("attempts")).isEqualTo(Right(attempts))
    assertThat(cursor.get[BigDecimal]("ratio")).isEqualTo(Right(ratio))
    assertThat(cursor.get[List[String]]("labels")).isEqualTo(Right(labels))
    assertThat(cursor.get[BuildMetric]("metric")).isEqualTo(Right(metric))
    assertThat(cursor.get[Option[String]]("owner")).isEqualTo(Right(optionalOwner))
    assertThat(cursor.get[Option[String]]("absent")).isEqualTo(Right(None))
    assertThat(cursor.downField("absent").focus.exists(_.isNull)).isTrue
  }

  @Test
  def interpolatesTopLevelJsonAndDomainValues(): Unit = {
    val rawJson: Json = Json.obj(
      "source" -> Json.fromString("prebuilt"),
      "values" -> Json.arr(Json.fromInt(1), Json.fromInt(2))
    )
    val metric: BuildMetric = BuildMetric("warnings", 0, BigDecimal("1.0"), passed = true)

    val topLevelJson: Json = json"$rawJson"
    val topLevelDomain: Json = json"$metric"
    val wrapped: Json = json"""{ "raw": $rawJson, "metric": $metric }"""

    assertThat(topLevelJson).isEqualTo(rawJson)
    assertThat(topLevelDomain.as[BuildMetric]).isEqualTo(Right(metric))
    assertThat(wrapped.hcursor.downField("raw").get[List[Int]]("values")).isEqualTo(Right(List(1, 2)))
    assertThat(wrapped.hcursor.get[BuildMetric]("metric")).isEqualTo(Right(metric))
  }

  @Test
  def interpolatesObjectKeysUsingCirceKeyEncoders(): Unit = {
    val primary: ProjectId = ProjectId("runtime")
    val secondary: ProjectId = ProjectId("native")
    val primaryMetric: BuildMetric = BuildMetric("jvm", 12, BigDecimal("0.90"), passed = true)
    val secondaryMetric: BuildMetric = BuildMetric("image", 10, BigDecimal("0.80"), passed = true)

    val document: Json = json"""
      {
        $primary: $primaryMetric,
        $secondary: $secondaryMetric,
        "static": "kept"
      }
      """

    assertThat(document.hcursor.get[BuildMetric]("runtime")).isEqualTo(Right(primaryMetric))
    assertThat(document.hcursor.get[BuildMetric]("native")).isEqualTo(Right(secondaryMetric))
    assertThat(document.hcursor.get[String]("static")).isEqualTo(Right("kept"))
    assertThat(document.noSpacesSortKeys).contains("\"native\":")
    assertThat(document.noSpacesSortKeys).contains("\"runtime\":")
    assertThat(document.noSpacesSortKeys).contains("\"static\":\"kept\"")
  }

  @Test
  def preservesNestedProductRoundTripsWithInterpolatedKeysAndValues(): Unit = {
    val projectId: ProjectId = ProjectId("graalvm-reachability-metadata")
    val metric: BuildMetric = BuildMetric("dynamic-access", 64, BigDecimal("0.64"), passed = true)
    val note: ReleaseNote = ReleaseNote(
      title = "literal macro coverage",
      labels = List("json", "encoder", "key-encoder"),
      metadata = Map(projectId -> metric)
    )

    val document: Json = json"""
      {
        "note": $note,
        "metadataOnly": {
          $projectId: $metric
        }
      }
      """

    assertThat(document.hcursor.get[ReleaseNote]("note")).isEqualTo(Right(note))
    assertThat(document.hcursor.downField("note").downField("metadata").get[BuildMetric](projectId.value))
      .isEqualTo(Right(metric))
    assertThat(document.hcursor.downField("metadataOnly").as[Map[ProjectId, BuildMetric]])
      .isEqualTo(Right(Map(projectId -> metric)))
  }

  @Test
  def supportsJsonNumbersThatRemainUsableWithThePublicNumberApi(): Unit = {
    val integerLiteral: Json = json"9007199254740993"
    val decimalLiteral: Json = json"0.00000000000000000012345"
    val scientificLiteral: Json = json"6.02214076e23"

    assertThat(integerLiteral.as[BigInt]).isEqualTo(Right(BigInt("9007199254740993")))
    assertThat(decimalLiteral.asNumber.flatMap(_.toBigDecimal))
      .isEqualTo(Some(BigDecimal("0.00000000000000000012345")))
    assertThat(scientificLiteral.asNumber.flatMap(_.toBigDecimal))
      .isEqualTo(Some(BigDecimal("6.02214076e23")))

    val rebuilt: Json = Json.fromJsonNumber(
      JsonNumber.fromString("123456789012345678901234567890").getOrElse(fail("Expected valid JSON number"))
    )
    val combined: Json = json"""{ "literal": $integerLiteral, "rebuilt": $rebuilt }"""

    assertThat(combined.hcursor.get[BigInt]("literal")).isEqualTo(Right(BigInt("9007199254740993")))
    assertThat(combined.hcursor.get[BigInt]("rebuilt"))
      .isEqualTo(Right(BigInt("123456789012345678901234567890")))
  }

  @Test
  def composedLiteralDocumentsSupportCursorTransformationsAndDecodingFailures(): Unit = {
    val generatedId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000123")
    val metric: BuildMetric = BuildMetric("tests", 42, BigDecimal("1.00"), passed = true)
    val document: Json = json"""
      {
        "id": $generatedId,
        "items": [
          { "name": "first", "metric": $metric },
          { "name": "second", "metric": { "name": "broken", "count": "not-an-int", "ratio": 0.5, "passed": false } }
        ]
      }
      """

    val firstMetric = document.hcursor.downField("items").downArray.downField("metric").as[BuildMetric]
    val secondMetric = document.hcursor.downField("items").downN(1).downField("metric").as[BuildMetric]
    val renamed = document.hcursor.downField("items").downArray.downField("name")
      .withFocus(_.withString(value => Json.fromString(value.toUpperCase))).top

    assertThat(document.hcursor.get[UUID]("id")).isEqualTo(Right(generatedId))
    assertThat(firstMetric).isEqualTo(Right(metric))
    assertThat(secondMetric.isLeft).isTrue
    assertThat(renamed.getOrElse(fail("Expected cursor transformation to produce a top-level document"))
      .hcursor.downField("items").downArray.get[String]("name"))
      .isEqualTo(Right("FIRST"))
  }

  @Test
  def literalOutputCanBeDeepMergedAndPrintedDeterministically(): Unit = {
    val base: Json = json"""
      {
        "service": "metadata",
        "config": {
          "threads": 2,
          "debug": false
        },
        "removeMe": null
      }
      """
    val overrideJson: Json = json"""
      {
        "config": {
          "debug": true,
          "region": "eu"
        },
        "extra": ["literal", "merge"]
      }
      """

    val merged: Json = base.deepDropNullValues.deepMerge(overrideJson)

    assertThat(merged.noSpacesSortKeys)
      .isEqualTo("""{"config":{"debug":true,"region":"eu","threads":2},"extra":["literal","merge"],"service":"metadata"}""")
    assertThat(merged.spaces2SortKeys).contains("\n  \"config\" : {")
    assertThat(merged.hcursor.downField("removeMe").succeeded).isFalse
  }

  @Test
  def interpolatedCollectionsAndMapsDecodeBackToTheOriginalScalaValues(): Unit = {
    val projects: Map[ProjectId, List[BuildMetric]] = Map(
      ProjectId("core") -> List(BuildMetric("compile", 1, BigDecimal("1.0"), passed = true)),
      ProjectId("native") -> List(BuildMetric("image", 2, BigDecimal("0.5"), passed = false))
    )
    val lookupKeys: Set[ProjectId] = Set(ProjectId("core"), ProjectId("native"))
    val maybeProjects: Option[Map[ProjectId, List[BuildMetric]]] = Some(projects)

    val document: Json = json"""
      {
        "projects": $projects,
        "lookupKeys": $lookupKeys,
        "maybeProjects": $maybeProjects
      }
      """

    assertThat(document.hcursor.get[Map[ProjectId, List[BuildMetric]]]("projects")).isEqualTo(Right(projects))
    assertThat(document.hcursor.get[Set[ProjectId]]("lookupKeys")).isEqualTo(Right(lookupKeys))
    assertThat(document.hcursor.get[Option[Map[ProjectId, List[BuildMetric]]]]("maybeProjects"))
      .isEqualTo(Right(maybeProjects))
  }

  @Test
  def interpolatedKeyValuesAvoidStringEscapingAmbiguities(): Unit = {
    val escapedKey: ProjectId = ProjectId("key with spaces and \"quotes\"")
    val escapedValue: String = "value with newline\nquote \" and unicode ☃"

    val document: Json = json"""
      {
        $escapedKey: $escapedValue
      }
      """

    assertThat(document.hcursor.get[String](escapedKey.value)).isEqualTo(Right(escapedValue))
    assertThat(document.noSpaces).contains("\\\"quotes\\\"")
    assertThat(document.noSpaces).contains("value with newline\\nquote")
  }

  @Test
  def interpolatesValuesWhoseStaticTypesHaveCustomEncoders(): Unit = {
    val value: Try[Int] = Try(21)
    given Encoder[Try[Int]] = Encoder.instance {
      case scala.util.Success(number) =>
        Json.obj("status" -> Json.fromString("success"), "value" -> Json.fromInt(number))
      case scala.util.Failure(error) =>
        Json.obj("status" -> Json.fromString("failure"), "message" -> Json.fromString(error.getMessage))
    }

    val document: Json = json"""{ "result": $value }"""

    assertThat(document.hcursor.downField("result").get[String]("status")).isEqualTo(Right("success"))
    assertThat(document.hcursor.downField("result").get[Int]("value")).isEqualTo(Right(21))
  }
}
