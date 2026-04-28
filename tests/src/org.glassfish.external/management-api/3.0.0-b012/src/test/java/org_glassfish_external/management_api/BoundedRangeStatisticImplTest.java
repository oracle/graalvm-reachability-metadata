/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_external.management_api;

import org.glassfish.external.statistics.BoundedRangeStatistic;
import org.glassfish.external.statistics.impl.BoundedRangeStatisticImpl;
import org.glassfish.external.statistics.impl.StatisticImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BoundedRangeStatisticImplTest {

    @Test
    void statisticProxyReportsUpdatedBoundedRangeValues() {
        long startTime = System.currentTimeMillis();
        BoundedRangeStatisticImpl statistic = new BoundedRangeStatisticImpl(
                40L,
                45L,
                35L,
                100L,
                10L,
                "thread-pool-size",
                StatisticImpl.UNIT_COUNT,
                "Tracks thread pool size",
                startTime,
                startTime);

        BoundedRangeStatistic statisticView = statistic.getStatistic();

        assertThat(statisticView.getName()).isEqualTo("thread-pool-size");
        assertThat(statisticView.getUnit()).isEqualTo(StatisticImpl.UNIT_COUNT);
        assertThat(statisticView.getDescription()).isEqualTo("Tracks thread pool size");
        assertThat(statisticView.getStartTime()).isEqualTo(startTime);
        assertThat(statisticView.getLastSampleTime()).isEqualTo(startTime);
        assertThat(statisticView.getCurrent()).isEqualTo(40L);
        assertThat(statisticView.getHighWaterMark()).isEqualTo(45L);
        assertThat(statisticView.getLowWaterMark()).isEqualTo(35L);
        assertThat(statisticView.getLowerBound()).isEqualTo(10L);
        assertThat(statisticView.getUpperBound()).isEqualTo(100L);

        statistic.setCurrent(55L);
        statistic.setCurrent(25L);

        assertThat(statisticView.getCurrent()).isEqualTo(25L);
        assertThat(statisticView.getHighWaterMark()).isEqualTo(55L);
        assertThat(statisticView.getLowWaterMark()).isEqualTo(25L);
        assertThat(statisticView.getLastSampleTime()).isGreaterThanOrEqualTo(startTime);
    }

    @Test
    void statisticProxyReflectsResetState() {
        long startTime = System.currentTimeMillis();
        BoundedRangeStatisticImpl statistic = new BoundedRangeStatisticImpl(
                12L,
                20L,
                8L,
                30L,
                5L,
                "connection-window",
                StatisticImpl.UNIT_COUNT,
                "Tracks connection window bounds",
                startTime,
                startTime);
        BoundedRangeStatistic statisticView = statistic.getStatistic();

        statistic.setCurrent(24L);
        statistic.setLowWaterMark(4L);
        statistic.setHighWaterMark(28L);
        statistic.reset();

        assertThat(statisticView.getCurrent()).isEqualTo(12L);
        assertThat(statisticView.getHighWaterMark()).isEqualTo(20L);
        assertThat(statisticView.getLowWaterMark()).isEqualTo(8L);
        assertThat(statisticView.getLowerBound()).isEqualTo(5L);
        assertThat(statisticView.getUpperBound()).isEqualTo(30L);
        assertThat(statisticView.getLastSampleTime()).isEqualTo(-1L);
        assertThat(statisticView.toString())
                .contains("connection-window")
                .contains("Current: 12")
                .contains("LowWaterMark: 8")
                .contains("HighWaterMark: 20")
                .contains("LowerBound: 5")
                .contains("UpperBound: 30");
        assertThat(statistic.getStaticAsMap())
                .containsEntry("current", 12L)
                .containsEntry("lowerbound", 5L)
                .containsEntry("upperbound", 30L)
                .containsEntry("lowwatermark", 8L)
                .containsEntry("highwatermark", 20L)
                .containsEntry(StatisticImpl.LAST_SAMPLE_TIME, -1L);
    }
}
