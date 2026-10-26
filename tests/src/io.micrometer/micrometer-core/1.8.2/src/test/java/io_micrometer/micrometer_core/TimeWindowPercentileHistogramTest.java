/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TimeWindowPercentileHistogramTest {

    @Test
    void summaryPublishesClientSidePercentiles() {
        MeterRegistry registry = new SimpleMeterRegistry();
        DistributionSummary summary = DistributionSummary.builder("payload.size")
                .publishPercentiles(0.5, 0.9)
                .distributionStatisticBufferLength(3)
                .distributionStatisticExpiry(Duration.ofMinutes(1))
                .register(registry);

        for (int value = 1; value <= 100; value++) {
            summary.record(value);
        }

        HistogramSnapshot snapshot = summary.takeSnapshot();
        assertThat(snapshot.count()).isEqualTo(100);
        assertThat(snapshot.max()).isEqualTo(100.0);
        ValueAtPercentile[] percentiles = snapshot.percentileValues();
        assertThat(percentiles).hasSize(2);
        assertThat(percentiles[0].percentile()).isEqualTo(0.5);
        assertThat(percentiles[0].value()).isBetween(48.0, 52.0);
        assertThat(percentiles[1].percentile()).isEqualTo(0.9);
        assertThat(percentiles[1].value()).isBetween(88.0, 92.0);
    }

    @Test
    void summaryCountsServiceLevelObjectiveBuckets() {
        MeterRegistry registry = new SimpleMeterRegistry();
        DistributionSummary summary = DistributionSummary.builder("batch.size")
                .serviceLevelObjectives(50.0, 100.0)
                .register(registry);

        for (int value = 1; value <= 120; value++) {
            summary.record(value);
        }

        HistogramSnapshot snapshot = summary.takeSnapshot();
        assertThat(snapshot.histogramCounts()).hasSize(2);
        assertThat(snapshot.histogramCounts()[0].bucket()).isEqualTo(50.0);
        assertThat(snapshot.histogramCounts()[0].count()).isEqualTo(50.0);
        assertThat(snapshot.histogramCounts()[1].bucket()).isEqualTo(100.0);
        assertThat(snapshot.histogramCounts()[1].count()).isEqualTo(100.0);
    }

    @Test
    void timerPublishesHighPercentile() {
        MeterRegistry registry = new SimpleMeterRegistry();
        Timer timer = Timer.builder("request.latency")
                .publishPercentiles(0.99)
                .register(registry);

        timer.record(10, TimeUnit.MILLISECONDS);
        timer.record(20, TimeUnit.MILLISECONDS);

        HistogramSnapshot snapshot = timer.takeSnapshot();
        assertThat(snapshot.count()).isEqualTo(2);
        assertThat(snapshot.max(TimeUnit.MILLISECONDS)).isEqualTo(20.0);
        assertThat(snapshot.percentileValues()).singleElement()
                .satisfies(percentile -> assertThat(percentile.value(TimeUnit.MILLISECONDS)).isBetween(19.0, 21.0));
    }
}
