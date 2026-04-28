/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_external.management_api;

import org.glassfish.external.statistics.RangeStatistic;
import org.glassfish.external.statistics.impl.RangeStatisticImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RangeStatisticImplTest {
    @Test
    void proxyStatisticExposesInitialRangeValuesAndMetadata() {
        long startTime = 1_000L;
        long sampleTime = 1_500L;
        RangeStatisticImpl rangeStatistic = new RangeStatisticImpl(
                10L,
                20L,
                5L,
                "active-connections",
                "count",
                "Active connection count",
                startTime,
                sampleTime);

        RangeStatistic proxyStatistic = rangeStatistic.getStatistic();

        assertThat(proxyStatistic).isNotSameAs(rangeStatistic);
        assertThat(proxyStatistic.getCurrent()).isEqualTo(10L);
        assertThat(proxyStatistic.getHighWaterMark()).isEqualTo(20L);
        assertThat(proxyStatistic.getLowWaterMark()).isEqualTo(5L);
        assertThat(proxyStatistic.getName()).isEqualTo("active-connections");
        assertThat(proxyStatistic.getUnit()).isEqualTo("count");
        assertThat(proxyStatistic.getDescription()).isEqualTo("Active connection count");
        assertThat(proxyStatistic.getStartTime()).isEqualTo(startTime);
        assertThat(proxyStatistic.getLastSampleTime()).isEqualTo(sampleTime);
    }

    @Test
    void proxyStatisticReflectsRangeUpdatesAndReset() {
        RangeStatisticImpl rangeStatistic = new RangeStatisticImpl(
                10L,
                20L,
                5L,
                "queue-depth",
                "count",
                "Queue depth",
                1_000L,
                1_500L);
        RangeStatistic proxyStatistic = rangeStatistic.getStatistic();

        rangeStatistic.setCurrent(2L);
        assertThat(proxyStatistic.getCurrent()).isEqualTo(2L);
        assertThat(proxyStatistic.getLowWaterMark()).isEqualTo(2L);
        assertThat(proxyStatistic.getHighWaterMark()).isEqualTo(20L);

        rangeStatistic.setCurrent(30L);
        assertThat(proxyStatistic.getCurrent()).isEqualTo(30L);
        assertThat(proxyStatistic.getLowWaterMark()).isEqualTo(2L);
        assertThat(proxyStatistic.getHighWaterMark()).isEqualTo(30L);

        rangeStatistic.reset();
        assertThat(proxyStatistic.getCurrent()).isEqualTo(10L);
        assertThat(proxyStatistic.getLowWaterMark()).isEqualTo(5L);
        assertThat(proxyStatistic.getHighWaterMark()).isEqualTo(20L);
        assertThat(proxyStatistic.getLastSampleTime()).isEqualTo(-1L);
    }
}
