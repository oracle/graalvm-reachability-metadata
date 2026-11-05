/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_threeten_threeten_extra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.threeten.extra.OffsetDate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises the offset-date subsystem to assign settlement dates across merchant time zones.
public class SettlementDateByOffsetTest {
    @Test
    @Timeout(60)
    public void assignsSettlementDatesFromOneCapturedInstant() {
        Instant capturedAt = Instant.parse("2025-01-15T23:30:00Z");
        OffsetDate merchantDate = OffsetDate.ofInstant(capturedAt, ZoneOffset.ofHours(-5));
        OffsetDate customerDate = OffsetDate.ofInstant(capturedAt, ZoneOffset.ofHours(9));
        OffsetDate nextProcessingDate = merchantDate.plusDays(1);

        assertEquals(LocalDate.of(2025, 1, 15), merchantDate.toLocalDate());
        assertEquals(ZoneOffset.ofHours(-5), merchantDate.getOffset());
        assertEquals(LocalDate.of(2025, 1, 16), customerDate.toLocalDate());
        assertTrue(merchantDate.isBefore(customerDate));

        OffsetDateTime processingAt = nextProcessingDate.atTime(LocalTime.of(9, 0));
        assertEquals("2025-01-16T09:00-05:00", processingAt.toString());
        assertEquals(
                OffsetDate.parse("2025-01-16-05:00"),
                nextProcessingDate.withOffsetSameLocal(ZoneOffset.ofHours(-5)));
    }
}
