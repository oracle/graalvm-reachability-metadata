/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_instrumentation.opentelemetry_micrometer_1_5;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.NamingConvention;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistry;
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistryBuilder;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

public class Opentelemetry_micrometer_1_5Test {
    @Test
    void registryCreatesAndFindsCommonMicrometerMeterTypes() {
        MeterRegistry registry = OpenTelemetryMeterRegistry.create(OpenTelemetry.noop());
        try {
            registry.config().commonTags("service", "catalog");

            Counter counter = Counter.builder("requests.total")
                    .description("request counter")
                    .baseUnit("requests")
                    .tag("route", "list")
                    .register(registry);
            counter.increment();
            counter.increment(2.5);

            Timer timer = Timer.builder("requests.latency")
                    .description("request latency")
                    .tag("route", "list")
                    .register(registry);
            timer.record(150, TimeUnit.MILLISECONDS);
            timer.record(Duration.ofMillis(250));

            DistributionSummary summary = DistributionSummary.builder("payload.size")
                    .description("payload bytes")
                    .baseUnit("bytes")
                    .tag("route", "list")
                    .register(registry);
            summary.record(512);
            summary.record(1024);

            AtomicInteger queueDepth = new AtomicInteger(7);
            Gauge gauge = Gauge.builder("queue.depth", queueDepth, AtomicInteger::get)
                    .description("queued work")
                    .tag("queue", "primary")
                    .register(registry);
            queueDepth.set(11);

            LongTaskTimer longTaskTimer = LongTaskTimer.builder("batch.active")
                    .description("active batch work")
                    .tag("worker", "one")
                    .register(registry);
            LongTaskTimer.Sample sample = longTaskTimer.start();
            try {
                assertThat(longTaskTimer.activeTasks()).isEqualTo(1);
            } finally {
                sample.stop();
            }
            assertThat(longTaskTimer.activeTasks()).isZero();

            AtomicLong observedCount = new AtomicLong(4);
            FunctionCounter functionCounter = FunctionCounter.builder(
                            "cache.evictions", observedCount, AtomicLong::doubleValue)
                    .description("evictions observed from an object")
                    .tag("cache", "users")
                    .register(registry);

            AtomicLong invocations = new AtomicLong(5);
            AtomicLong totalMillis = new AtomicLong(1200);
            FunctionTimer functionTimer = FunctionTimer.builder(
                            "cache.load",
                            invocations,
                            AtomicLong::get,
                            AtomicLong::doubleValue,
                            TimeUnit.MILLISECONDS)
                    .description("cache load function timer")
                    .tag("cache", "users")
                    .register(registry);

            Collection<Meter> meters = registry.getMeters();
            assertThat(meters)
                    .contains(
                            counter,
                            timer,
                            summary,
                            gauge,
                            longTaskTimer,
                            functionCounter,
                            functionTimer)
                    .hasSize(7);
            assertThat(registry.find("requests.total").counter()).isSameAs(counter);
            assertThat(registry.find("requests.latency").timer()).isSameAs(timer);
            assertThat(registry.find("payload.size").summary()).isSameAs(summary);
            assertThat(registry.find("queue.depth").gauge()).isSameAs(gauge);
            assertThat(registry.find("batch.active").longTaskTimer()).isSameAs(longTaskTimer);
            assertThat(registry.find("cache.evictions").functionCounter())
                    .isSameAs(functionCounter);
            assertThat(registry.find("cache.load").functionTimer()).isSameAs(functionTimer);

            assertThat(counter.count()).isNaN();
            assertThat(gauge.value()).isNaN();
            assertThat(functionCounter.count()).isNaN();
            assertThat(functionTimer.count()).isNaN();
            assertThat(functionTimer.totalTime(TimeUnit.MILLISECONDS)).isNaN();
            assertThat(functionTimer.baseTimeUnit()).isEqualTo(TimeUnit.SECONDS);
        } finally {
            registry.close();
        }
    }

