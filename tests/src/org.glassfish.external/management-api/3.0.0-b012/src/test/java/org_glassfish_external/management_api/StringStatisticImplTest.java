/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_external.management_api;

import org.glassfish.external.statistics.StringStatistic;
import org.glassfish.external.statistics.impl.StatisticImpl;
import org.glassfish.external.statistics.impl.StringStatisticImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StringStatisticImplTest {

    @Test
    void statisticProxyReportsUpdatedStringValues() {
        long startTime = System.currentTimeMillis();
        StringStatisticImpl statistic = new StringStatisticImpl(
                "CONNECTED",
                "connection-state",
                StatisticImpl.UNIT_COUNT,
                "Tracks connection state",
                startTime,
                startTime);

        StringStatistic statisticView = statistic.getStatistic();

        assertThat(statisticView).isNotSameAs(statistic);
        assertThat(statisticView.getName()).isEqualTo("connection-state");
        assertThat(statisticView.getUnit()).isEqualTo(StatisticImpl.UNIT_COUNT);
        assertThat(statisticView.getDescription()).isEqualTo("Tracks connection state");
        assertThat(statisticView.getStartTime()).isEqualTo(startTime);
        assertThat(statisticView.getLastSampleTime()).isEqualTo(startTime);
        assertThat(statisticView.getCurrent()).isEqualTo("CONNECTED");

        statistic.setCurrent("DISCONNECTED");

        assertThat(statisticView.getCurrent()).isEqualTo("DISCONNECTED");
        assertThat(statisticView.getLastSampleTime()).isGreaterThanOrEqualTo(startTime);
        assertThat(statistic.getStaticAsMap())
                .containsEntry("current", "DISCONNECTED")
                .containsEntry("name", "connection-state");
    }

    @Test
    void statisticProxyReflectsResetState() {
        long startTime = System.currentTimeMillis();
        StringStatisticImpl statistic = new StringStatisticImpl(
                "STARTING",
                "server-state",
                StatisticImpl.UNIT_COUNT,
                "Tracks server state",
                startTime,
                startTime);
        StringStatistic statisticView = statistic.getStatistic();

        statistic.setCurrent("RUNNING");
        statistic.reset();

        assertThat(statisticView.getCurrent()).isEqualTo("STARTING");
        assertThat(statisticView.getLastSampleTime()).isEqualTo(-1L);
        assertThat(statisticView.getStartTime()).isGreaterThanOrEqualTo(startTime);
        assertThat(statisticView.toString())
                .contains("server-state")
                .contains("Tracks server state")
                .contains("Current-value: STARTING");
        assertThat(statistic.getStaticAsMap())
                .containsEntry("current", "STARTING")
                .containsEntry(StatisticImpl.LAST_SAMPLE_TIME, -1L);
    }
}
