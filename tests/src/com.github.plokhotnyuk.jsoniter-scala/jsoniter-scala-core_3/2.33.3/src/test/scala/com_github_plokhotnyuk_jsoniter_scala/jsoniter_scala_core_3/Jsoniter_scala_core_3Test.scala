/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_plokhotnyuk_jsoniter_scala.jsoniter_scala_core_3

import com.github.plokhotnyuk.jsoniter_scala.core.JsonKeyCodec
import com.github.plokhotnyuk.jsoniter_scala.core.JsonReader
import com.github.plokhotnyuk.jsoniter_scala.core.JsonReaderException
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.core.JsonWriter
import com.github.plokhotnyuk.jsoniter_scala.core.ReaderConfig
import com.github.plokhotnyuk.jsoniter_scala.core.WriterConfig
import com.github.plokhotnyuk.jsoniter_scala.core.readFromArray
import com.github.plokhotnyuk.jsoniter_scala.core.readFromArrayReentrant
import com.github.plokhotnyuk.jsoniter_scala.core.readFromByteBuffer
import com.github.plokhotnyuk.jsoniter_scala.core.readFromStream
import com.github.plokhotnyuk.jsoniter_scala.core.readFromString
import com.github.plokhotnyuk.jsoniter_scala.core.readFromStringReentrant
import com.github.plokhotnyuk.jsoniter_scala.core.readFromSubArray
import com.github.plokhotnyuk.jsoniter_scala.core.scanJsonArrayFromStream
import com.github.plokhotnyuk.jsoniter_scala.core.scanJsonValuesFromStream
import com.github.plokhotnyuk.jsoniter_scala.core.writeToArray
import com.github.plokhotnyuk.jsoniter_scala.core.writeToArrayReentrant
import com.github.plokhotnyuk.jsoniter_scala.core.writeToByteBuffer
import com.github.plokhotnyuk.jsoniter_scala.core.writeToStream
import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import com.github.plokhotnyuk.jsoniter_scala.core.writeToStringReentrant
import com.github.plokhotnyuk.jsoniter_scala.core.writeToSubArray
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.MonthDay
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.Period
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID
import scala.collection.mutable.ListBuffer

class Jsoniter_scala_core_3Test {
  import Jsoniter_scala_core_3Test.*

  @Test
  def roundTripsRichObjectAcrossSupportedInputAndOutputTargets(): Unit = {
    val value: Telemetry = sampleTelemetry
    val writerConfig: WriterConfig = WriterConfig
      .withPreferredBufSize(8)
      .withIndentionStep(2)
      .withEscapeUnicode(true)
    val readerConfig: ReaderConfig = ReaderConfig
      .withPreferredBufSize(12)
      .withPreferredCharBufSize(7)
      .withMaxBufSize(4096)
      .withMaxCharBufSize(512)

    val json: String = writeToString(value, writerConfig)(telemetryCodec)
    assertTrue(json.contains("\n"))
    assertTelemetryEquals(value, readFromString[Telemetry](json, readerConfig)(telemetryCodec))

    val compactBytes: Array[Byte] = writeToArray(value, WriterConfig.withPreferredBufSize(11))(telemetryCodec)
    assertTelemetryEquals(value, readFromArray[Telemetry](compactBytes, readerConfig)(telemetryCodec))
    assertTelemetryEquals(value, readFromArrayReentrant[Telemetry](compactBytes, readerConfig)(telemetryCodec))

    val target: Array[Byte] = Array.fill[Byte](2048)('#'.toByte)
    val from: Int = 17
    val to: Int = writeToSubArray(value, target, from, target.length - 17)(telemetryCodec)
    assertTrue(to > from)
    assertEquals('#'.toByte, target(from - 1))
    assertTelemetryEquals(value, readFromSubArray[Telemetry](target, from, to, readerConfig)(telemetryCodec))

    val directBuffer: ByteBuffer = ByteBuffer.allocateDirect(2048)
    writeToByteBuffer(value, directBuffer, WriterConfig.withPreferredBufSize(13))(telemetryCodec)
    assertTrue(directBuffer.position() > 0)
    directBuffer.flip()
    assertTelemetryEquals(value, readFromByteBuffer[Telemetry](directBuffer, readerConfig)(telemetryCodec))

    val outputStream: ByteArrayOutputStream = new ByteArrayOutputStream()
    writeToStream(value, outputStream, WriterConfig.withPreferredBufSize(9))(telemetryCodec)
    assertTelemetryEquals(
      value,
      readFromStream[Telemetry](new ByteArrayInputStream(outputStream.toByteArray), readerConfig)(telemetryCodec)
    )
  }

