/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_datetime_jvm

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.YearMonth
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.minus
import kotlinx.datetime.monthsUntil
import kotlinx.datetime.offsetAt
import kotlinx.datetime.onDay
import kotlinx.datetime.periodUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toDatePeriod
import kotlinx.datetime.toDateTimePeriod
import kotlinx.datetime.toInstant
import kotlinx.datetime.toJavaDayOfWeek
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toJavaMonth
import kotlinx.datetime.toJavaPeriod
import kotlinx.datetime.toJavaYearMonth
import kotlinx.datetime.toJavaZoneId
import kotlinx.datetime.toJavaZoneOffset
import kotlinx.datetime.toKotlinDatePeriod
import kotlinx.datetime.toKotlinDayOfWeek
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.datetime.toKotlinLocalTime
import kotlinx.datetime.toKotlinMonth
import kotlinx.datetime.toKotlinTimeZone
import kotlinx.datetime.toKotlinUtcOffset
import kotlinx.datetime.toKotlinYearMonth
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.until
import kotlinx.datetime.yearsUntil
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Period as JavaPeriod
import java.time.ZoneId as JavaZoneId
import java.time.ZoneOffset as JavaZoneOffset
import kotlin.time.Instant

public class Kotlinx_datetime_jvmTest {
    @Test
    fun parsesFormatsAndInspectsCoreLocalTypes(): Unit {
        val leapDay: LocalDate = LocalDate.parse("2024-02-29")
        val sameLeapDay: LocalDate = LocalDate(2024, Month.FEBRUARY, 29)

        assertThat(leapDay).isEqualTo(sameLeapDay)
        assertThat(leapDay.year).isEqualTo(2024)
        assertThat(leapDay.month).isEqualTo(Month.FEBRUARY)
        assertThat(leapDay.day).isEqualTo(29)
        assertThat(leapDay.dayOfWeek).isEqualTo(DayOfWeek.THURSDAY)
        assertThat(leapDay.dayOfYear).isEqualTo(60)
        assertThat(LocalDate.fromEpochDays(leapDay.toEpochDays())).isEqualTo(leapDay)
        assertThat(LocalDate.parse("20240229", LocalDate.Formats.ISO_BASIC)).isEqualTo(leapDay)
        assertThat(leapDay.format(LocalDate.Formats.ISO)).isEqualTo("2024-02-29")

        val time: LocalTime = LocalTime.parse("23:59:58.123456789")
        assertThat(time.hour).isEqualTo(23)
        assertThat(time.minute).isEqualTo(59)
        assertThat(time.second).isEqualTo(58)
        assertThat(time.nanosecond).isEqualTo(123_456_789)
        assertThat(LocalTime.fromNanosecondOfDay(time.toNanosecondOfDay())).isEqualTo(time)
        assertThat(LocalTime.fromSecondOfDay(time.toSecondOfDay())).isEqualTo(LocalTime(23, 59, 58))
        assertThat(time.format(LocalTime.Formats.ISO)).isEqualTo("23:59:58.123456789")

        val dateTime: LocalDateTime = leapDay.atTime(time)
        assertThat(LocalDateTime.parse("2024-02-29T23:59:58.123456789")).isEqualTo(dateTime)
        assertThat(dateTime.date).isEqualTo(leapDay)
        assertThat(dateTime.time).isEqualTo(time)
        assertThat(dateTime.format(LocalDateTime.Formats.ISO)).isEqualTo("2024-02-29T23:59:58.123456789")
    }

