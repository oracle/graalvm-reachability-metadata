/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_plokhotnyuk_jsoniter_scala.jsoniter_scala_core_3

import com.github.plokhotnyuk.jsoniter_scala.core.JsonReaderException
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.core.ReaderConfig
import com.github.plokhotnyuk.jsoniter_scala.core.readFromString
import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.lang.Double.doubleToLongBits
import java.lang.Float.floatToIntBits

import DeviceMetricsPrimitivesTest.*

/**
 * Primitive scalar values: shortest round-trip `Float` and `Double` formatting and parsing,
 * fixed-width `Byte`/`Short`/`Int`/`Long` range checks, `Char` and `Boolean` values, and maps
 * keyed by primitives: an IoT hub ingests device metric samples and per-channel histograms,
 * rejecting readings that do not fit the declared field widths.
 */
class DeviceMetricsPrimitivesTest {

  private val diagnostics: ReaderConfig = ReaderConfig.withAppendHexDumpToParseException(false)

  private val sample: Sample = Sample(
    signal = -73.toByte,
    firmware = 1203.toShort,
    uptime = 2147483647,
    sequence = -9223372036854775808L,
    voltage = 3.3f,
    temperature = 0.1 + 0.2,
    grade = 'B',
    charging = true
  )

  @Test
  def samplesUseTheShortestDigitsThatRoundTrip(): Unit = {
    val json: String = writeToString(sample)

    assertEquals(
      """{"signal":-73,"firmware":1203,"uptime":2147483647,"sequence":-9223372036854775808,""" +
        """"voltage":3.3,"temperature":0.30000000000000004,"grade":"B","charging":true}""",
      json
    )
    val decoded: Sample = readFromString[Sample](json)
    assertEquals(sample, decoded)
    assertEquals(floatToIntBits(3.3f), floatToIntBits(decoded.voltage))
  }

  @Test
  def extremeFloatingPointValuesKeepTheirExactBits(): Unit = {
    val extremes: List[Sample] = List(
      sample.copy(voltage = Float.MaxValue, temperature = Double.MaxValue),
      sample.copy(voltage = Float.MinPositiveValue, temperature = Double.MinPositiveValue),
      sample.copy(voltage = 1.0e-7f, temperature = 1.0e23)
    )

    for (extreme <- extremes) {
      val decoded: Sample = readFromString[Sample](writeToString(extreme))
      assertEquals(floatToIntBits(extreme.voltage), floatToIntBits(decoded.voltage))
      assertEquals(
        doubleToLongBits(extreme.temperature),
        doubleToLongBits(decoded.temperature)
      )
    }
    val maxJson: String = writeToString(extremes.head)
    assertTrue(maxJson.contains(""""voltage":3.4028235E38"""), maxJson)
    assertTrue(maxJson.contains(""""temperature":1.7976931348623157E308"""), maxJson)
  }

  @Test
  def exponentAndFractionNotationsAreParsedIntoFloatingPointFields(): Unit = {
    val json: String =
      """{"signal":0,"firmware":0,"uptime":0,"sequence":0,"voltage":33E-1,""" +
        """"temperature":-2.5e+2,"grade":"A","charging":false}"""

    val decoded: Sample = readFromString[Sample](json)

    assertEquals(3.3f, decoded.voltage)
    assertEquals(-250.0, decoded.temperature)
    assertEquals('A', decoded.grade)
    assertEquals(false, decoded.charging)
  }

  @Test
  def readingsOutsideTheFieldWidthAreRejected(): Unit = {
    def assertRejected(field: String, value: String, expectedPrefix: String): Unit = {
      val json: String = writeToString(sample).replaceFirst(s""""$field":[^,}]+""", s""""$field":$value""")
      val message: String =
        assertThrows(classOf[JsonReaderException], () => readFromString[Sample](json, diagnostics)).getMessage
      assertTrue(message.startsWith(expectedPrefix), message)
    }

    assertRejected("signal", "128", "value is too large for byte")
    assertRejected("firmware", "-32769", "value is too large for short")
    assertRejected("uptime", "2147483648", "value is too large for int")
    assertRejected("sequence", "9223372036854775808", "value is too large for long")
    assertRejected("uptime", "1.5", "illegal number")
    assertRejected("uptime", "007", "illegal number with leading zero")
    assertRejected("charging", "\"true\"", "illegal boolean")
    assertRejected("grade", "\"AB\"", "expected '\"'")
  }

  @Test
  def histogramsAreKeyedByPrimitiveBucketsAndChannels(): Unit = {
    val histogram: Histogram = Histogram(
      byBucket = Map(-40 -> 2L, 0 -> 17L, 25 -> 4096L),
      byChannel = Map('x' -> 0.5f, 'y' -> -1.25f),
      thresholds = Map(1.5 -> true, 1.0e-3 -> false),
      samples = Array(1, -1, 127, -128).map(_.toByte)
    )

    val json: String = writeToString(histogram)

    assertEquals(
      """{"byBucket":{"-40":2,"0":17,"25":4096},"byChannel":{"x":0.5,"y":-1.25},""" +
        """"thresholds":{"1.5":true,"0.001":false},"samples":[1,-1,127,-128]}""",
      json
    )
    val decoded: Histogram = readFromString[Histogram](json)
    assertEquals(histogram.byBucket, decoded.byBucket)
    assertEquals(histogram.byChannel, decoded.byChannel)
    assertEquals(histogram.thresholds, decoded.thresholds)
    assertEquals(histogram.samples.toList, decoded.samples.toList)

    val namedBucket: String = """{"byBucket":{"warm":1},"byChannel":{},"thresholds":{},"samples":[]}"""
    val badKey: JsonReaderException =
      assertThrows(classOf[JsonReaderException], () => readFromString[Histogram](namedBucket, diagnostics))
    assertTrue(badKey.getMessage.startsWith("illegal number"), badKey.getMessage)
  }
}

object DeviceMetricsPrimitivesTest {

  /** One metric sample, with each reading stored in the narrowest type its sensor needs. */
  final case class Sample(
    signal: Byte,
    firmware: Short,
    uptime: Int,
    sequence: Long,
    voltage: Float,
    temperature: Double,
    grade: Char,
    charging: Boolean
  )

  /** Counts per temperature bucket, gains per axis, alerting thresholds and raw signed samples. */
  final case class Histogram(
    byBucket: Map[Int, Long],
    byChannel: Map[Char, Float],
    thresholds: Map[Double, Boolean],
    samples: Array[Byte]
  )

  given sampleCodec: JsonValueCodec[Sample] = JsonCodecMaker.make

  given histogramCodec: JsonValueCodec[Histogram] = JsonCodecMaker.make
}