  @Test
  def supportsReentrantRawValueComposition(): Unit = {
    val value: Envelope = Envelope("outer", sampleTelemetry)

    val json: String = writeToStringReentrant(value)(envelopeCodec)

    assertTrue(json.startsWith("{\"name\":\"outer\",\"payload\":{"))
    assertEquals(value.name, readFromStringReentrant[Envelope](json)(envelopeCodec).name)
    assertTelemetryEquals(value.payload, readFromStringReentrant[Envelope](json)(envelopeCodec).payload)
  }

  @Test
  def scansJsonValueStreamsAndArraysWithBoundedConsumers(): Unit = {
    val valueStreamJson: Array[Byte] = "1\n2 3\n4".getBytes(UTF_8)
    val streamValues: ListBuffer[Int] = ListBuffer.empty[Int]
    scanJsonValuesFromStream[Int](new ByteArrayInputStream(valueStreamJson)) { value =>
      streamValues += value
      value < 3
    }(intCodec)
    assertEquals(List(1, 2, 3), streamValues.toList)

    val arrayValues: ListBuffer[Int] = ListBuffer.empty[Int]
    scanJsonArrayFromStream[Int](new ByteArrayInputStream("[4,5,6]".getBytes(UTF_8))) { value =>
      arrayValues += value
      true
    }(intCodec)
    assertEquals(List(4, 5, 6), arrayValues.toList)
  }

  @Test
  def encodesAndDecodesTypedObjectKeys(): Unit = {
    val report: DailyReport = DailyReport(
      Map(
        LocalDate.of(2026, 5, 4) -> BigDecimal("12.50"),
        LocalDate.of(2026, 5, 5) -> BigDecimal("0.75")
      )
    )

    val json: String = writeToString(report)(dailyReportCodec)

    assertTrue(json.contains("\"2026-05-04\":12.50"))
    assertEquals(report, readFromString[DailyReport](json)(dailyReportCodec))
  }

  @Test
  def roundTripsAdditionalJavaTimeValues(): Unit = {
    val value: TemporalValues = TemporalValues(
      duration = Duration.ofHours(27).plusMinutes(5).plusNanos(123456789L),
      localDateTime = LocalDateTime.of(2026, 5, 4, 10, 15, 30, 987654321),
      localTime = LocalTime.of(23, 59, 58, 123456789),
      monthDay = MonthDay.of(5, 4),
      offsetDateTime = OffsetDateTime.parse("2026-05-04T10:15:30.123456789+02:00"),
      offsetTime = OffsetTime.parse("10:15:30.123456789+02:00"),
      period = Period.of(1, 2, 3),
      year = Year.of(2026),
      yearMonth = YearMonth.of(2026, 5),
      zonedDateTime = ZonedDateTime.parse("2026-05-04T10:15:30.123456789+02:00[Europe/Belgrade]"),
      zoneOffset = ZoneOffset.ofHoursMinutes(5, 45)
    )

    val json: String = writeToString(value)(temporalValuesCodec)

    assertTrue(json.contains("\"duration\":\"PT27H5M0.123456789S\""))
    assertEquals(value, readFromString[TemporalValues](json)(temporalValuesCodec))
  }

