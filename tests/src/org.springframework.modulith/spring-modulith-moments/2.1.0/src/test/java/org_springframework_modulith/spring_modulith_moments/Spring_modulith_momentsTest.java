/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.spring_modulith_moments;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.moments.DayHasPassed;
import org.springframework.modulith.moments.HourHasPassed;
import org.springframework.modulith.moments.MonthHasPassed;
import org.springframework.modulith.moments.Quarter;
import org.springframework.modulith.moments.QuarterHasPassed;
import org.springframework.modulith.moments.WeekHasPassed;
import org.springframework.modulith.moments.YearHasPassed;
import org.springframework.modulith.moments.support.MomentsProperties;
import org.springframework.modulith.moments.support.TimeMachine;

public class Spring_modulith_momentsTest {

    @Test
    void publishesHourlyDailyAndWeeklyEventsWhenAWeekEnds() {
        MomentsProperties properties = MomentsProperties.DEFAULTS;
        WeekFields weekFields = WeekFields.of(properties.getLocale());
        LocalDate firstDayOfNextWeek = LocalDate.of(2025, 2, 10)
                .with(TemporalAdjusters.nextOrSame(weekFields.getFirstDayOfWeek()));
        LocalDate weekEnd = firstDayOfNextWeek.minusDays(1);
        LocalDateTime initialTime = weekEnd.atTime(23, 30);
        List<Object> events = new ArrayList<>();
        TimeMachine timeMachine = new TimeMachine(
                Clock.fixed(initialTime.toInstant(ZoneOffset.UTC), ZoneOffset.UTC), events::add, properties);

        timeMachine.shiftBy(Duration.ofHours(1));

        WeekHasPassed expectedWeek = WeekHasPassed.of(
                Year.of(weekEnd.get(weekFields.weekBasedYear())),
                weekEnd.get(weekFields.weekOfWeekBasedYear()),
                properties.getLocale());
        assertThat(events).contains(
                HourHasPassed.of(firstDayOfNextWeek.atStartOfDay()),
                DayHasPassed.of(weekEnd),
                expectedWeek);

        List<WeekHasPassed> weeklyEvents = events.stream()
                .filter(event -> event instanceof WeekHasPassed)
                .map(event -> (WeekHasPassed) event)
                .toList();
        assertThat(weeklyEvents).containsExactly(expectedWeek);
        assertThat(weeklyEvents.getFirst().getEndDate())
                .isEqualTo(weeklyEvents.getFirst().getStartDate().plusDays(6));
        assertThat(timeMachine.now()).isEqualTo(firstDayOfNextWeek.atTime(0, 30));
    }

    @Test
    void publishesMonthQuarterAndYearEventsWhenAYearEnds() {
        LocalDateTime initialTime = LocalDateTime.of(2024, 12, 31, 23, 30);
        List<Object> events = new ArrayList<>();
        TimeMachine timeMachine = new TimeMachine(
                Clock.fixed(initialTime.toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
                events::add,
                MomentsProperties.DEFAULTS);

        timeMachine.shiftBy(Duration.ofHours(1));

        assertThat(events).contains(
                HourHasPassed.of(LocalDateTime.of(2025, 1, 1, 0, 0)),
                DayHasPassed.of(LocalDate.of(2024, 12, 31)),
                MonthHasPassed.of(YearMonth.of(2024, 12)),
                QuarterHasPassed.of(Year.of(2024), Quarter.Q4),
                YearHasPassed.of(2024));
        assertThat(timeMachine.now()).isEqualTo(LocalDateTime.of(2025, 1, 1, 0, 30));
    }

    @Test
    void resetRestoresTheFixedClockAfterMultipleShifts() {
        LocalDateTime initialTime = LocalDateTime.of(2025, 4, 15, 10, 15);
        List<Object> events = new ArrayList<>();
        TimeMachine timeMachine = new TimeMachine(
                Clock.fixed(initialTime.toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
                events::add,
                MomentsProperties.DEFAULTS);

        assertThat(timeMachine.shiftBy(Duration.ofHours(2))).isSameAs(timeMachine);
        assertThat(timeMachine.shiftBy(Duration.ofMinutes(45))).isSameAs(timeMachine);
        assertThat(timeMachine.now()).isEqualTo(LocalDateTime.of(2025, 4, 15, 13, 0));

        assertThat(timeMachine.reset()).isSameAs(timeMachine);
        assertThat(timeMachine.now()).isEqualTo(initialTime);
    }
}
