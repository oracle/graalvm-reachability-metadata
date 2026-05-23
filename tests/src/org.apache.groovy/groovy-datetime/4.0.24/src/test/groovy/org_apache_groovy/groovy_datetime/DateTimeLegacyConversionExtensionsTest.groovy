/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_datetime

import org.apache.groovy.datetime.extensions.DateTimeExtensions
import org.junit.jupiter.api.Test

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.MonthDay
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

public class DateTimeLegacyConversionExtensionsTest {
    @Test
    void calendarConvertsToJavaTimeTypesUsingItsConfiguredTimeZone() {
        ZoneId zone = ZoneId.of('UTC')
        ZonedDateTime zonedDateTime = ZonedDateTime.of(2024, 7, 20, 14, 15, 16, 123000000, zone)
        Calendar calendar = GregorianCalendar.from(zonedDateTime)

        assert DateTimeExtensions.toInstant(calendar) == zonedDateTime.toInstant()
        assert DateTimeExtensions.getZoneId(calendar) == zone
        assert DateTimeExtensions.getZoneOffset(calendar) == ZoneOffset.UTC
        assert DateTimeExtensions.toYear(calendar) == Year.of(2024)
        assert DateTimeExtensions.toMonth(calendar) == Month.JULY
        assert DateTimeExtensions.toMonthDay(calendar) == MonthDay.of(7, 20)
        assert DateTimeExtensions.toYearMonth(calendar) == YearMonth.of(2024, 7)
        assert DateTimeExtensions.toDayOfWeek(calendar) == DayOfWeek.SATURDAY
        assert DateTimeExtensions.toLocalTime(calendar) == LocalTime.of(14, 15, 16, 123000000)
        assert DateTimeExtensions.toLocalDateTime(calendar) == LocalDateTime.of(2024, 7, 20, 14, 15, 16, 123000000)
        assert DateTimeExtensions.toZonedDateTime(calendar) == zonedDateTime
        assert DateTimeExtensions.toOffsetDateTime(calendar) == zonedDateTime.toOffsetDateTime()
        assert DateTimeExtensions.toOffsetTime(calendar) == zonedDateTime.toOffsetDateTime().toOffsetTime()
    }

    @Test
    void instantLocalDateAndLocalTimeConvertToLegacyTypes() {
        Instant instant = Instant.parse('2024-07-20T14:15:16Z')
        Date instantDate = DateTimeExtensions.toDate(instant)
        Calendar instantCalendar = DateTimeExtensions.toCalendar(instant)

        assert instantDate.toInstant() == instant
        assert instantCalendar.toInstant() == instant

        LocalDate localDate = LocalDate.of(2024, 7, 20)
        Date localDateAsDate = DateTimeExtensions.toDate(localDate)
        Calendar localDateAsCalendar = DateTimeExtensions.toCalendar(localDate)
        assert DateTimeExtensions.toLocalDate(localDateAsDate) == localDate
        assert DateTimeExtensions.toLocalDateTime(localDateAsCalendar).toLocalDate() == localDate
        assert DateTimeExtensions.toLocalTime(localDateAsCalendar) == LocalTime.MIDNIGHT

        LocalTime localTime = LocalTime.of(14, 15, 16)
        Date localTimeAsDate = DateTimeExtensions.toDate(localTime)
        Calendar localTimeAsCalendar = DateTimeExtensions.toCalendar(localTime)
        assert DateTimeExtensions.toLocalTime(localTimeAsDate).withNano(0) == localTime
        assert DateTimeExtensions.toLocalTime(localTimeAsCalendar).withNano(0) == localTime
    }

    @Test
    void offsetAndZonedValuesConvertToDateAndCalendarAtSameInstant() {
        ZoneOffset offset = ZoneOffset.ofHoursMinutes(5, 30)
        OffsetDateTime offsetDateTime = OffsetDateTime.of(2024, 7, 20, 14, 15, 16, 0, offset)
        OffsetTime offsetTime = OffsetTime.of(14, 15, 16, 0, offset)
        ZonedDateTime zonedDateTime = ZonedDateTime.of(2024, 7, 20, 8, 45, 16, 0, ZoneId.of('UTC'))

        assert DateTimeExtensions.toDate(offsetDateTime).toInstant() == offsetDateTime.toInstant()
        assert DateTimeExtensions.toCalendar(offsetDateTime).toInstant() == offsetDateTime.toInstant()
        assert DateTimeExtensions.toOffsetDateTime(DateTimeExtensions.toCalendar(offsetDateTime)) == offsetDateTime

        assert DateTimeExtensions.toDate(offsetTime) instanceof Date
        assert DateTimeExtensions.toOffsetTime(DateTimeExtensions.toCalendar(offsetTime)).withNano(0) == offsetTime

        assert DateTimeExtensions.toDate(zonedDateTime).toInstant() == zonedDateTime.toInstant()
        assert DateTimeExtensions.toCalendar(zonedDateTime).toInstant() == zonedDateTime.toInstant()
        assert DateTimeExtensions.toZonedDateTime(DateTimeExtensions.toCalendar(zonedDateTime)) == zonedDateTime
    }

    @Test
    void timeZoneConvertsToZoneOffsetForCurrentAndSpecificInstants() {
        TimeZone fixedTimeZone = TimeZone.getTimeZone('GMT+05:30')
        assert DateTimeExtensions.toZoneOffset(fixedTimeZone) == ZoneOffset.ofHoursMinutes(5, 30)
        assert DateTimeExtensions.toZoneOffset(fixedTimeZone, Instant.parse('2024-01-01T00:00:00Z')) == ZoneOffset.ofHoursMinutes(5, 30)

        TimeZone utc = TimeZone.getTimeZone('UTC')
        assert DateTimeExtensions.toZoneOffset(utc) == ZoneOffset.UTC
        assert DateTimeExtensions.toZoneOffset(utc, Instant.parse('2024-07-20T14:15:16Z')) == ZoneOffset.UTC
    }
}