  @Test
  def encodesValuesAsJsonStringsAndAlternativeBinaryAlphabets(): Unit = {
    val value: EncodedFields = EncodedFields(
      id = 7,
      active = true,
      checksum = Array[Byte](0, 15, -91.toByte, -1.toByte),
      token = Array[Byte](-5.toByte, -17.toByte, -1.toByte)
    )

    val json: String = writeToString(value)(encodedFieldsCodec)

    assertTrue(json.contains("\"id\":\"7\""))
    assertTrue(json.contains("\"active\":\"true\""))
    assertTrue(json.contains("\"token\":\"--__\""))
    val decoded: EncodedFields = readFromString[EncodedFields](json)(encodedFieldsCodec)
    assertEquals(value.id, decoded.id)
    assertEquals(value.active, decoded.active)
    assertArrayEquals(value.checksum, decoded.checksum)
    assertArrayEquals(value.token, decoded.token)
  }

  @Test
  def reportsReaderFailuresForInvalidInputAndCodecValidation(): Unit = {
    val trailingInput: JsonReaderException = expectReaderFailure {
      readFromString[Int]("1 2")(intCodec)
    }
    assertNotNull(trailingInput.getMessage)

    val duplicateField: JsonReaderException = expectReaderFailure {
      readFromString[Telemetry]("{\"id\":1,\"id\":2}")(telemetryCodec)
    }
    assertNotNull(duplicateField.getMessage)

    val missingField: JsonReaderException = expectReaderFailure {
      readFromString[Telemetry]("{\"id\":1}")(telemetryCodec)
    }
    assertTrue(missingField.getMessage.contains("name"))

    val malformedWithHexDump: JsonReaderException = expectReaderFailure {
      readFromString[Int](
        "not-json",
        ReaderConfig.withAppendHexDumpToParseException(true).withHexDumpSize(8)
      )(intCodec)
    }
    assertNotNull(malformedWithHexDump.getMessage)
  }
}

object Jsoniter_scala_core_3Test {
  final case class Telemetry(
      id: Int,
      name: String,
      enabled: Boolean,
      count: Long,
      ratio: Double,
      marker: Char,
      big: BigInt,
      amount: BigDecimal,
      createdAt: Instant,
      activeOn: LocalDate,
      zone: ZoneId,
      uuid: UUID,
      payload: Array[Byte],
      tags: List[String])

  final case class Envelope(name: String, payload: Telemetry)

  final case class DailyReport(values: Map[LocalDate, BigDecimal])

  final case class EncodedFields(id: Int, active: Boolean, checksum: Array[Byte], token: Array[Byte])

  final case class TemporalValues(
      duration: Duration,
      localDateTime: LocalDateTime,
      localTime: LocalTime,
      monthDay: MonthDay,
      offsetDateTime: OffsetDateTime,
      offsetTime: OffsetTime,
      period: Period,
      year: Year,
      yearMonth: YearMonth,
      zonedDateTime: ZonedDateTime,
      zoneOffset: ZoneOffset)

  private val sampleTelemetry: Telemetry = Telemetry(
    id = 42,
    name = "sensor-µ\\nunit",
    enabled = true,
    count = 9876543210123L,
    ratio = 12.5d,
    marker = 'Ω',
    big = BigInt("123456789012345678901234567890"),
    amount = BigDecimal("12345.6789"),
    createdAt = Instant.parse("2026-05-04T10:15:30.123456Z"),
    activeOn = LocalDate.of(2026, 5, 4),
    zone = ZoneId.of("Europe/Belgrade"),
    uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
    payload = Array[Byte](0, 1, 2, 3, 127, -128.toByte, -1.toByte),
    tags = List("fast", "unicode-π", "escaped\\tag")
  )

  private val intCodec: JsonValueCodec[Int] = new JsonValueCodec[Int] {
    override def nullValue: Int = 0

    override def decodeValue(in: JsonReader, default: Int): Int = in.readInt()

    override def encodeValue(value: Int, out: JsonWriter): Unit = out.writeVal(value)
  }

