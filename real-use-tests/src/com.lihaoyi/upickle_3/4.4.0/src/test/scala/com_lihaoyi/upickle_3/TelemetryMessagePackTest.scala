/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lihaoyi.upickle_3

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import upickle.default.*

/**
 * MessagePack binary serialization through upickle's writeBinary, readBinary and the upack
 * message tree, as an edge telemetry agent spools sensor batches to disk and a collector reads
 * them back, inspects them and forwards them as JSON.
 */
class TelemetryMessagePackTest {

  import TelemetryMessagePackTest.*

  @TempDir
  var spoolDir: Path = null

  @Test
  def spooledBatchRoundTripsThroughMessagePackFile(): Unit = {
    val batch: TelemetryBatch = sampleBatch()
    val spoolFile: Path = spoolDir.resolve("batch-0001.msgpack")

    val out: ByteArrayOutputStream = ByteArrayOutputStream()
    writeBinaryTo(batch, out)
    Files.write(spoolFile, out.toByteArray)

    val bytes: Array[Byte] = Files.readAllBytes(spoolFile)
    val restored: TelemetryBatch = readBinary[TelemetryBatch](bytes)

    assertEquals(batch.deviceId, restored.deviceId)
    assertEquals(batch.readings, restored.readings)
    assertArrayEquals(batch.firmwareDigest, restored.firmwareDigest)
    assertEquals(batch.labels, restored.labels)
    assertArrayEquals(writeBinary(batch), bytes)
  }

  @Test
  def binaryEncodingIsMoreCompactThanJson(): Unit = {
    val batch: TelemetryBatch = sampleBatch()

    val binary: Array[Byte] = writeBinary(batch)
    val json: String = write(batch)

    assertTrue(binary.length < json.getBytes("UTF-8").length, s"${binary.length} vs ${json.length}")
    assertEquals(0x80, binary(0) & 0xf0, "a case class is written as a MessagePack fixmap")
  }

  @Test
  def collectorInspectsTheMessageTreeWithoutTypedModels(): Unit = {
    val bytes: Array[Byte] = writeBinary(sampleBatch())

    val msg: upack.Msg = upack.read(bytes)
    val fields: upickle.core.LinkedHashMap[upack.Msg, upack.Msg] = msg.obj

    assertEquals(upack.Str("edge-7"), fields(upack.Str("deviceId")))
    val readings: collection.mutable.ArrayBuffer[upack.Msg] = fields(upack.Str("readings")).arr
    assertEquals(3, readings.size)
    assertEquals(upack.Str("humidity"), readings(2).obj(upack.Str("sensor")))
    val digest: upack.Msg = fields(upack.Str("firmwareDigest"))
    assertTrue(digest.isInstanceOf[upack.Binary], digest.toString)
    assertArrayEquals(Array[Byte](0xde.toByte, 0xad.toByte, 0xbe.toByte, 0xef.toByte), digest.binary)

    assertArrayEquals(bytes, upack.write(msg))
  }

  @Test
  def collectorForwardsBatchAsJsonForTheDashboard(): Unit = {
    val bytes: Array[Byte] = writeBinary(sampleBatch())

    val forwarded: ujson.Value = upack.transform(bytes, ujson.Value)

    assertEquals("edge-7", forwarded("deviceId").str)
    assertEquals(21.5, forwarded("readings")(0)("value").num)
    assertEquals(1_700_000_000_000L.toDouble, forwarded("readings")(0)("epochMillis").num)
    assertEquals("lab", forwarded("labels")("site").str)
  }

  @Test
  def handBuiltMessageIsReadIntoTypedReading(): Unit = {
    val msg: upack.Msg = upack.Obj(
      upack.Str("sensor") -> upack.Str("pressure"),
      upack.Str("epochMillis") -> upack.Int64(1_700_000_123_456L),
      upack.Str("value") -> upack.Float64(1013.25)
    )

    val reading: Reading = readBinary[Reading](upack.write(msg))

    assertEquals(Reading("pressure", 1_700_000_123_456L, 1013.25), reading)
    assertEquals(msg, writeMsg(reading))
  }
}

object TelemetryMessagePackTest {

  final case class Reading(sensor: String, epochMillis: Long, value: Double) derives ReadWriter

  final case class TelemetryBatch(
      deviceId: String,
      readings: Seq[Reading],
      firmwareDigest: Array[Byte],
      labels: Map[String, String]
  ) derives ReadWriter

  def sampleBatch(): TelemetryBatch = TelemetryBatch(
    deviceId = "edge-7",
    readings = Seq(
      Reading("temperature", 1_700_000_000_000L, 21.5),
      Reading("temperature", 1_700_000_060_000L, 21.75),
      Reading("humidity", 1_700_000_060_000L, 48.0)
    ),
    firmwareDigest = Array[Byte](0xde.toByte, 0xad.toByte, 0xbe.toByte, 0xef.toByte),
    labels = Map("site" -> "lab", "rack" -> "r12")
  )
}
