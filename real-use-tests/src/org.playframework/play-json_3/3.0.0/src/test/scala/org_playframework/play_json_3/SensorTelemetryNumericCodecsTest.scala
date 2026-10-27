/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_json_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import play.api.libs.json.JsError
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.json.JsPath
import play.api.libs.json.JsResult
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.libs.json.Reads

import java.math.BigInteger

/**
 * Built-in numeric scalar codecs: `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `BigInt`,
 * `java.math.BigInteger` and `BigDecimal` Reads and Writes with their range checks and their
 * acceptance of string-encoded big numbers.
 *
 * A telemetry gateway ingests sensor readings whose fields use the narrowest numeric type that
 * fits: signal bars as a byte, temperature in tenths of a degree as a short, a cumulative pulse
 * counter that outgrows `Long`, and an energy meter reading kept exact as a decimal. Readings
 * round-trip through JSON without losing precision, legacy firmware that sends big numbers as
 * strings is accepted, and values that overflow their field type are rejected at their path.
 */
class SensorTelemetryNumericCodecsTest {
  import SensorTelemetryNumericCodecsTest.*

  @Test
  def readingsRoundTripWithExactNumbers(): Unit = {
    val reading: SensorReading = SensorReading(
      sensorId = "meter-17",
      signalBars = 4.toByte,
      temperatureTenths = (-125).toShort,
      batteryPercent = 87,
      capturedAtMillis = 1767225600123L,
      humidity = 41.5f,
      voltage = 229.875d,
      pulseCount = BigInt("123456789012345678901234567890"),
      energyKwh = BigDecimal("98765.000123456789")
    )

    val json: JsValue = Json.toJson(reading)

    assertEquals(JsNumber(4), (json \ "signalBars").get)
    assertEquals(JsNumber(-125), (json \ "temperatureTenths").get)
    assertEquals(JsNumber(1767225600123L), (json \ "capturedAtMillis").get)
    assertEquals(JsNumber(BigDecimal("41.5")), (json \ "humidity").get)
    assertEquals(JsNumber(BigDecimal("229.875")), (json \ "voltage").get)
    assertEquals(
      JsNumber(BigDecimal("123456789012345678901234567890")),
      (json \ "pulseCount").get
    )
    assertEquals(JsNumber(BigDecimal("98765.000123456789")), (json \ "energyKwh").get)

    val parsed: JsResult[SensorReading] = Json.parse(Json.stringify(json)).validate[SensorReading]
    assertEquals(JsSuccess(reading), parsed)
  }

  @Test
  def legacyFirmwareSendsBigNumbersAsStrings(): Unit = {
    val payload: JsValue = Json.parse(
      """{
        |  "sensorId": "meter-03",
        |  "signalBars": 2,
        |  "temperatureTenths": 215,
        |  "batteryPercent": 100.0,
        |  "capturedAtMillis": 1767225600000,
        |  "humidity": 60,
        |  "voltage": 230,
        |  "pulseCount": "18446744073709551616",
        |  "energyKwh": "12.50"
        |}""".stripMargin
    )

    val reading: SensorReading = payload.as[SensorReading]

    assertEquals(100, reading.batteryPercent)
    assertEquals(60.0f, reading.humidity)
    assertEquals(230.0d, reading.voltage)
    assertEquals(BigInt(2).pow(64), reading.pulseCount)
    assertEquals(BigDecimal("12.50"), reading.energyKwh)
    assertEquals(2, reading.energyKwh.scale)

    val counter: BigInteger = JsString("9223372036854775808").as[BigInteger]
    assertEquals(BigInteger.valueOf(Long.MaxValue).add(BigInteger.ONE), counter)
    assertEquals(JsNumber(BigDecimal("9223372036854775808")), Json.toJson(counter))
  }

  @Test
  def valuesThatOverflowTheirFieldTypeAreRejectedAtTheirPath(): Unit = {
    val payload: JsObject = Json.obj(
      "sensorId" -> "meter-99",
      "signalBars" -> 200,
      "temperatureTenths" -> 40000,
      "batteryPercent" -> 87.5,
      "capturedAtMillis" -> BigDecimal("9223372036854775808"),
      "humidity" -> "high",
      "voltage" -> true,
      "pulseCount" -> "12x4",
      "energyKwh" -> "twelve"
    )

    val errors: Map[String, Seq[String]] = payload.validate[SensorReading] match {
      case JsError(failures) =>
        failures.map { case (path, details) =>
          path.toString -> details.flatMap(_.messages).toSeq
        }.toMap
      case success => throw new AssertionError(s"Expected rejection, got $success")
    }

    assertEquals(Seq("error.expected.byte"), errors("/signalBars"))
    assertEquals(Seq("error.expected.short"), errors("/temperatureTenths"))
    assertEquals(Seq("error.expected.int"), errors("/batteryPercent"))
    assertEquals(Seq("error.expected.long"), errors("/capturedAtMillis"))
    assertEquals(Seq("error.expected.jsnumber"), errors("/humidity"))
    assertEquals(Seq("error.expected.jsnumber"), errors("/voltage"))
    assertEquals(Seq("error.expected.numberformatexception"), errors("/pulseCount"))
    assertEquals(Seq("error.expected.numberformatexception"), errors("/energyKwh"))
    assertEquals(8, errors.size)
  }

  @Test
  def standaloneScalarReadsApplyTheSameBounds(): Unit = {
    assertEquals(JsSuccess(Byte.MaxValue), JsNumber(127).validate[Byte])
    assertTrue(JsNumber(128).validate[Byte].isError)
    assertEquals(JsSuccess(Short.MinValue), JsNumber(-32768).validate[Short])
    assertEquals(JsSuccess(Int.MaxValue), JsNumber(Int.MaxValue).validate[Int])
    assertTrue(JsNumber(BigDecimal(Int.MaxValue) + 1).validate[Int].isError)
    assertEquals(JsSuccess(Long.MinValue), JsNumber(Long.MinValue).validate[Long])

    val bigIntReads: Reads[BigInt] = summon[Reads[BigInt]]
    assertEquals(JsSuccess(BigInt(42)), bigIntReads.reads(JsNumber(42)))
    assertTrue(bigIntReads.reads(JsNumber(BigDecimal("42.5"))).isError)
    assertEquals(
      JsError(JsPath, "error.expected.jsnumberorjsstring"),
      bigIntReads.reads(Json.arr(1, 2))
    )
  }
}

object SensorTelemetryNumericCodecsTest {
  final case class SensorReading(
    sensorId: String,
    signalBars: Byte,
    temperatureTenths: Short,
    batteryPercent: Int,
    capturedAtMillis: Long,
    humidity: Float,
    voltage: Double,
    pulseCount: BigInt,
    energyKwh: BigDecimal
  )

  implicit val sensorReadingFormat: OFormat[SensorReading] = Json.format[SensorReading]
}