  private val telemetryCodec: JsonValueCodec[Telemetry] = new JsonValueCodec[Telemetry] {
    private val RequiredMask: Int = (1 << 14) - 1

    override def nullValue: Telemetry = null

    override def decodeValue(in: JsonReader, default: Telemetry): Telemetry = {
      if (in.isNextToken('{')) {
        var id: Int = 0
        var name: String = null
        var enabled: Boolean = false
        var count: Long = 0L
        var ratio: Double = 0.0d
        var marker: Char = 0.toChar
        var big: BigInt = BigInt(0)
        var amount: BigDecimal = BigDecimal(0)
        var createdAt: Instant = null
        var activeOn: LocalDate = null
        var zone: ZoneId = null
        var uuid: UUID = null
        var payload: Array[Byte] = Array.emptyByteArray
        var tags: List[String] = Nil
        var seen: Int = 0

        if (!in.isNextToken('}')) {
          in.rollbackToken()
          var keyLength: Int = -1
          while (keyLength < 0 || in.isNextToken(',')) {
            keyLength = in.readKeyAsCharBuf()
            if (in.isCharBufEqualsTo(keyLength, "id")) {
              seen = markSeen(in, keyLength, seen, 0)
              id = in.readInt()
            } else if (in.isCharBufEqualsTo(keyLength, "name")) {
              seen = markSeen(in, keyLength, seen, 1)
              name = in.readString(name)
            } else if (in.isCharBufEqualsTo(keyLength, "enabled")) {
              seen = markSeen(in, keyLength, seen, 2)
              enabled = in.readBoolean()
            } else if (in.isCharBufEqualsTo(keyLength, "count")) {
              seen = markSeen(in, keyLength, seen, 3)
              count = in.readLong()
            } else if (in.isCharBufEqualsTo(keyLength, "ratio")) {
              seen = markSeen(in, keyLength, seen, 4)
              ratio = in.readDouble()
            } else if (in.isCharBufEqualsTo(keyLength, "marker")) {
              seen = markSeen(in, keyLength, seen, 5)
              marker = in.readChar()
            } else if (in.isCharBufEqualsTo(keyLength, "big")) {
              seen = markSeen(in, keyLength, seen, 6)
              big = in.readBigInt(big)
            } else if (in.isCharBufEqualsTo(keyLength, "amount")) {
              seen = markSeen(in, keyLength, seen, 7)
              amount = in.readBigDecimal(amount)
            } else if (in.isCharBufEqualsTo(keyLength, "createdAt")) {
              seen = markSeen(in, keyLength, seen, 8)
              createdAt = in.readInstant(createdAt)
            } else if (in.isCharBufEqualsTo(keyLength, "activeOn")) {
              seen = markSeen(in, keyLength, seen, 9)
              activeOn = in.readLocalDate(activeOn)
            } else if (in.isCharBufEqualsTo(keyLength, "zone")) {
              seen = markSeen(in, keyLength, seen, 10)
              zone = in.readZoneId(zone)
            } else if (in.isCharBufEqualsTo(keyLength, "uuid")) {
              seen = markSeen(in, keyLength, seen, 11)
              uuid = in.readUUID(uuid)
            } else if (in.isCharBufEqualsTo(keyLength, "payload")) {
              seen = markSeen(in, keyLength, seen, 12)
              payload = in.readBase64AsBytes(payload)
            } else if (in.isCharBufEqualsTo(keyLength, "tags")) {
              seen = markSeen(in, keyLength, seen, 13)
              tags = decodeStringList(in, tags)
            } else {
              in.skip()
            }
          }
          if (!in.isCurrentToken('}')) in.objectEndOrCommaError()
        }
        val missing: Int = RequiredMask & ~seen
        if (missing != 0) in.requiredFieldError(fieldName(Integer.numberOfTrailingZeros(missing)))
        Telemetry(id, name, enabled, count, ratio, marker, big, amount, createdAt, activeOn, zone, uuid, payload, tags)
      } else {
        in.readNullOrTokenError(default, '{')
      }
    }

    override def encodeValue(value: Telemetry, out: JsonWriter): Unit = {
      out.writeObjectStart()
      out.writeNonEscapedAsciiKey("id")
      out.writeVal(value.id)
      out.writeNonEscapedAsciiKey("name")
      out.writeVal(value.name)
      out.writeNonEscapedAsciiKey("enabled")
      out.writeVal(value.enabled)
      out.writeNonEscapedAsciiKey("count")
      out.writeVal(value.count)
      out.writeNonEscapedAsciiKey("ratio")
      out.writeVal(value.ratio)
      out.writeNonEscapedAsciiKey("marker")
      out.writeVal(value.marker)
      out.writeNonEscapedAsciiKey("big")
      out.writeVal(value.big)
      out.writeNonEscapedAsciiKey("amount")
      out.writeVal(value.amount)
      out.writeNonEscapedAsciiKey("createdAt")
      out.writeVal(value.createdAt)
      out.writeNonEscapedAsciiKey("activeOn")
      out.writeVal(value.activeOn)
      out.writeNonEscapedAsciiKey("zone")
      out.writeVal(value.zone)
      out.writeNonEscapedAsciiKey("uuid")
      out.writeVal(value.uuid)
      out.writeNonEscapedAsciiKey("payload")
      out.writeBase64Val(value.payload, true)
      out.writeNonEscapedAsciiKey("tags")
      encodeStringList(value.tags, out)
      out.writeObjectEnd()
    }
  }

