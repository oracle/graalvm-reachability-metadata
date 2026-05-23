/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_datetime

import org.junit.jupiter.api.Test

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
import java.time.temporal.ChronoUnit

public class Groovy_datetimeTest {
    @Test
    void test() {
        println "This is just a placeholder, implement your test"
    }

    @Test
    void javaTimeTypesComposeWithLeftShiftOperator() {
        LocalDate date = LocalDate.of(2024, 7, 20)
        LocalTime time = LocalTime.of(14, 15, 16)
        LocalDateTime dateTime = LocalDateTime.of(date, time)
        ZoneOffset offset = ZoneOffset.ofHoursMinutes(5, 30)
        ZoneId zone = ZoneId.of('UTC')

        assert (date << time) == dateTime
        assert (time << date) == dateTime
        assert (date << OffsetTime.of(time, offset)) == OffsetDateTime.of(date, time, offset)
        assert (time << offset) == OffsetTime.of(time, offset)
        assert (dateTime << offset) == OffsetDateTime.of(dateTime, offset)
        assert (dateTime << zone) == ZonedDateTime.of(dateTime, zone)
        assert (zone << dateTime) == ZonedDateTime.of(dateTime, zone)
    }

    @Test
    void partialDateTypesComposeWithLeftShiftOperator() {
        Year year = Year.of(2024)
        Month month = Month.JULY
        MonthDay monthDay = MonthDay.of(month, 20)
        YearMonth yearMonth = YearMonth.of(year.value, month)

        assert (month << 20) == monthDay
        assert (month << year) == yearMonth
        assert (year << month) == yearMonth
        assert (year << monthDay) == LocalDate.of(2024, 7, 20)
        assert (yearMonth << 20) == LocalDate.of(2024, 7, 20)
        assert (monthDay << year) == LocalDate.of(2024, 7, 20)
        assert (monthDay << 2024) == LocalDate.of(2024, 7, 20)
    }

    @Test
    void javaTimeTypesParseWithCustomPatterns() {
        assert LocalDate.parse('20/07/2024', 'dd/MM/uuuu') == LocalDate.of(2024, 7, 20)
        assert LocalTime.parse('14-15-16', 'HH-mm-ss') == LocalTime.of(14, 15, 16)
        assert LocalDateTime.parse('2024/07/20 14:15:16', 'uuuu/MM/dd HH:mm:ss') ==
                LocalDateTime.of(2024, 7, 20, 14, 15, 16)
        assert OffsetTime.parse('14:15:16 +05:30', 'HH:mm:ss XXX') ==
                OffsetTime.of(14, 15, 16, 0, ZoneOffset.ofHoursMinutes(5, 30))
        assert OffsetDateTime.parse('2024-07-20 14:15:16 +05:30', 'uuuu-MM-dd HH:mm:ss XXX') ==
                OffsetDateTime.of(2024, 7, 20, 14, 15, 16, 0, ZoneOffset.ofHoursMinutes(5, 30))
        assert ZonedDateTime.parse('2024-07-20 14:15:16 UTC', 'uuuu-MM-dd HH:mm:ss VV') ==
                ZonedDateTime.of(2024, 7, 20, 14, 15, 16, 0, ZoneId.of('UTC'))
        assert Year.parse('Year 2024', "'Year' uuuu") == Year.of(2024)
        assert YearMonth.parse('2024-07', 'uuuu-MM') == YearMonth.of(2024, 7)
        assert MonthDay.parse('07/20', 'MM/dd') == MonthDay.of(7, 20)
    }

    @Test
    void temporalTypesIterateInclusivelyWithUptoAndDownto() {
        List<LocalDate> dates = []
        LocalDate.of(2024, 7, 20).upto(LocalDate.of(2024, 7, 22)) { LocalDate current ->
            dates << current
        }
        assert dates == [
                LocalDate.of(2024, 7, 20),
                LocalDate.of(2024, 7, 21),
                LocalDate.of(2024, 7, 22),
        ]

        LocalDateTime start = LocalDateTime.of(2024, 7, 20, 10, 0)
        List<LocalDateTime> dateTimes = []
        start.upto(start.plusHours(2), ChronoUnit.HOURS) { LocalDateTime current ->
            dateTimes << current
        }
        assert dateTimes == [
                LocalDateTime.of(2024, 7, 20, 10, 0),
                LocalDateTime.of(2024, 7, 20, 11, 0),
                LocalDateTime.of(2024, 7, 20, 12, 0),
        ]

        List<YearMonth> months = []
        YearMonth.of(2024, 9).downto(YearMonth.of(2024, 7)) { YearMonth current ->
            months << current
        }
        assert months == [
                YearMonth.of(2024, 9),
                YearMonth.of(2024, 8),
                YearMonth.of(2024, 7),
        ]
    }
}
