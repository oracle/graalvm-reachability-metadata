/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite_avatica.avatica_metrics;

import org.apache.calcite.avatica.metrics.Counter;
import org.apache.calcite.avatica.metrics.Gauge;
import org.apache.calcite.avatica.metrics.Histogram;
import org.apache.calcite.avatica.metrics.Meter;
import org.apache.calcite.avatica.metrics.MetricsSystem;
import org.apache.calcite.avatica.metrics.Timer;
import org.apache.calcite.avatica.metrics.Timer.Context;
import org.apache.calcite.avatica.metrics.noop.NoopMetricsSystem;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class NoopMetricsSystemTest {
    private final MetricsSystem metrics = NoopMetricsSystem.getInstance();

    @Test
    void acceptsCounterUpdatesWithoutAllocatingPerNameCounters() {
        Counter counter = metrics.getCounter("requests.active");

        assertDoesNotThrow(() -> {
            counter.increment();
            counter.increment(12L);
            counter.decrement();
            counter.decrement(4L);
        });
        assertSame(counter, metrics.getCounter("requests.completed"));
    }

    @Test
    void acceptsBothHistogramValueTypes() {
        Histogram histogram = metrics.getHistogram("response.bytes");

        assertDoesNotThrow(() -> {
            histogram.update(128);
            histogram.update(1L << 33);
        });
        assertSame(histogram, metrics.getHistogram("request.bytes"));
    }

    @Test
    void acceptsIndividualAndBatchedMeterEvents() {
        Meter meter = metrics.getMeter("requests.rate");

        assertDoesNotThrow(() -> {
            meter.mark();
            meter.mark(25L);
        });
        assertSame(meter, metrics.getMeter("responses.rate"));
    }

    @Test
    void supportsNestedAndRepeatedTimedOperations() {
        Timer timer = metrics.getTimer("request.duration");

        assertDoesNotThrow(() -> {
            try (Context outer = timer.start()) {
                assertNotNull(outer);
                try (Context inner = timer.start()) {
                    assertNotNull(inner);
                }
            }
            try (Context next = timer.start()) {
                assertNotNull(next);
            }
        });
        assertSame(timer, metrics.getTimer("response.duration"));
    }

    @Test
    void discardsGaugeRegistrationWithoutEvaluatingIt() {
        AtomicInteger evaluations = new AtomicInteger();
        Gauge<Integer> gauge = evaluations::incrementAndGet;

        metrics.register("queue.size", gauge);
        metrics.register("queue.size", gauge);

        assertEquals(0, evaluations.get());
    }
}