  private val envelopeCodec: JsonValueCodec[Envelope] = new JsonValueCodec[Envelope] {
    override def nullValue: Envelope = null

    override def decodeValue(in: JsonReader, default: Envelope): Envelope = {
      if (in.isNextToken('{')) {
        var name: String = null
        var payload: Telemetry = null
        var seen: Int = 0
        if (!in.isNextToken('}')) {
          in.rollbackToken()
          var keyLength: Int = -1
          while (keyLength < 0 || in.isNextToken(',')) {
            keyLength = in.readKeyAsCharBuf()
            if (in.isCharBufEqualsTo(keyLength, "name")) {
              seen = markSeen(in, keyLength, seen, 0)
              name = in.readString(name)
            } else if (in.isCharBufEqualsTo(keyLength, "payload")) {
              seen = markSeen(in, keyLength, seen, 1)
              payload = readFromArrayReentrant[Telemetry](in.readRawValAsBytes())(telemetryCodec)
            } else {
              in.skip()
            }
          }
          if (!in.isCurrentToken('}')) in.objectEndOrCommaError()
        }
        val missing: Int = 0x3 & ~seen
        if (missing != 0) in.requiredFieldError(if ((missing & 0x1) != 0) "name" else "payload")
        Envelope(name, payload)
      } else {
        in.readNullOrTokenError(default, '{')
      }
    }

    override def encodeValue(value: Envelope, out: JsonWriter): Unit = {
      out.writeObjectStart()
      out.writeNonEscapedAsciiKey("name")
      out.writeVal(value.name)
      out.writeNonEscapedAsciiKey("payload")
      out.writeRawVal(writeToArrayReentrant(value.payload)(telemetryCodec))
      out.writeObjectEnd()
    }
  }

  private val localDateKeyCodec: JsonKeyCodec[LocalDate] = new JsonKeyCodec[LocalDate] {
    override def decodeKey(in: JsonReader): LocalDate = in.readKeyAsLocalDate()

    override def encodeKey(value: LocalDate, out: JsonWriter): Unit = out.writeKey(value)
  }

