/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_plokhotnyuk_jsoniter_scala.jsoniter_scala_core_3

import com.github.plokhotnyuk.jsoniter_scala.core.JsonCodec
import com.github.plokhotnyuk.jsoniter_scala.core.JsonReader
import com.github.plokhotnyuk.jsoniter_scala.core.JsonReaderException
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.core.JsonWriter
import com.github.plokhotnyuk.jsoniter_scala.core.readFromString
import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.time.Instant
import java.util.UUID

import scala.collection.mutable.ListBuffer

import SensorTelemetryCodecTest.*

/**
 * Hand-written `JsonValueCodec` and `JsonCodec` implementations over the `JsonReader` and
 * `JsonWriter` token API: a telemetry gateway encodes sensor readings with custom map keys,
 * a compact `[lat, lon]` location, a hex checksum and a version field, and decodes both the
 * current layout and a legacy layout by peeking at the version with a mark and rollback.
 */
class SensorTelemetryCodecTest {

  private val sensor: UUID = UUID.fromString("6f1c2a3b-4d5e-4f60-8a71-92b3c4d5e6f7")

  private val reading: Reading = Reading(
    sensor = sensor,
    takenAt = Instant.parse("2026-05-01T08:00:00Z"),
    values = Map(Channel.Temperature -> 21.5, Channel.Humidity -> 40.25),
    location = Some(Location(52.52, 13.405)),
    muted = List(Channel.Pressure),
    checksum = Vector(0x0a, 0x1b, 0xfe).map(_.toByte)
  )

  @Test
  def currentLayoutIsWrittenTokenByTokenAndReadBack(): Unit = {
    val json: String = writeToString(reading)

    assertEquals(
      """{"version":2,"sensor":"6f1c2a3b-4d5e-4f60-8a71-92b3c4d5e6f7","takenAt":"2026-05-01T08:00:00Z",""" +
        """"values":{"temp_c":21.5,"rh_pct":40.25},"location":[52.52,13.405],"muted":["hpa"],""" +
        """"checksum":"0a1bfe"}""",
      json
    )
    assertEquals(reading, readFromString[Reading](json))

    val unlocated: Reading = reading.copy(location = None, muted = Nil)
    val unlocatedJson: String = writeToString(unlocated)
    assertTrue(unlocatedJson.contains(""""location":null,"muted":[]"""), unlocatedJson)
    assertEquals(unlocated, readFromString[Reading](unlocatedJson))
  }

  @Test
  def versionFieldIsFoundWhereverItAppears(): Unit = {
    val reordered: String =
      """{
        |  "checksum": "0A1BFE",
        |  "firmware": {"build": 7, "flags": [true, null]},
        |  "values": {"rh_pct": 40.25, "temp_c": 21.5},
        |  "takenAt": "2026-05-01T08:00:00Z",
        |  "muted": ["hpa"],
        |  "location": [52.52, 13.405],
        |  "sensor": "6f1c2a3b-4d5e-4f60-8a71-92b3c4d5e6f7",
        |  "version": 2
        |}""".stripMargin
    assertEquals(reading, readFromString[Reading](reordered))

    val legacy: String = """{"celsius": 19.75, "id": "6f1c2a3b-4d5e-4f60-8a71-92b3c4d5e6f7", "at": 1777622400000, "version": 1}"""
    val upgraded: Reading = readFromString[Reading](legacy)
    assertEquals(sensor, upgraded.sensor)
    assertEquals(Instant.parse("2026-05-01T08:00:00Z"), upgraded.takenAt)
    assertEquals(Map(Channel.Temperature -> 19.75), upgraded.values)
    assertEquals(None, upgraded.location)
    assertTrue(upgraded.checksum.isEmpty)
  }

  @Test
  def batchCodecDecodesEveryLayoutInOneArray(): Unit = {
    val batch: List[Reading] = List(
      reading,
      reading.copy(takenAt = reading.takenAt.plusSeconds(60), values = Map(Channel.Pressure -> 1013.0))
    )
    val json: String = writeToString(batch)
    assertTrue(json.startsWith("""[{"version":2,"""), json)
    assertEquals(batch, readFromString[List[Reading]](json))

    val mixed: String =
      "[" + writeToString(reading) + """,{"version":1,"id":"6f1c2a3b-4d5e-4f60-8a71-92b3c4d5e6f7",""" +
        """"at":1777622460000,"celsius":20.0}]"""
    val decoded: List[Reading] = readFromString[List[Reading]](mixed)
    assertEquals(2, decoded.size)
    assertEquals(reading, decoded.head)
    assertEquals(Map(Channel.Temperature -> 20.0), decoded(1).values)
    assertEquals(List.empty[Reading], readFromString[List[Reading]]("[ ]"))
  }

