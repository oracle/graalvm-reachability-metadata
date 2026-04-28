/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_external.management_api;

import org.glassfish.external.statistics.BoundaryStatistic;
import org.glassfish.external.statistics.impl.BoundaryStatisticImpl;
import org.glassfish.external.statistics.impl.StatisticImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BoundaryStatisticImplTest {

    @Test
    void statisticProxyReportsBoundaryValues() {
        long startTime = System.currentTimeMillis();
        BoundaryStatisticImpl statistic = new BoundaryStatisticImpl(
                -5L,
                55L,
                "connection-boundary",
                StatisticImpl.UNIT_COUNT,
                "Tracks connection limits",
                startTime,
                startTime);

        BoundaryStatistic statisticView = statistic.getStatistic();

        assertThat(statisticView.getName()).isEqualTo("connection-boundary");
        assertThat(statisticView.getUnit()).isEqualTo(StatisticImpl.UNIT_COUNT);
        assertThat(statisticView.getDescription()).isEqualTo("Tracks connection limits");
        assertThat(statisticView.getStartTime()).isEqualTo(startTime);
        assertThat(statisticView.getLastSampleTime()).isEqualTo(startTime);
        assertThat(statisticView.getLowerBound()).isEqualTo(-5L);
        assertThat(statisticView.getUpperBound()).isEqualTo(55L);
        assertThat(statisticView.toString())
                .contains("connection-boundary")
                .contains("Tracks connection limits")
                .contains(StatisticImpl.UNIT_COUNT);
    }

    @Test
    void statisticProxyReflectsResetState() {
        long startTime = System.currentTimeMillis();
        BoundaryStatisticImpl statistic = new BoundaryStatisticImpl(
                10L,
                100L,
                "heap-boundary",
                StatisticImpl.UNIT_COUNT,
                "Tracks heap boundaries",
                startTime,
                startTime);
        BoundaryStatistic statisticView = statistic.getStatistic();

        statistic.reset();

        assertThat(statisticView.getLowerBound()).isEqualTo(10L);
        assertThat(statisticView.getUpperBound()).isEqualTo(100L);
        assertThat(statisticView.getLastSampleTime()).isEqualTo(-1L);
        assertThat(statisticView.getStartTime()).isGreaterThanOrEqualTo(startTime);
        assertThat(statistic.getStaticAsMap())
                .containsEntry("lowerbound", 10L)
                .containsEntry("upperbound", 100L)
                .containsEntry(StatisticImpl.LAST_SAMPLE_TIME, -1L);
    }
}