  private val dailyReportCodec: JsonValueCodec[DailyReport] = new JsonValueCodec[DailyReport] {
    override def nullValue: DailyReport = null

    override def decodeValue(in: JsonReader, default: DailyReport): DailyReport = {
      if (in.isNextToken('{')) {
        val builder: scala.collection.mutable.Builder[(LocalDate, BigDecimal), Map[LocalDate, BigDecimal]] =
          Map.newBuilder[LocalDate, BigDecimal]
        if (!in.isNextToken('}')) {
          in.rollbackToken()
          var keepReading: Boolean = true
          while (keepReading) {
            val key: LocalDate = localDateKeyCodec.decodeKey(in)
            builder += key -> in.readBigDecimal(BigDecimal(0))
            keepReading = in.isNextToken(',')
          }
          if (!in.isCurrentToken('}')) in.objectEndOrCommaError()
        }
        DailyReport(builder.result())
      } else {
        in.readNullOrTokenError(default, '{')
      }
    }

    override def encodeValue(value: DailyReport, out: JsonWriter): Unit = {
      out.writeObjectStart()
      value.values.toSeq.sortBy(_._1.toEpochDay).foreach { case (key, amount) =>
        localDateKeyCodec.encodeKey(key, out)
        out.writeVal(amount)
      }
      out.writeObjectEnd()
    }
  }

  private val temporalValuesCodec: JsonValueCodec[TemporalValues] = new JsonValueCodec[TemporalValues] {
    private val RequiredMask: Int = (1 << 11) - 1

    override def nullValue: TemporalValues = null

    override def decodeValue(in: JsonReader, default: TemporalValues): TemporalValues = {
      if (in.isNextToken('{')) {
        var duration: Duration = null
        var localDateTime: LocalDateTime = null
        var localTime: LocalTime = null
        var monthDay: MonthDay = null
        var offsetDateTime: OffsetDateTime = null
        var offsetTime: OffsetTime = null
        var period: Period = null
        var year: Year = null
        var yearMonth: YearMonth = null
        var zonedDateTime: ZonedDateTime = null
        var zoneOffset: ZoneOffset = null
        var seen: Int = 0

        if (!in.isNextToken('}')) {
          in.rollbackToken()
          var keyLength: Int = -1
          while (keyLength < 0 || in.isNextToken(',')) {
            keyLength = in.readKeyAsCharBuf()
            if (in.isCharBufEqualsTo(keyLength, "duration")) {
              seen = markSeen(in, keyLength, seen, 0)
              duration = in.readDuration(duration)
            } else if (in.isCharBufEqualsTo(keyLength, "localDateTime")) {
              seen = markSeen(in, keyLength, seen, 1)
              localDateTime = in.readLocalDateTime(localDateTime)
            } else if (in.isCharBufEqualsTo(keyLength, "localTime")) {
              seen = markSeen(in, keyLength, seen, 2)
              localTime = in.readLocalTime(localTime)
            } else if (in.isCharBufEqualsTo(keyLength, "monthDay")) {
              seen = markSeen(in, keyLength, seen, 3)
              monthDay = in.readMonthDay(monthDay)
            } else if (in.isCharBufEqualsTo(keyLength, "offsetDateTime")) {
              seen = markSeen(in, keyLength, seen, 4)
              offsetDateTime = in.readOffsetDateTime(offsetDateTime)
            } else if (in.isCharBufEqualsTo(keyLength, "offsetTime")) {
              seen = markSeen(in, keyLength, seen, 5)
              offsetTime = in.readOffsetTime(offsetTime)
            } else if (in.isCharBufEqualsTo(keyLength, "period")) {
              seen = markSeen(in, keyLength, seen, 6)
              period = in.readPeriod(period)
            } else if (in.isCharBufEqualsTo(keyLength, "year")) {
              seen = markSeen(in, keyLength, seen, 7)
              year = in.readYear(year)
            } else if (in.isCharBufEqualsTo(keyLength, "yearMonth")) {
              seen = markSeen(in, keyLength, seen, 8)
              yearMonth = in.readYearMonth(yearMonth)
            } else if (in.isCharBufEqualsTo(keyLength, "zonedDateTime")) {
              seen = markSeen(in, keyLength, seen, 9)
              zonedDateTime = in.readZonedDateTime(zonedDateTime)
            } else if (in.isCharBufEqualsTo(keyLength, "zoneOffset")) {
              seen = markSeen(in, keyLength, seen, 10)
              zoneOffset = in.readZoneOffset(zoneOffset)
            } else {
              in.skip()
            }
          }
          if (!in.isCurrentToken('}')) in.objectEndOrCommaError()
        }
        val missing: Int = RequiredMask & ~seen
        if (missing != 0) in.requiredFieldError(temporalFieldName(Integer.numberOfTrailingZeros(missing)))
        TemporalValues(
          duration,
          localDateTime,
          localTime,
          monthDay,
          offsetDateTime,
          offsetTime,
          period,
          year,
          yearMonth,
          zonedDateTime,
          zoneOffset
        )
      } else {
        in.readNullOrTokenError(default, '{')
      }
    }

    override def encodeValue(value: TemporalValues, out: JsonWriter): Unit = {
      out.writeObjectStart()
      out.writeNonEscapedAsciiKey("duration")
      out.writeVal(value.duration)
      out.writeNonEscapedAsciiKey("localDateTime")
      out.writeVal(value.localDateTime)
      out.writeNonEscapedAsciiKey("localTime")
      out.writeVal(value.localTime)
      out.writeNonEscapedAsciiKey("monthDay")
      out.writeVal(value.monthDay)
      out.writeNonEscapedAsciiKey("offsetDateTime")
      out.writeVal(value.offsetDateTime)
      out.writeNonEscapedAsciiKey("offsetTime")
      out.writeVal(value.offsetTime)
      out.writeNonEscapedAsciiKey("period")
      out.writeVal(value.period)
      out.writeNonEscapedAsciiKey("year")
      out.writeVal(value.year)
      out.writeNonEscapedAsciiKey("yearMonth")
      out.writeVal(value.yearMonth)
      out.writeNonEscapedAsciiKey("zonedDateTime")
      out.writeVal(value.zonedDateTime)
      out.writeNonEscapedAsciiKey("zoneOffset")
      out.writeVal(value.zoneOffset)
      out.writeObjectEnd()
    }
  }

