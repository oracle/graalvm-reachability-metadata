/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_external.management_api;

import org.glassfish.external.statistics.TimeStatistic;
import org.glassfish.external.statistics.impl.StatisticImpl;
import org.glassfish.external.statistics.impl.TimeStatisticImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TimeStatisticImplTest {

    @Test
    void statisticProxyReportsInitialTimingValuesAndMetadata() {
        long startTime = 1_000L;
        long sampleTime = 1_500L;
        TimeStatisticImpl statistic = new TimeStatisticImpl(
                3L,
                40L,
                10L,
                75L,
                "request-latency",
                StatisticImpl.UNIT_MILLISECOND,
                "Tracks request latency",
                startTime,
                sampleTime);

        TimeStatistic statisticView = statistic.getStatistic();

        assertThat(statisticView).isNotSameAs(statistic);
        assertThat(statisticView.getName()).isEqualTo("request-latency");
        assertThat(statisticView.getUnit()).isEqualTo(StatisticImpl.UNIT_MILLISECOND);
        assertThat(statisticView.getDescription()).isEqualTo("Tracks request latency");
        assertThat(statisticView.getStartTime()).isEqualTo(startTime);
        assertThat(statisticView.getLastSampleTime()).isEqualTo(sampleTime);
        assertThat(statisticView.getCount()).isEqualTo(3L);
        assertThat(statisticView.getMaxTime()).isEqualTo(40L);
        assertThat(statisticView.getMinTime()).isEqualTo(10L);
        assertThat(statisticView.getTotalTime()).isEqualTo(75L);
    }

    @Test
    void statisticProxyReflectsTimingUpdatesAndReset() {
        long startTime = System.currentTimeMillis();
        TimeStatisticImpl statistic = new TimeStatisticImpl(
                0L,
                0L,
                0L,
                0L,
                "database-query-time",
                StatisticImpl.UNIT_MILLISECOND,
                "Tracks database query time",
                startTime,
                startTime);
        TimeStatistic statisticView = statistic.getStatistic();

        statistic.incrementCount(30L);
        statistic.incrementCount(12L);
        statistic.incrementCount(45L);

        assertThat(statisticView.getCount()).isEqualTo(3L);
        assertThat(statisticView.getMaxTime()).isEqualTo(45L);
        assertThat(statisticView.getMinTime()).isEqualTo(12L);
        assertThat(statisticView.getTotalTime()).isEqualTo(87L);
        assertThat(statisticView.getLastSampleTime()).isGreaterThanOrEqualTo(startTime);

        statistic.reset();

        assertThat(statisticView.getCount()).isEqualTo(0L);
        assertThat(statisticView.getMaxTime()).isEqualTo(0L);
        assertThat(statisticView.getMinTime()).isEqualTo(0L);
        assertThat(statisticView.getTotalTime()).isEqualTo(0L);
        assertThat(statisticView.getLastSampleTime()).isEqualTo(-1L);
        assertThat(statisticView.toString())
                .contains("database-query-time")
                .contains("Count: 0")
                .contains("MinTime: 0")
                .contains("MaxTime: 0")
                .contains("TotalTime: 0");
        assertThat(statistic.getStaticAsMap())
                .containsEntry("count", 0L)
                .containsEntry("maxtime", 0L)
                .containsEntry("mintime", 0L)
                .containsEntry("totaltime", 0L)
                .containsEntry(StatisticImpl.LAST_SAMPLE_TIME, -1L);
    }
}
