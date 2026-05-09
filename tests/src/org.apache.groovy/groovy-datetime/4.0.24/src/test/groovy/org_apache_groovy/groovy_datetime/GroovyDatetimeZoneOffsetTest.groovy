/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_datetime

import org.apache.groovy.datetime.extensions.DateTimeExtensions
import org.junit.jupiter.api.Test

import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoField
import java.util.TimeZone

public class GroovyDatetimeZoneOffsetTest {
    @Test
    void exposesZoneOffsetComponentsAndFieldAccess() {
        ZoneOffset offset = ZoneOffset.ofHoursMinutesSeconds(-5, -30, -15)

        assert DateTimeExtensions.getHours(offset) == -5
        assert DateTimeExtensions.getMinutes(offset) == -30
        assert DateTimeExtensions.getSeconds(offset) == -15
        assert DateTimeExtensions.getAt(offset, ChronoField.OFFSET_SECONDS) == -19_815L
    }

    @Test
    void resolvesZoneOffsetsAtSpecificInstants() {
        Instant winter = Instant.parse('2024-01-15T00:00:00Z')
        Instant summer = Instant.parse('2024-07-15T00:00:00Z')
        ZoneId paris = ZoneId.of('Europe/Paris')
        TimeZone parisTimeZone = TimeZone.getTimeZone('Europe/Paris')

        assert DateTimeExtensions.getOffset(paris, winter) == ZoneOffset.ofHours(1)
        assert DateTimeExtensions.getOffset(paris, summer) == ZoneOffset.ofHours(2)
        assert DateTimeExtensions.toZoneOffset(parisTimeZone, winter) == ZoneOffset.ofHours(1)
        assert DateTimeExtensions.toZoneOffset(parisTimeZone, summer) == ZoneOffset.ofHours(2)
    }

    @Test
    void composesOffsetsWithLocalDateTimesAndLocalTimes() {
        ZoneOffset offset = ZoneOffset.ofHoursMinutes(5, 45)
        LocalDateTime dateTime = LocalDateTime.of(2024, 10, 31, 23, 45, 12)
        LocalTime time = LocalTime.of(23, 45, 12)

        assert DateTimeExtensions.leftShift(offset, dateTime) == OffsetDateTime.of(dateTime, offset)
        assert DateTimeExtensions.leftShift(offset, time) == OffsetTime.of(time, offset)
    }
}