  private val encodedFieldsCodec: JsonValueCodec[EncodedFields] = new JsonValueCodec[EncodedFields] {
    override def nullValue: EncodedFields = null

    override def decodeValue(in: JsonReader, default: EncodedFields): EncodedFields = {
      if (in.isNextToken('{')) {
        var id: Int = 0
        var active: Boolean = false
        var checksum: Array[Byte] = Array.emptyByteArray
        var token: Array[Byte] = Array.emptyByteArray
        var seen: Int = 0

        if (!in.isNextToken('}')) {
          in.rollbackToken()
          var keyLength: Int = -1
          while (keyLength < 0 || in.isNextToken(',')) {
            keyLength = in.readKeyAsCharBuf()
            if (in.isCharBufEqualsTo(keyLength, "id")) {
              seen = markSeen(in, keyLength, seen, 0)
              id = in.readStringAsInt()
            } else if (in.isCharBufEqualsTo(keyLength, "active")) {
              seen = markSeen(in, keyLength, seen, 1)
              active = in.readStringAsBoolean()
            } else if (in.isCharBufEqualsTo(keyLength, "checksum")) {
              seen = markSeen(in, keyLength, seen, 2)
              checksum = in.readBase16AsBytes(checksum)
            } else if (in.isCharBufEqualsTo(keyLength, "token")) {
              seen = markSeen(in, keyLength, seen, 3)
              token = in.readBase64UrlAsBytes(token)
            } else {
              in.skip()
            }
          }
          if (!in.isCurrentToken('}')) in.objectEndOrCommaError()
        }
        val missing: Int = 0xf & ~seen
        if (missing != 0) in.requiredFieldError(encodedFieldName(Integer.numberOfTrailingZeros(missing)))
        EncodedFields(id, active, checksum, token)
      } else {
        in.readNullOrTokenError(default, '{')
      }
    }

    override def encodeValue(value: EncodedFields, out: JsonWriter): Unit = {
      out.writeObjectStart()
      out.writeNonEscapedAsciiKey("id")
      out.writeValAsString(value.id)
      out.writeNonEscapedAsciiKey("active")
      out.writeValAsString(value.active)
      out.writeNonEscapedAsciiKey("checksum")
      out.writeBase16Val(value.checksum, true)
      out.writeNonEscapedAsciiKey("token")
      out.writeBase64UrlVal(value.token, false)
      out.writeObjectEnd()
    }
  }

