/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_external.management_api;

import org.glassfish.external.statistics.AverageRangeStatistic;
import org.glassfish.external.statistics.impl.AverageRangeStatisticImpl;
import org.glassfish.external.statistics.impl.StatisticImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AverageRangeStatisticImplTest {

    @Test
    void statisticProxyReportsUpdatedAverageRangeValues() {
        long startTime = System.currentTimeMillis();
        AverageRangeStatisticImpl statistic = new AverageRangeStatisticImpl(
                10L,
                15L,
                5L,
                "request-count",
                StatisticImpl.UNIT_COUNT,
                "Tracks request counts",
                startTime,
                startTime);

        AverageRangeStatistic statisticView = statistic.getStatistic();
        assertThat(statisticView.getName()).isEqualTo("request-count");
        assertThat(statisticView.getUnit()).isEqualTo(StatisticImpl.UNIT_COUNT);
        assertThat(statisticView.getDescription()).isEqualTo("Tracks request counts");
        assertThat(statisticView.getStartTime()).isEqualTo(startTime);
        assertThat(statisticView.getCurrent()).isEqualTo(10L);
        assertThat(statisticView.getHighWaterMark()).isEqualTo(15L);
        assertThat(statisticView.getLowWaterMark()).isEqualTo(5L);
        assertThat(statisticView.getAverage()).isEqualTo(-1L);

        statistic.setCurrent(20L);
        statistic.setCurrent(4L);

        assertThat(statisticView.getCurrent()).isEqualTo(4L);
        assertThat(statisticView.getHighWaterMark()).isEqualTo(20L);
        assertThat(statisticView.getLowWaterMark()).isEqualTo(4L);
        assertThat(statisticView.getAverage()).isEqualTo(12L);
        assertThat(statisticView.getLastSampleTime()).isGreaterThanOrEqualTo(startTime);
    }

    @Test
    void statisticProxyReflectsResetState() {
        long startTime = System.currentTimeMillis();
        AverageRangeStatisticImpl statistic = new AverageRangeStatisticImpl(
                7L,
                9L,
                3L,
                "queue-depth",
                StatisticImpl.UNIT_COUNT,
                "Tracks queue depth",
                startTime,
                startTime);
        AverageRangeStatistic statisticView = statistic.getStatistic();

        statistic.setCurrent(11L);
        statistic.reset();

        assertThat(statisticView.getCurrent()).isEqualTo(7L);
        assertThat(statisticView.getHighWaterMark()).isEqualTo(9L);
        assertThat(statisticView.getLowWaterMark()).isEqualTo(3L);
        assertThat(statisticView.getAverage()).isEqualTo(-1L);
        assertThat(statisticView.getLastSampleTime()).isEqualTo(-1L);
        assertThat(statisticView.toString())
                .contains("queue-depth")
                .contains("Current: 7")
                .contains("Average:-1");
    }
}