  @Test
  def invalidReadingsAreRejectedWithTheCodecsOwnMessages(): Unit = {
    def rejection(json: String): String =
      assertThrows(classOf[JsonReaderException], () => readFromString[Reading](json)).getMessage

    val unknownChannel: String = rejection(
      """{"version":2,"sensor":"6f1c2a3b-4d5e-4f60-8a71-92b3c4d5e6f7","takenAt":"2026-05-01T08:00:00Z",""" +
        """"values":{"lux":300.0}}"""
    )
    assertTrue(unknownChannel.contains("unknown channel: lux"), unknownChannel)

    val missingSensor: String = rejection("""{"version":2,"takenAt":"2026-05-01T08:00:00Z"}""")
    assertTrue(missingSensor.contains("missing required field \"sensor\""), missingSensor)

    val futureVersion: String = rejection("""{"version":3,"sensor":"6f1c2a3b-4d5e-4f60-8a71-92b3c4d5e6f7"}""")
    assertTrue(futureVersion.contains("unsupported layout version 3"), futureVersion)

    val noVersion: String = rejection("""{"sensor":"6f1c2a3b-4d5e-4f60-8a71-92b3c4d5e6f7"}""")
    assertTrue(noVersion.contains("missing required field \"version\""), noVersion)

    val badLocation: String = rejection(
      """{"version":2,"sensor":"6f1c2a3b-4d5e-4f60-8a71-92b3c4d5e6f7","takenAt":"2026-05-01T08:00:00Z",""" +
        """"location":[52.52]}"""
    )
    assertTrue(badLocation.contains("expected ','"), badLocation)
  }
}

object SensorTelemetryCodecTest {

  enum Channel(val wire: String) {
    case Temperature extends Channel("temp_c")
    case Humidity extends Channel("rh_pct")
    case Pressure extends Channel("hpa")
  }

  final case class Location(lat: Double, lon: Double)

  final case class Reading(
    sensor: UUID,
    takenAt: Instant,
    values: Map[Channel, Double],
    location: Option[Location],
    muted: List[Channel],
    checksum: Vector[Byte]
  )

  /** Encodes a channel by its wire name both as an object key and as a string value. */
  given channelCodec: JsonCodec[Channel] = new JsonCodec[Channel] {
    private def lookup(in: JsonReader, name: String): Channel =
      Channel.values.find(_.wire == name).getOrElse(in.decodeError(s"unknown channel: $name"))

    override def decodeKey(in: JsonReader): Channel = lookup(in, in.readKeyAsString())

    override def encodeKey(x: Channel, out: JsonWriter): Unit = out.writeNonEscapedAsciiKey(x.wire)

    override def decodeValue(in: JsonReader, default: Channel): Channel = lookup(in, in.readString(null))

    override def encodeValue(x: Channel, out: JsonWriter): Unit = out.writeNonEscapedAsciiVal(x.wire)

    override def nullValue: Channel = null
  }