  private def decodeStringList(in: JsonReader, default: List[String]): List[String] = {
    if (in.isNextToken('[')) {
      val builder: scala.collection.mutable.Builder[String, List[String]] = List.newBuilder[String]
      if (!in.isNextToken(']')) {
        in.rollbackToken()
        var keepReading: Boolean = true
        while (keepReading) {
          builder += in.readString(null)
          keepReading = in.isNextToken(',')
        }
        if (!in.isCurrentToken(']')) in.arrayEndOrCommaError()
      }
      builder.result()
    } else {
      in.readNullOrTokenError(default, '[')
    }
  }

  private def encodeStringList(values: List[String], out: JsonWriter): Unit = {
    out.writeArrayStart()
    values.foreach { value =>
      out.writeVal(value)
    }
    out.writeArrayEnd()
  }

  private def markSeen(in: JsonReader, keyLength: Int, seen: Int, bitIndex: Int): Int = {
    val mask: Int = 1 << bitIndex
    if ((seen & mask) == 0) seen | mask
    else in.duplicatedKeyError(keyLength)
  }

  private def fieldName(index: Int): String = index match {
    case 0 => "id"
    case 1 => "name"
    case 2 => "enabled"
    case 3 => "count"
    case 4 => "ratio"
    case 5 => "marker"
    case 6 => "big"
    case 7 => "amount"
    case 8 => "createdAt"
    case 9 => "activeOn"
    case 10 => "zone"
    case 11 => "uuid"
    case 12 => "payload"
    case 13 => "tags"
  }

  private def encodedFieldName(index: Int): String = index match {
    case 0 => "id"
    case 1 => "active"
    case 2 => "checksum"
    case 3 => "token"
  }

  private def temporalFieldName(index: Int): String = index match {
    case 0 => "duration"
    case 1 => "localDateTime"
    case 2 => "localTime"
    case 3 => "monthDay"
    case 4 => "offsetDateTime"
    case 5 => "offsetTime"
    case 6 => "period"
    case 7 => "year"
    case 8 => "yearMonth"
    case 9 => "zonedDateTime"
    case 10 => "zoneOffset"
  }

  private def assertTelemetryEquals(expected: Telemetry, actual: Telemetry): Unit = {
    assertEquals(expected.id, actual.id)
    assertEquals(expected.name, actual.name)
    assertEquals(expected.enabled, actual.enabled)
    assertEquals(expected.count, actual.count)
    assertEquals(expected.ratio, actual.ratio, 0.0d)
    assertEquals(expected.marker, actual.marker)
    assertEquals(expected.big, actual.big)
    assertEquals(expected.amount, actual.amount)
    assertEquals(expected.createdAt, actual.createdAt)
    assertEquals(expected.activeOn, actual.activeOn)
    assertEquals(expected.zone, actual.zone)
    assertEquals(expected.uuid, actual.uuid)
    assertArrayEquals(expected.payload, actual.payload)
    assertEquals(expected.tags, actual.tags)
  }

  private def expectReaderFailure(body: => Unit): JsonReaderException = {
    val exception: JsonReaderException = assertThrows(
      classOf[JsonReaderException],
      new Executable {
        override def execute(): Unit = body
      }
    )
    exception
  }
}
