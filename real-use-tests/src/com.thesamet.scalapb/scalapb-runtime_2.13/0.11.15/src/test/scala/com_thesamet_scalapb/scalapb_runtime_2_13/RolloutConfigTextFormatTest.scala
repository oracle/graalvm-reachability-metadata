/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thesamet_scalapb.scalapb_runtime_2_13

import com.google.protobuf.duration.Duration
import com.google.protobuf.struct.ListValue
import com.google.protobuf.struct.Struct
import com.google.protobuf.struct.Value
import com.google.protobuf.timestamp.Timestamp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import scalapb.TextFormat
import scalapb.TextFormatError
import scalapb.TextFormatException

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Exercises the protobuf text format: `TextFormat`, `fromAscii`, `validateAscii` and
 * `toProtoString` print generated messages as human-editable text and parse them back.
 *
 * A deployment tool keeps each service's rollout settings in a text-format file checked into
 * the repository, so operators can review and edit them by hand. The tool loads the file,
 * rejects edits that do not parse with a message pointing at the mistake, rewrites the file in
 * canonical form, and logs a one-line summary of what it is about to roll out.
 */
class RolloutConfigTextFormatTest {
  @Test
  def handEditedRolloutFileLoadsIntoTypedSettings(@TempDir workspace: Path): Unit = {
    val file: Path = workspace.resolve("checkout.rollout.txtpb")
    Files.write(file, (
      """# Rollout settings for the checkout service, edited by hand.
        |fields {
        |  key: "service"
        |  value { string_value: "checkout" }
        |}
        |fields {
        |  key: "canary_percent"
        |  value { number_value: 12.5 }
        |}
        |fields {
        |  key: "regions"
        |  value {
        |    list_value {
        |      values { string_value: "eu-west-1" }
        |      values { string_value: "us-east-2" }
        |    }
        |  }
        |}
        |fields { key: "paused" value { bool_value: false } }
        |fields { key: "owner" value { null_value: NULL_VALUE } }
        |""".stripMargin).getBytes(StandardCharsets.UTF_8))

    val settings: RolloutSettings = RolloutStore.load(file)

    assertEquals("checkout", settings.service)
    assertEquals(12.5, settings.canaryPercent)
    assertEquals(List("eu-west-1", "us-east-2"), settings.regions)
    assertFalse(settings.paused)
    assertTrue(settings.raw.fields("owner").kind.isNullValue)
  }

  @Test
  def canonicalRewriteRoundTripsAndEscapesNonAsciiOnlyWhenAsked(@TempDir workspace: Path): Unit = {
    val config: Struct = Struct(fields = Map(
      "service" -> Value(Value.Kind.StringValue("caf\u00e9-menu")),
      "canary_percent" -> Value(Value.Kind.NumberValue(5)),
      "regions" -> Value(Value.Kind.ListValue(ListValue(Seq(Value(Value.Kind.StringValue("ap-south-1")))))),
      "paused" -> Value(Value.Kind.BoolValue(true))
    ))

    val file: Path = workspace.resolve("menu.rollout.txtpb")
    RolloutStore.save(file, config)
    val written: String = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
    assertTrue(written.contains("string_value: \"caf\u00e9-menu\""), written)
    assertTrue(written.contains("bool_value: true"), written)
    assertTrue(written.linesIterator.size > 1, written)
    assertEquals(config, RolloutStore.load(file).raw)

    val escaped: String = TextFormat.printToString(config)
    assertFalse(escaped.exists(_ > '\u007f'), escaped)
    assertTrue(escaped.contains("caf\\303\\251-menu"), escaped)
    assertEquals(config, Struct.fromAscii(escaped))
    assertEquals(config.toProtoString, TextFormat.printToUnicodeString(config))
  }

  @Test
  def deploymentLogCarriesOneLineSummaryOfScheduleAndWindow(): Unit = {
    val startsAt: Timestamp = Timestamp(seconds = 1760400000L, nanos = 250000000)
    val bakeTime: Duration = Duration(seconds = 1800L, nanos = 500000000)

    val startLine: String = TextFormat.printToSingleLineUnicodeString(startsAt)
    val bakeLine: String = TextFormat.printToSingleLineUnicodeString(bakeTime)
    assertFalse(startLine.contains("\n"))
    assertEquals("seconds: 1760400000 nanos: 250000000", startLine.trim)
    assertEquals("seconds: 1800 nanos: 500000000", bakeLine.trim)

    assertEquals(startsAt, Timestamp.fromAscii(startLine))
    assertEquals(bakeTime, Duration.fromAscii(bakeLine))
  }

  @Test
  def brokenEditsAreRejectedWithAReasonBeforeAnythingRollsOut(@TempDir workspace: Path): Unit = {
    val unknownField: Either[TextFormatError, Struct] =
      Struct.validateAscii("""fields { key: "service" value { text_value: "checkout" } }""")
    assertTrue(unknownField.isLeft)
    assertTrue(unknownField.left.toOption.get.msg.contains("text_value"), unknownField.toString)

    val unclosed: Either[TextFormatError, Struct] =
      TextFormat.fromAscii(Struct, """fields { key: "service" value { string_value: "checkout" }""")
    assertTrue(unclosed.isLeft, unclosed.toString)

    val badEnum: Either[TextFormatError, Value] = Value.validateAscii("null_value: NOTHING")
    assertTrue(badEnum.isLeft, badEnum.toString)

    val file: Path = workspace.resolve("search.rollout.txtpb")
    Files.write(file, "fields { key: \"canary_percent\" value { number_value: lots } }"
      .getBytes(StandardCharsets.UTF_8))
    assertThrows(classOf[TextFormatException], () => RolloutStore.load(file))
  }

  /** The settings the deployment tool acts on, read out of the text-format `Struct`. */
  final case class RolloutSettings(
      service: String,
      canaryPercent: Double,
      regions: List[String],
      paused: Boolean,
      raw: Struct
  )

  /** Reads and writes rollout files in the protobuf text format. */
  object RolloutStore {
    def load(file: Path): RolloutSettings = {
      val text: String = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
      val raw: Struct = Struct.fromAscii(text)
      RolloutSettings(
        service = raw.fields("service").getStringValue,
        canaryPercent = raw.fields("canary_percent").getNumberValue,
        regions = raw.fields("regions").getListValue.values.map(_.getStringValue).toList,
        paused = raw.fields("paused").getBoolValue,
        raw = raw
      )
    }

    def save(file: Path, config: Struct): Unit =
      Files.write(file, config.toProtoString.getBytes(StandardCharsets.UTF_8))
  }
}
