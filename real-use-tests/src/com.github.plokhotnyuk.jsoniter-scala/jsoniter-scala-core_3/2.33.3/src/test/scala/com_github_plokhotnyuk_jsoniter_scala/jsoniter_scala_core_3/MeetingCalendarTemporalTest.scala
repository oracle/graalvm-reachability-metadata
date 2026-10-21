/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_plokhotnyuk_jsoniter_scala.jsoniter_scala_core_3

import com.github.plokhotnyuk.jsoniter_scala.core.JsonReaderException
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.core.readFromString
import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.time.Duration
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

import MeetingCalendarTemporalTest.{*, given}

/**
 * Reading and writing of the `java.time` value family in ISO-8601 form: a meeting calendar
 * stores zoned start times in region time zones across a daylight-saving change, offset
 * timestamps, local dates and times, durations, periods, year-month and month-day values,
 * and maps keyed by dates and zone IDs, and rejects impossible dates and unknown zones.
 */
class MeetingCalendarTemporalTest {

  private val berlin: ZoneId = ZoneId.of("Europe/Berlin")

  private val newYork: ZoneId = ZoneId.of("America/New_York")

  private val firstStandup: ZonedDateTime = ZonedDateTime.of(2026, 3, 23, 9, 0, 0, 0, berlin)

