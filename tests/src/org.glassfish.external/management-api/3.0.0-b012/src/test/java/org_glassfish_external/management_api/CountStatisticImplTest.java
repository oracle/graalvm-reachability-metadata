/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_external.management_api;

import org.glassfish.external.statistics.CountStatistic;
import org.glassfish.external.statistics.impl.CountStatisticImpl;
import org.glassfish.external.statistics.impl.StatisticImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CountStatisticImplTest {

    @Test
    void statisticProxyReportsUpdatedCountValues() {
        long startTime = System.currentTimeMillis();
        CountStatisticImpl statistic = new CountStatisticImpl(
                3L,
                "processed-messages",
                StatisticImpl.UNIT_COUNT,
                "Tracks processed messages",
                startTime,
                startTime);

        CountStatistic statisticView = statistic.getStatistic();

        assertThat(statisticView.getName()).isEqualTo("processed-messages");
        assertThat(statisticView.getUnit()).isEqualTo(StatisticImpl.UNIT_COUNT);
        assertThat(statisticView.getDescription()).isEqualTo("Tracks processed messages");
        assertThat(statisticView.getStartTime()).isEqualTo(startTime);
        assertThat(statisticView.getLastSampleTime()).isEqualTo(startTime);
        assertThat(statisticView.getCount()).isEqualTo(3L);

        statistic.increment();
        statistic.increment(6L);
        statistic.decrement();
        statistic.setCount(21L);

        assertThat(statisticView.getCount()).isEqualTo(21L);
        assertThat(statisticView.getLastSampleTime()).isGreaterThanOrEqualTo(startTime);
    }

    @Test
    void statisticProxyReflectsResetState() {
        long startTime = System.currentTimeMillis();
        CountStatisticImpl statistic = new CountStatisticImpl(
                8L,
                "active-sessions",
                StatisticImpl.UNIT_COUNT,
                "Tracks active sessions",
                startTime,
                startTime);
        CountStatistic statisticView = statistic.getStatistic();

        statistic.increment(5L);
        statistic.reset();

        assertThat(statisticView.getCount()).isEqualTo(8L);
        assertThat(statisticView.getLastSampleTime()).isEqualTo(-1L);
        assertThat(statisticView.getStartTime()).isGreaterThanOrEqualTo(startTime);
        assertThat(statisticView.toString())
                .contains("active-sessions")
                .contains("Tracks active sessions")
                .contains("Count: 8");
        assertThat(statistic.getStaticAsMap())
                .containsEntry("count", 8L)
                .containsEntry(StatisticImpl.LAST_SAMPLE_TIME, -1L);
    }
}