    @Test
    fun rejectsInvalidTextWithIllegalArgumentException(): Unit {
        assertThatThrownBy { LocalDate.parse("2024-02-30") }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { LocalTime.parse("24:00:00") }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { UtcOffset.parse("+18:01") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun datePeriodsUnitsAndRangesHandleCalendarBoundaries(): Unit {
        val leapMonthEnd: LocalDate = LocalDate(2024, Month.JANUARY, 31) + DatePeriod(months = 1)
        assertThat(leapMonthEnd).isEqualTo(LocalDate(2024, Month.FEBRUARY, 29))
        assertThat(leapMonthEnd + DatePeriod(years = 1)).isEqualTo(LocalDate(2025, Month.FEBRUARY, 28))
        assertThat(LocalDate(2024, 1, 1).plus(6, DateTimeUnit.WEEK)).isEqualTo(LocalDate(2024, 2, 12))
        assertThat(LocalDate(2024, 3, 1).minus(1, DateTimeUnit.DAY)).isEqualTo(LocalDate(2024, 2, 29))

        val dateRange: List<LocalDate> = (LocalDate(2024, 2, 27)..LocalDate(2024, 3, 1)).toList()
        assertThat(dateRange).containsExactly(
            LocalDate(2024, 2, 27),
            LocalDate(2024, 2, 28),
            LocalDate(2024, 2, 29),
            LocalDate(2024, 3, 1)
        )
        assertThat(LocalDate(2024, 2, 28)..<LocalDate(2024, 3, 1)).contains(LocalDate(2024, 2, 29))
        assertThat(LocalDate(2024, 2, 28)..<LocalDate(2024, 3, 1)).doesNotContain(LocalDate(2024, 3, 1))
    }

    @Test
    fun yearMonthSupportsMonthArithmeticRangesAndDayProjection(): Unit {
        val february2024: YearMonth = YearMonth(2024, Month.FEBRUARY)

        assertThat(february2024.numberOfDays).isEqualTo(29)
        assertThat(february2024.firstDay).isEqualTo(LocalDate(2024, 2, 1))
        assertThat(february2024.lastDay).isEqualTo(LocalDate(2024, 2, 29))
        assertThat(february2024.onDay(29)).isEqualTo(LocalDate(2024, 2, 29))
        assertThat(february2024.format(YearMonth.Formats.ISO)).isEqualTo("2024-02")

        val november2023: YearMonth = YearMonth(2023, Month.NOVEMBER)
        assertThat(november2023.plus(5, DateTimeUnit.MONTH)).isEqualTo(YearMonth(2024, Month.APRIL))
        assertThat(november2023.monthsUntil(february2024)).isEqualTo(3)
        assertThat(november2023.yearsUntil(YearMonth(2025, Month.NOVEMBER))).isEqualTo(2)
        assertThat((YearMonth(2024, 1)..YearMonth(2024, 3)).toList()).containsExactly(
            YearMonth(2024, Month.JANUARY),
            february2024,
            YearMonth(2024, Month.MARCH)
        )
    }

    @Test
    fun periodsParseNormalizeExposeFieldsAndRoundTripThroughIsoText(): Unit {
        val dateTimePeriod: DateTimePeriod = "P1Y2M3DT4H5M6.000000007S".toDateTimePeriod()

        assertThat(dateTimePeriod.years).isEqualTo(1)
        assertThat(dateTimePeriod.months).isEqualTo(2)
        assertThat(dateTimePeriod.days).isEqualTo(3)
        assertThat(dateTimePeriod.hours).isEqualTo(4)
        assertThat(dateTimePeriod.minutes).isEqualTo(5)
        assertThat(dateTimePeriod.seconds).isEqualTo(6)
        assertThat(dateTimePeriod.nanoseconds).isEqualTo(7)
        assertThat(dateTimePeriod.toString().toDateTimePeriod()).isEqualTo(dateTimePeriod)

        val datePeriod: DatePeriod = "P2Y3M4D".toDatePeriod()
        assertThat(datePeriod).isEqualTo(DatePeriod(years = 2, months = 3, days = 4))
        assertThat(datePeriod.toJavaPeriod()).isEqualTo(JavaPeriod.of(2, 3, 4))
        assertThat(JavaPeriod.of(2, 3, 4).toKotlinDatePeriod()).isEqualTo(datePeriod)
    }

    @Test
    fun timeZonesOffsetsAndInstantsHandleDstTransitions(): Unit {
        val berlin: TimeZone = TimeZone.of("Europe/Berlin")
        val beforeSpringGap: Instant = Instant.parse("2024-03-31T00:30:00Z")
        val localBeforeGap: LocalDateTime = beforeSpringGap.toLocalDateTime(berlin)

        assertThat(localBeforeGap).isEqualTo(LocalDateTime(2024, 3, 31, 1, 30))
        assertThat(localBeforeGap.toInstant(berlin)).isEqualTo(beforeSpringGap)
        assertThat(berlin.offsetAt(beforeSpringGap)).isEqualTo(UtcOffset(hours = 1))

        val onePhysicalHourLater: Instant = beforeSpringGap.plus(1, DateTimeUnit.HOUR)
        assertThat(onePhysicalHourLater).isEqualTo(Instant.parse("2024-03-31T01:30:00Z"))
        assertThat(onePhysicalHourLater.toLocalDateTime(berlin)).isEqualTo(LocalDateTime(2024, 3, 31, 3, 30))
        assertThat(berlin.offsetAt(onePhysicalHourLater)).isEqualTo(UtcOffset(hours = 2))

        val startOfSkippedMidnightDay: Instant = LocalDate(2011, 12, 30).atStartOfDayIn(TimeZone.of("Pacific/Apia"))
        assertThat(startOfSkippedMidnightDay.toLocalDateTime(TimeZone.UTC).date).isEqualTo(LocalDate(2011, 12, 30))
    }

    @Test
    fun instantArithmeticUsesCalendarUnitsWithTheProvidedTimeZone(): Unit {
        val utc: TimeZone = TimeZone.UTC
        val start: Instant = Instant.parse("2024-01-31T10:15:30Z")
        val advanced: Instant = start.plus(DateTimePeriod(months = 1, days = 1, hours = 2), utc)

        assertThat(advanced).isEqualTo(Instant.parse("2024-03-01T12:15:30Z"))
        assertThat(start.periodUntil(advanced, utc)).isEqualTo(DateTimePeriod(months = 1, days = 1, hours = 2))
        assertThat(start.until(advanced, DateTimeUnit.HOUR, utc)).isEqualTo(722L)
        assertThat(start.until(start.plus(90, DateTimeUnit.MINUTE), DateTimeUnit.MINUTE)).isEqualTo(90L)
        assertThat(start.plus(1, DateTimeUnit.MONTH, utc).toLocalDateTime(utc)).isEqualTo(
            LocalDateTime(2024, 2, 29, 10, 15, 30)
        )
    }

    @Test
    fun utcOffsetsParseFormatAndCreateFixedOffsetZones(): Unit {
        val nepal: UtcOffset = UtcOffset.parse("+05:45")
        val negative: UtcOffset = UtcOffset(hours = -3, minutes = -30)

        assertThat(nepal.totalSeconds).isEqualTo(20_700)
        assertThat(nepal.format(UtcOffset.Formats.ISO)).isEqualTo("+05:45")
        assertThat(UtcOffset.parse("+0545", UtcOffset.Formats.FOUR_DIGITS)).isEqualTo(nepal)
        assertThat(negative.format(UtcOffset.Formats.ISO)).isEqualTo("-03:30")
        assertThat(UtcOffset.ZERO.asTimeZone().id).isEqualTo("Z")

        val localDateTime: LocalDateTime = LocalDateTime(2024, 6, 1, 12, 0)
        val instantInNepal: Instant = localDateTime.toInstant(nepal)
        assertThat(instantInNepal.toLocalDateTime(nepal.asTimeZone())).isEqualTo(localDateTime)
        assertThat(instantInNepal).isEqualTo(Instant.parse("2024-06-01T06:15:00Z"))
    }

    @Test
    fun customFormatBuildersParseAndFormatNamedDatesAndFractionalTimes(): Unit {
        val namedDateFormat = LocalDate.Format {
            dayOfWeek(DayOfWeekNames.ENGLISH_ABBREVIATED)
            chars(", ")
            monthName(MonthNames.ENGLISH_ABBREVIATED)
            char(' ')
            day(Padding.ZERO)
            chars(", ")
            year()
        }
        val parsedDate: LocalDate = namedDateFormat.parse("Thu, Feb 29, 2024")
        assertThat(parsedDate).isEqualTo(LocalDate(2024, Month.FEBRUARY, 29))
        assertThat(namedDateFormat.format(parsedDate)).isEqualTo("Thu, Feb 29, 2024")

        val preciseTimeFormat = LocalTime.Format {
            hour(Padding.ZERO)
            char(':')
            minute(Padding.ZERO)
            char(':')
            second(Padding.ZERO)
            char('.')
            secondFraction(6)
        }
        val parsedTime: LocalTime = preciseTimeFormat.parse("09:08:07.123456")
        assertThat(parsedTime).isEqualTo(LocalTime(9, 8, 7, 123_456_000))
        assertThat(preciseTimeFormat.format(parsedTime)).isEqualTo("09:08:07.123456")

        val localDateTimeFormat = LocalDateTime.Format {
            date(LocalDate.Formats.ISO)
            char(' ')
            time(preciseTimeFormat)
        }
        assertThat(localDateTimeFormat.parse("2024-02-29 09:08:07.123456")).isEqualTo(
            LocalDateTime(parsedDate, parsedTime)
        )
    }

    @Test
    fun dateTimeComponentsParseAndFormatTimeZoneIdentifiers(): Unit {
        val zonedDateTimeFormat = DateTimeComponents.Format {
            dateTime(LocalDateTime.Formats.ISO)
            char(' ')
            timeZoneId()
        }
        val components: DateTimeComponents = zonedDateTimeFormat.parse("2024-07-01T09:15:00 Europe/Paris")

        assertThat(components.toLocalDateTime()).isEqualTo(LocalDateTime(2024, 7, 1, 9, 15))
        assertThat(components.timeZoneId).isEqualTo("Europe/Paris")

        val timeZone: TimeZone = TimeZone.of(components.timeZoneId!!)
        assertThat(components.toLocalDateTime().toInstant(timeZone)).isEqualTo(Instant.parse("2024-07-01T07:15:00Z"))

        assertThat(zonedDateTimeFormat.format(components)).isEqualTo("2024-07-01T09:15:00 Europe/Paris")
    }

    @Test
    fun rfc1123ComponentsParseFormatAndConvertUsingOffset(): Unit {
        val rfc1123Format = DateTimeComponents.Formats.RFC_1123
        val components: DateTimeComponents = rfc1123Format.parse("Tue, 3 Jun 2008 11:05:30 +0200")

        assertThat(components.dayOfWeek).isEqualTo(DayOfWeek.TUESDAY)
        assertThat(components.toLocalDateTime()).isEqualTo(LocalDateTime(2008, 6, 3, 11, 5, 30))
        assertThat(components.toUtcOffset()).isEqualTo(UtcOffset(hours = 2))
        assertThat(components.toInstantUsingOffset()).isEqualTo(Instant.parse("2008-06-03T09:05:30Z"))

        val formatted: String = rfc1123Format.format(components)
        assertThat(rfc1123Format.parse(formatted).toInstantUsingOffset()).isEqualTo(components.toInstantUsingOffset())
    }

    @Test
    fun javaTimeConvertersPreserveEquivalentValues(): Unit {
        val date: LocalDate = LocalDate(2024, Month.FEBRUARY, 29)
        val time: LocalTime = LocalTime(12, 34, 56, 789_000_000)
        val dateTime: LocalDateTime = LocalDateTime(date, time)
        val zone: TimeZone = TimeZone.of("Asia/Tokyo")
        val offset: UtcOffset = UtcOffset(hours = 9)
        val yearMonth: YearMonth = YearMonth(2024, Month.FEBRUARY)

        assertThat(date.toJavaLocalDate().toKotlinLocalDate()).isEqualTo(date)
        assertThat(time.toJavaLocalTime().toKotlinLocalTime()).isEqualTo(time)
        assertThat(dateTime.toJavaLocalDateTime().toKotlinLocalDateTime()).isEqualTo(dateTime)
        assertThat(zone.toJavaZoneId().toKotlinTimeZone()).isEqualTo(zone)
        assertThat(offset.toJavaZoneOffset().toKotlinUtcOffset()).isEqualTo(offset)
        assertThat(yearMonth.toJavaYearMonth().toKotlinYearMonth()).isEqualTo(yearMonth)
        assertThat(Month.FEBRUARY.toJavaMonth().toKotlinMonth()).isEqualTo(Month.FEBRUARY)
        assertThat(DayOfWeek.THURSDAY.toJavaDayOfWeek().toKotlinDayOfWeek()).isEqualTo(DayOfWeek.THURSDAY)

        assertThat(JavaZoneId.of("UTC").toKotlinTimeZone()).isEqualTo(TimeZone.UTC)
        assertThat(JavaZoneOffset.ofHoursMinutes(5, 45).toKotlinUtcOffset()).isEqualTo(UtcOffset(hours = 5, minutes = 45))
    }
}
