/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.scheduling.support.CronExpression;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Spring cron scheduling to preview monthly merchant settlement cutoffs in the merchant's time zone.
public class SettlementScheduleTest {

    @Test
    @Timeout(60)
    public void previewsLastWeekdayCutoffsAcrossLeapFebruaryAndDaylightSaving() {
        ZoneId merchantZone = ZoneId.of("Europe/Berlin");
        SettlementCalendar calendar = new SettlementCalendar("0 0 9 LW * *", merchantZone);
        ZonedDateTime opening = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, merchantZone);

        List<ZonedDateTime> cutoffs = calendar.upcomingCutoffs(opening, 6);

        assertEquals(List.of(
                ZonedDateTime.parse("2024-01-31T09:00:00+01:00[Europe/Berlin]"),
                ZonedDateTime.parse("2024-02-29T09:00:00+01:00[Europe/Berlin]"),
                ZonedDateTime.parse("2024-03-29T09:00:00+01:00[Europe/Berlin]"),
                ZonedDateTime.parse("2024-04-30T09:00:00+02:00[Europe/Berlin]"),
                ZonedDateTime.parse("2024-05-31T09:00:00+02:00[Europe/Berlin]"),
                ZonedDateTime.parse("2024-06-28T09:00:00+02:00[Europe/Berlin]")), cutoffs);

        // A settled cutoff must not be offered again when planning the next batch.
        assertEquals(cutoffs.subList(1, 4), calendar.upcomingCutoffs(cutoffs.getFirst(), 3));

        // The caller's own time zone does not change the merchant's settlement policy.
        assertEquals(cutoffs, calendar.upcomingCutoffs(opening.withZoneSameInstant(ZoneId.of("UTC")), 6));
    }

    public static final class SettlementCalendar {
        private final CronExpression schedule;
        private final ZoneId merchantZone;

        public SettlementCalendar(String schedule, ZoneId merchantZone) {
            this.schedule = CronExpression.parse(schedule);
            this.merchantZone = merchantZone;
        }

        public List<ZonedDateTime> upcomingCutoffs(ZonedDateTime after, int count) {
            List<ZonedDateTime> cutoffs = new ArrayList<>();
            ZonedDateTime cursor = after.withZoneSameInstant(merchantZone);
            for (int index = 0; index < count; index++) {
                cursor = schedule.next(cursor);
                if (cursor == null) {
                    throw new IllegalStateException("Settlement schedule has no further cutoff");
                }
                cutoffs.add(cursor);
            }
            return List.copyOf(cutoffs);
        }
    }
}