  private val calendar: Calendar = Calendar(
    owner = "platform-team",
    homeZone = berlin,
    meetings = List(
      Meeting(
        title = "Standup",
        start = firstStandup,
        length = Duration.ofMinutes(15),
        repeatEvery = Period.ofWeeks(1),
        reminderAt = LocalTime.of(8, 45),
        createdAt = OffsetDateTime.of(2026, 3, 1, 17, 30, 12, 250000000, ZoneOffset.ofHours(1))
      ),
      Meeting(
        title = "Quarterly review",
        start = ZonedDateTime.of(2026, 10, 30, 11, 30, 0, 0, newYork),
        length = Duration.ofHours(2).plusMinutes(30),
        repeatEvery = Period.ofMonths(3),
        reminderAt = LocalTime.of(10, 0, 30),
        createdAt = OffsetDateTime.of(2026, 9, 15, 8, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30))
      )
    ),
    holidays = Map(
      LocalDate.of(2026, 12, 25) -> "Christmas Day",
      LocalDate.of(2027, 1, 1) -> "New Year's Day"
    ),
    officeHours = Map(
      berlin -> OfficeHours(OffsetTime.of(9, 0, 0, 0, ZoneOffset.ofHours(1)), LocalTime.of(17, 30)),
      ZoneId.of("Asia/Kolkata") -> OfficeHours(OffsetTime.of(10, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)), LocalTime.of(18, 0))
    ),
    fiscalYear = Year.of(2026),
    closingMonth = YearMonth.of(2026, 11),
    anniversary = MonthDay.of(2, 29),
    lastSync = LocalDateTime.of(2026, 10, 19, 23, 59, 59, 999000000)
  )

  @Test
  def calendarIsWrittenInIsoFormAndReadBack(): Unit = {
    val json: String = writeToString(calendar)

    assertTrue(json.contains(""""homeZone":"Europe/Berlin""""), json)
    assertTrue(json.contains(""""start":"2026-03-23T09:00+01:00[Europe/Berlin]""""), json)
    assertTrue(json.contains(""""length":"PT15M","repeatEvery":"P7D","reminderAt":"08:45""""), json)
    assertTrue(json.contains(""""createdAt":"2026-03-01T17:30:12.250+01:00""""), json)
    assertTrue(json.contains(""""start":"2026-10-30T11:30-04:00[America/New_York]""""), json)
    assertTrue(json.contains(""""length":"PT2H30M","repeatEvery":"P3M","reminderAt":"10:00:30""""), json)
    assertTrue(json.contains(""""holidays":{"2026-12-25":"Christmas Day","2027-01-01":"New Year's Day"}"""), json)
    assertTrue(json.contains(""""Asia/Kolkata":{"opens":"10:00+05:30","closes":"18:00"}"""), json)
    assertTrue(json.contains(""""fiscalYear":"2026","closingMonth":"2026-11","anniversary":"--02-29""""), json)
    assertTrue(json.contains(""""lastSync":"2026-10-19T23:59:59.999""""), json)
    assertEquals(calendar, readFromString[Calendar](json))
  }

  @Test
  def recurringMeetingKeepsWallClockTimeAcrossDaylightSavingChange(): Unit = {
    val standup: Meeting = calendar.meetings.head
    val occurrences: List[ZonedDateTime] =
      List.iterate(standup.start, 3)(start => start.plus(standup.repeatEvery))
    val json: String = writeToString(occurrences)

    assertEquals(
      """["2026-03-23T09:00+01:00[Europe/Berlin]","2026-03-30T09:00+02:00[Europe/Berlin]",""" +
        """"2026-04-06T09:00+02:00[Europe/Berlin]"]""",
      json
    )
    val restored: List[ZonedDateTime] = readFromString[List[ZonedDateTime]](json)
    assertEquals(occurrences, restored)
    assertEquals(
      Duration.ofDays(7).minusHours(1),
      Duration.between(restored.head.toInstant, restored(1).toInstant)
    )
    assertEquals(List(9, 9, 9), restored.map(_.getHour))
  }

  @Test
  def clientSuppliedTimesInOtherNotationsAreNormalized(): Unit = {
    val submitted: String =
      """{"title":"Retro","start":"2026-06-05T14:00:00.000+02:00[Europe/Berlin]","length":"PT1H90M",""" +
        """"repeatEvery":"P2W","reminderAt":"13:45:00","createdAt":"2026-06-01T10:15:30Z"}"""
    val retro: Meeting = readFromString[Meeting](submitted)

    assertEquals(ZonedDateTime.of(2026, 6, 5, 14, 0, 0, 0, berlin), retro.start)
    assertEquals(Duration.ofMinutes(150), retro.length)
    assertEquals(Period.ofDays(14), retro.repeatEvery)
    assertEquals(LocalTime.of(13, 45), retro.reminderAt)
    assertEquals(ZoneOffset.UTC, retro.createdAt.getOffset)
    assertEquals(
      """{"title":"Retro","start":"2026-06-05T14:00+02:00[Europe/Berlin]","length":"PT2H30M",""" +
        """"repeatEvery":"P14D","reminderAt":"13:45","createdAt":"2026-06-01T10:15:30Z"}""",
      writeToString(retro)
    )

    val fixedZone: ZonedDateTime = readFromString[List[ZonedDateTime]]("""["2026-06-05T14:00+05:30"]""").head
    assertEquals(ZoneOffset.ofHoursMinutes(5, 30), fixedZone.getZone)
  }

  @Test
  def impossibleDatesAndUnknownZonesAreRejected(): Unit = {
    val json: String = writeToString(calendar)

    def rejection(broken: String): String =
      assertThrows(classOf[JsonReaderException], () => readFromString[Calendar](broken)).getMessage

    val leapDay: String = rejection(json.replace("2026-12-25", "2026-02-29"))
    assertTrue(leapDay.contains("illegal day"), leapDay)

    val unknownZone: String = rejection(json.replace("Asia/Kolkata", "Mars/Olympus_Mons"))
    assertTrue(unknownZone.contains("illegal timezone"), unknownZone)

    val badDuration: String = rejection(json.replace("PT15M", "15 minutes"))
    assertTrue(badDuration.contains("expected 'P' or '-'"), badDuration)

    val badMonth: String = rejection(json.replace("2026-11", "2026-13"))
    assertTrue(badMonth.contains("illegal month"), badMonth)
  }
}

object MeetingCalendarTemporalTest {

  final case class Meeting(
    title: String,
    start: ZonedDateTime,
    length: Duration,
    repeatEvery: Period,
    reminderAt: LocalTime,
    createdAt: OffsetDateTime
  )

  final case class OfficeHours(opens: OffsetTime, closes: LocalTime)

  final case class Calendar(
    owner: String,
    homeZone: ZoneId,
    meetings: List[Meeting],
    holidays: Map[LocalDate, String],
    officeHours: Map[ZoneId, OfficeHours],
    fiscalYear: Year,
    closingMonth: YearMonth,
    anniversary: MonthDay,
    lastSync: LocalDateTime
  )

  given calendarCodec: JsonValueCodec[Calendar] = JsonCodecMaker.make

  given meetingCodec: JsonValueCodec[Meeting] = JsonCodecMaker.make

  given occurrencesCodec: JsonValueCodec[List[ZonedDateTime]] = JsonCodecMaker.make
}
