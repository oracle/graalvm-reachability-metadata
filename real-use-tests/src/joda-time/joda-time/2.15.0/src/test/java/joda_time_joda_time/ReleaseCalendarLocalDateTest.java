/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package joda_time_joda_time;

import org.joda.time.DateTimeConstants;
import org.joda.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Joda-Time's date-only calendar subsystem for planning a release review schedule.
public class ReleaseCalendarLocalDateTest {
    @Test
    void calculatesDateOnlyReleaseMilestonesWithoutAClockTime() {
        LocalDate codeFreeze = LocalDate.parse("2025-04-18");
        LocalDate releaseDate = codeFreeze.plusWeeks(2).withDayOfWeek(DateTimeConstants.FRIDAY);
        LocalDate reviewDeadline = releaseDate.minusDays(3);

        assertEquals(new LocalDate(2025, 4, 18), codeFreeze);
        assertEquals(new LocalDate(2025, 5, 2), releaseDate);
        assertEquals(DateTimeConstants.FRIDAY, releaseDate.getDayOfWeek());
        assertEquals(new LocalDate(2025, 4, 29), reviewDeadline);
        assertEquals(119, reviewDeadline.getDayOfYear());
    }
}