    @Test
    void builderOptionsEnableMicrometerHistogramMeasurements() {
        MeterRegistry registry = OpenTelemetryMeterRegistry.builder(OpenTelemetry.noop())
                .setClock(Clock.SYSTEM)
                .setBaseTimeUnit(TimeUnit.MILLISECONDS)
                .setMicrometerHistogramGaugesEnabled(true)
                .build();
        try {
            Timer timer = Timer.builder("histogram.timer")
                    .publishPercentileHistogram()
                    .register(registry);
            timer.record(100, TimeUnit.MILLISECONDS);
            timer.record(250, TimeUnit.MILLISECONDS);

            assertThat(timer.count()).isEqualTo(2);
            assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(350.0);
            assertThat(timer.max(TimeUnit.MILLISECONDS)).isEqualTo(250.0);

            DistributionSummary summary = DistributionSummary.builder("histogram.summary")
                    .publishPercentileHistogram()
                    .register(registry);
            summary.record(4.0);
            summary.record(6.5);

            assertThat(summary.count()).isEqualTo(2);
            assertThat(summary.totalAmount()).isEqualTo(10.5);
            assertThat(summary.max()).isEqualTo(6.5);

            FunctionTimer functionTimer = FunctionTimer.builder(
                            "histogram.function.timer",
                            new FunctionTimerState(3, 900),
                            FunctionTimerState::count,
                            FunctionTimerState::totalTimeMillis,
                            TimeUnit.MILLISECONDS)
                    .register(registry);
            assertThat(functionTimer.baseTimeUnit()).isEqualTo(TimeUnit.MILLISECONDS);
        } finally {
            registry.close();
        }
    }

    @Test
    void prometheusModeNamingConventionAddsPrometheusCompatibleUnitSuffixes() {
        MeterRegistry registry = OpenTelemetryMeterRegistry.builder(OpenTelemetry.noop())
                .setPrometheusMode(true)
                .build();
        try {
            NamingConvention namingConvention = registry.config().namingConvention();

            assertThat(namingConvention.name("orders.processed", Meter.Type.COUNTER, "items"))
                    .isEqualTo("orders.processed.items");
            assertThat(namingConvention.name(
                            "payload.size", Meter.Type.DISTRIBUTION_SUMMARY, "bytes"))
                    .isEqualTo("payload.size.bytes");
            assertThat(namingConvention.name("queue.depth", Meter.Type.GAUGE, ""))
                    .isEqualTo("queue.depth");
            assertThat(namingConvention.name("request.latency", Meter.Type.TIMER, null))
                    .isEqualTo("request.latency.seconds");
            assertThat(namingConvention.name("batch.active", Meter.Type.LONG_TASK_TIMER, "ignored"))
                    .isEqualTo("batch.active.seconds");
            assertThat(namingConvention.name("already.seconds", Meter.Type.TIMER, null))
                    .isEqualTo("already.seconds");
        } finally {
            registry.close();
        }
    }

    @Test
    void prometheusModeOverridesBaseTimeUnitAndRegistryRemovalClosesMeters() {
        OpenTelemetryMeterRegistryBuilder builder = OpenTelemetryMeterRegistry.builder(
                OpenTelemetry.noop());
        assertThat(builder.setClock(Clock.SYSTEM)).isSameAs(builder);
        assertThat(builder.setBaseTimeUnit(TimeUnit.NANOSECONDS)).isSameAs(builder);
        assertThat(builder.setPrometheusMode(true)).isSameAs(builder);
        assertThat(builder.setMicrometerHistogramGaugesEnabled(true)).isSameAs(builder);

        MeterRegistry registry = builder.build();
        try {
            FunctionTimer functionTimer = FunctionTimer.builder(
                            "prometheus.function.timer",
                            new FunctionTimerState(2, 5),
                            FunctionTimerState::count,
                            FunctionTimerState::totalTimeMillis,
                            TimeUnit.MILLISECONDS)
                    .register(registry);
            assertThat(functionTimer.baseTimeUnit()).isEqualTo(TimeUnit.SECONDS);

            Counter counter = registry.counter("removed.counter", "state", "before");
            Gauge gauge = Gauge.builder("removed.gauge", new AtomicInteger(3), AtomicInteger::get)
                    .register(registry);
            DistributionSummary summary = DistributionSummary.builder("removed.summary")
                    .publishPercentileHistogram()
                    .register(registry);
            Timer timer = Timer.builder("removed.timer")
                    .publishPercentileHistogram()
                    .register(registry);

            assertThat(registry.remove(counter)).isSameAs(counter);
            assertThat(registry.remove(gauge)).isSameAs(gauge);
            assertThat(registry.remove(summary)).isSameAs(summary);
            assertThat(registry.remove(timer)).isSameAs(timer);

            counter.increment(10.0);
            summary.record(2.0);
            timer.record(1, TimeUnit.MILLISECONDS);

            assertThat(registry.find("removed.counter").meter()).isNull();
            assertThat(registry.find("removed.gauge").meter()).isNull();
            assertThat(registry.find("removed.summary").meter()).isNull();
            assertThat(registry.find("removed.timer").meter()).isNull();
        } finally {
            registry.close();
        }
    }

    private static final class FunctionTimerState {
        private final long count;
        private final long totalTimeMillis;

        private FunctionTimerState(long count, long totalTimeMillis) {
            this.count = count;
            this.totalTimeMillis = totalTimeMillis;
        }

        long count() {
            return count;
        }

        double totalTimeMillis() {
            return totalTimeMillis;
        }
    }
}