  /** Writes the current layout and reads both the current (2) and the legacy (1) layouts. */
  given readingCodec: JsonValueCodec[Reading] = new JsonValueCodec[Reading] {
    override def nullValue: Reading = null

    override def encodeValue(x: Reading, out: JsonWriter): Unit = {
      out.writeObjectStart()
      out.writeNonEscapedAsciiKey("version")
      out.writeVal(2)
      out.writeNonEscapedAsciiKey("sensor")
      out.writeVal(x.sensor)
      out.writeNonEscapedAsciiKey("takenAt")
      out.writeVal(x.takenAt)
      out.writeNonEscapedAsciiKey("values")
      out.writeObjectStart()
      x.values.foreach { (channel, value) =>
        channelCodec.encodeKey(channel, out)
        out.writeVal(value)
      }
      out.writeObjectEnd()
      out.writeNonEscapedAsciiKey("location")
      x.location match {
        case Some(Location(lat, lon)) =>
          out.writeArrayStart()
          out.writeVal(lat)
          out.writeVal(lon)
          out.writeArrayEnd()
        case None => out.writeNull()
      }
      out.writeNonEscapedAsciiKey("muted")
      out.writeArrayStart()
      x.muted.foreach(channel => channelCodec.encodeValue(channel, out))
      out.writeArrayEnd()
      out.writeNonEscapedAsciiKey("checksum")
      out.writeBase16Val(x.checksum.toArray, true)
      out.writeObjectEnd()
    }

    override def decodeValue(in: JsonReader, default: Reading): Reading = {
      if (!in.isNextToken('{')) in.readNullOrTokenError(default, '{')
      else {
        in.rollbackToken()
        in.setMark()
        in.nextToken()
        val version: Int = if (in.skipToKey("version")) in.readInt() else in.requiredFieldError("version")
        in.rollbackToMark()
        in.nextToken()
        version match {
          case 2 => decodeCurrent(in)
          case 1 => decodeLegacy(in)
          case other => in.decodeError(s"unsupported layout version $other")
        }
      }
    }

    /** Visits every key of the object whose `{` was just consumed; returns after its `}`. */
    private def readFields(in: JsonReader)(field: String => Unit): Unit =
      if (!in.isNextToken('}')) {
        in.rollbackToken()
        var more: Boolean = true
        while (more) {
          field(in.readKeyAsString())
          more = in.isNextToken(',')
        }
        if (!in.isCurrentToken('}')) in.objectEndOrCommaError()
      }

    private def decodeCurrent(in: JsonReader): Reading = {
      var sensor: UUID = null
      var takenAt: Instant = null
      var values: Map[Channel, Double] = Map.empty
      var location: Option[Location] = None
      val muted: ListBuffer[Channel] = ListBuffer.empty
      var checksum: Vector[Byte] = Vector.empty
      readFields(in) {
        case "version" => in.skip()
        case "sensor" => sensor = in.readUUID(null)
        case "takenAt" => takenAt = in.readInstant(null)
        case "values" =>
          if (!in.isNextToken('{')) in.objectStartOrNullError()
          if (!in.isNextToken('}')) {
            in.rollbackToken()
            var more: Boolean = true
            while (more) {
              val channel: Channel = channelCodec.decodeKey(in)
              values += channel -> in.readDouble()
              more = in.isNextToken(',')
            }
            if (!in.isCurrentToken('}')) in.objectEndOrCommaError()
          }
        case "location" => location = decodeLocation(in)
        case "muted" =>
          if (!in.isNextToken('[')) in.arrayStartOrNullError()
          if (!in.isNextToken(']')) {
            in.rollbackToken()
            var more: Boolean = true
            while (more) {
              muted += channelCodec.decodeValue(in, null)
              more = in.isNextToken(',')
            }
            if (!in.isCurrentToken(']')) in.arrayEndOrCommaError()
          }
        case "checksum" => checksum = in.readBase16AsBytes(Array.emptyByteArray).toVector
        case _ => in.skip()
      }
      if (sensor == null) in.requiredFieldError("sensor")
      if (takenAt == null) in.requiredFieldError("takenAt")
      Reading(sensor, takenAt, values, location, muted.toList, checksum)
    }

    private def decodeLocation(in: JsonReader): Option[Location] =
      if (in.isNextToken('[')) {
        val lat: Double = in.readDouble()
        if (!in.isNextToken(',')) in.commaError()
        val lon: Double = in.readDouble()
        if (!in.isNextToken(']')) in.arrayEndError()
        Some(Location(lat, lon))
      } else in.readNullOrTokenError(None, '[')

    private def decodeLegacy(in: JsonReader): Reading = {
      var sensor: UUID = null
      var epochMillis: Long = -1L
      var celsius: Double = Double.NaN
      readFields(in) {
        case "id" => sensor = in.readUUID(null)
        case "at" => epochMillis = in.readLong()
        case "celsius" => celsius = in.readDouble()
        case _ => in.skip()
      }
      if (sensor == null) in.requiredFieldError("id")
      if (epochMillis < 0) in.requiredFieldError("at")
      if (celsius.isNaN) in.requiredFieldError("celsius")
      Reading(sensor, Instant.ofEpochMilli(epochMillis), Map(Channel.Temperature -> celsius), None, Nil, Vector.empty)
    }
  }

  /** Delegates each element to the reading codec, so one batch may mix layout versions. */
  given batchCodec: JsonValueCodec[List[Reading]] = new JsonValueCodec[List[Reading]] {
    override def nullValue: List[Reading] = Nil

    override def encodeValue(x: List[Reading], out: JsonWriter): Unit = {
      out.writeArrayStart()
      x.foreach(reading => readingCodec.encodeValue(reading, out))
      out.writeArrayEnd()
    }

    override def decodeValue(in: JsonReader, default: List[Reading]): List[Reading] =
      if (in.isNextToken('[')) {
        val buffer: ListBuffer[Reading] = ListBuffer.empty
        if (!in.isNextToken(']')) {
          in.rollbackToken()
          var more: Boolean = true
          while (more) {
            buffer += readingCodec.decodeValue(in, readingCodec.nullValue)
            more = in.isNextToken(',')
          }
          if (!in.isCurrentToken(']')) in.arrayEndOrCommaError()
        }
        buffer.toList
      } else in.readNullOrTokenError(default, '[')
  }
}
