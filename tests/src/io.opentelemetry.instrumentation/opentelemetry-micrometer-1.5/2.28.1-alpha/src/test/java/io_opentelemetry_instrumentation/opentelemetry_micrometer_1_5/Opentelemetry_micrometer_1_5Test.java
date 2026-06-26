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
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.NamingConvention;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.api.metrics.DoubleCounterBuilder;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.DoubleUpDownCounter;
import io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.metrics.ObservableDoubleCounter;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import io.opentelemetry.api.metrics.ObservableDoubleUpDownCounter;
import io.opentelemetry.api.metrics.ObservableLongCounter;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistry;
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistryBuilder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
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

    @Test
    void customMeterStatisticsCreateObservableOpenTelemetryInstruments() {
        CapturingOpenTelemetry openTelemetry = new CapturingOpenTelemetry();
        MeterRegistry registry = OpenTelemetryMeterRegistry.create(openTelemetry);
        try {
            AtomicReference<Double> observedValue = new AtomicReference<>(12.5);
            List<Measurement> measurements = Arrays.asList(
                    new Measurement(observedValue::get, Statistic.TOTAL),
                    new Measurement(observedValue::get, Statistic.ACTIVE_TASKS),
                    new Measurement(observedValue::get, Statistic.MAX));
            Meter meter = Meter.builder("custom.work", Meter.Type.OTHER, measurements)
                    .description("custom work meter")
                    .baseUnit("tasks")
                    .tag("queue", "priority")
                    .register(registry);

            assertThat(registry.find("custom.work").meter()).isSameAs(meter);
            assertThat(openTelemetry.registrations())
                    .extracting(registration -> registration.name)
                    .containsExactly("custom.work.total", "custom.work.active", "custom.work.max");
            assertThat(openTelemetry.registrations())
                    .extracting(registration -> registration.kind)
                    .containsExactly("counter", "upDownCounter", "gauge");

            Attributes expectedAttributes = Attributes.builder().put("queue", "priority").build();
            for (InstrumentRegistration registration : openTelemetry.registrations()) {
                assertThat(registration.description).isEqualTo("custom work meter");
                assertThat(registration.unit).isEqualTo("tasks");
                assertThat(registration.record()).isEqualTo(
                        new RecordedMeasurement(12.5, expectedAttributes));
            }

            observedValue.set(15.75);
            assertThat(openTelemetry.registrations())
                    .extracting(registration -> registration.record().value)
                    .containsExactly(15.75, 15.75, 15.75);

            assertThat(registry.remove(meter)).isSameAs(meter);
            assertThat(openTelemetry.registrations())
                    .extracting(registration -> registration.closed)
                    .containsExactly(true, true, true);
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

    private static final class CapturingOpenTelemetry implements OpenTelemetry {
        private final CapturingMeterProvider meterProvider = new CapturingMeterProvider();

        List<InstrumentRegistration> registrations() {
            return meterProvider.registrations;
        }

        @Override
        public TracerProvider getTracerProvider() {
            return TracerProvider.noop();
        }

        @Override
        public MeterProvider getMeterProvider() {
            return meterProvider;
        }

        @Override
        public ContextPropagators getPropagators() {
            return ContextPropagators.noop();
        }
    }

    private static final class CapturingMeterProvider implements MeterProvider {
        private final List<InstrumentRegistration> registrations = new ArrayList<>();

        @Override
        public MeterBuilder meterBuilder(String instrumentationScopeName) {
            return new MeterBuilder() {
                @Override
                public MeterBuilder setSchemaUrl(String schemaUrl) {
                    return this;
                }

                @Override
                public MeterBuilder setInstrumentationVersion(String instrumentationVersion) {
                    return this;
                }

                @Override
                public io.opentelemetry.api.metrics.Meter build() {
                    return new CapturingMeter(registrations);
                }
            };
        }
    }

    private static final class CapturingMeter implements io.opentelemetry.api.metrics.Meter {
        private final List<InstrumentRegistration> registrations;
        private final io.opentelemetry.api.metrics.Meter noopMeter =
                MeterProvider.noop().get("noop");

        private CapturingMeter(List<InstrumentRegistration> registrations) {
            this.registrations = registrations;
        }

        @Override
        public LongCounterBuilder counterBuilder(String name) {
            return new CapturingLongCounterBuilder(name, registrations, noopMeter);
        }

        @Override
        public LongUpDownCounterBuilder upDownCounterBuilder(String name) {
            return new CapturingLongUpDownCounterBuilder(name, registrations, noopMeter);
        }

        @Override
        public DoubleHistogramBuilder histogramBuilder(String name) {
            return noopMeter.histogramBuilder(name);
        }

        @Override
        public DoubleGaugeBuilder gaugeBuilder(String name) {
            return new CapturingDoubleGaugeBuilder(name, registrations, noopMeter);
        }
    }

    private static final class CapturingLongCounterBuilder implements LongCounterBuilder {
        private final String name;
        private final List<InstrumentRegistration> registrations;
        private final io.opentelemetry.api.metrics.Meter noopMeter;
        private String description = "";
        private String unit = "";

        private CapturingLongCounterBuilder(
                String name,
                List<InstrumentRegistration> registrations,
                io.opentelemetry.api.metrics.Meter noopMeter) {
            this.name = name;
            this.registrations = registrations;
            this.noopMeter = noopMeter;
        }

        @Override
        public LongCounterBuilder setDescription(String description) {
            this.description = description;
            return this;
        }

        @Override
        public LongCounterBuilder setUnit(String unit) {
            this.unit = unit;
            return this;
        }

        @Override
        public DoubleCounterBuilder ofDoubles() {
            return new CapturingDoubleCounterBuilder(
                    name, registrations, description, unit, noopMeter);
        }

        @Override
        public LongCounter build() {
            return noopMeter.counterBuilder(name).build();
        }

        @Override
        public ObservableLongCounter buildWithCallback(
                Consumer<ObservableLongMeasurement> callback) {
            return noopMeter.counterBuilder(name).buildWithCallback(callback);
        }
    }

    private static final class CapturingDoubleCounterBuilder implements DoubleCounterBuilder {
        private final String name;
        private final List<InstrumentRegistration> registrations;
        private final io.opentelemetry.api.metrics.Meter noopMeter;
        private String description;
        private String unit;

        private CapturingDoubleCounterBuilder(
                String name,
                List<InstrumentRegistration> registrations,
                String description,
                String unit,
                io.opentelemetry.api.metrics.Meter noopMeter) {
            this.name = name;
            this.registrations = registrations;
            this.description = description;
            this.unit = unit;
            this.noopMeter = noopMeter;
        }

        @Override
        public DoubleCounterBuilder setDescription(String description) {
            this.description = description;
            return this;
        }

        @Override
        public DoubleCounterBuilder setUnit(String unit) {
            this.unit = unit;
            return this;
        }

        @Override
        public DoubleCounter build() {
            return noopMeter.counterBuilder(name).ofDoubles().build();
        }

        @Override
        public ObservableDoubleCounter buildWithCallback(
                Consumer<ObservableDoubleMeasurement> callback) {
            InstrumentRegistration registration = new InstrumentRegistration(
                    "counter", name, description, unit, callback);
            registrations.add(registration);
            return new ObservableDoubleCounter() {
                @Override
                public void close() {
                    registration.close();
                }
            };
        }
    }

    private static final class CapturingLongUpDownCounterBuilder
            implements LongUpDownCounterBuilder {
        private final String name;
        private final List<InstrumentRegistration> registrations;
        private final io.opentelemetry.api.metrics.Meter noopMeter;
        private String description = "";
        private String unit = "";

        private CapturingLongUpDownCounterBuilder(
                String name,
                List<InstrumentRegistration> registrations,
                io.opentelemetry.api.metrics.Meter noopMeter) {
            this.name = name;
            this.registrations = registrations;
            this.noopMeter = noopMeter;
        }

        @Override
        public LongUpDownCounterBuilder setDescription(String description) {
            this.description = description;
            return this;
        }

        @Override
        public LongUpDownCounterBuilder setUnit(String unit) {
            this.unit = unit;
            return this;
        }

        @Override
        public DoubleUpDownCounterBuilder ofDoubles() {
            return new CapturingDoubleUpDownCounterBuilder(
                    name, registrations, description, unit, noopMeter);
        }

        @Override
        public LongUpDownCounter build() {
            return noopMeter.upDownCounterBuilder(name).build();
        }

        @Override
        public ObservableLongUpDownCounter buildWithCallback(
                Consumer<ObservableLongMeasurement> callback) {
            return noopMeter.upDownCounterBuilder(name).buildWithCallback(callback);
        }
    }

    private static final class CapturingDoubleUpDownCounterBuilder
            implements DoubleUpDownCounterBuilder {
        private final String name;
        private final List<InstrumentRegistration> registrations;
        private final io.opentelemetry.api.metrics.Meter noopMeter;
        private String description;
        private String unit;

        private CapturingDoubleUpDownCounterBuilder(
                String name,
                List<InstrumentRegistration> registrations,
                String description,
                String unit,
                io.opentelemetry.api.metrics.Meter noopMeter) {
            this.name = name;
            this.registrations = registrations;
            this.description = description;
            this.unit = unit;
            this.noopMeter = noopMeter;
        }

        @Override
        public DoubleUpDownCounterBuilder setDescription(String description) {
            this.description = description;
            return this;
        }

        @Override
        public DoubleUpDownCounterBuilder setUnit(String unit) {
            this.unit = unit;
            return this;
        }

        @Override
        public DoubleUpDownCounter build() {
            return noopMeter.upDownCounterBuilder(name).ofDoubles().build();
        }

        @Override
        public ObservableDoubleUpDownCounter buildWithCallback(
                Consumer<ObservableDoubleMeasurement> callback) {
            InstrumentRegistration registration = new InstrumentRegistration(
                    "upDownCounter", name, description, unit, callback);
            registrations.add(registration);
            return new ObservableDoubleUpDownCounter() {
                @Override
                public void close() {
                    registration.close();
                }
            };
        }
    }

    private static final class CapturingDoubleGaugeBuilder implements DoubleGaugeBuilder {
        private final String name;
        private final List<InstrumentRegistration> registrations;
        private final io.opentelemetry.api.metrics.Meter noopMeter;
        private String description = "";
        private String unit = "";

        private CapturingDoubleGaugeBuilder(
                String name,
                List<InstrumentRegistration> registrations,
                io.opentelemetry.api.metrics.Meter noopMeter) {
            this.name = name;
            this.registrations = registrations;
            this.noopMeter = noopMeter;
        }

        @Override
        public DoubleGaugeBuilder setDescription(String description) {
            this.description = description;
            return this;
        }

        @Override
        public DoubleGaugeBuilder setUnit(String unit) {
            this.unit = unit;
            return this;
        }

        @Override
        public io.opentelemetry.api.metrics.LongGaugeBuilder ofLongs() {
            return noopMeter.gaugeBuilder(name).ofLongs();
        }

        @Override
        public ObservableDoubleGauge buildWithCallback(
                Consumer<ObservableDoubleMeasurement> callback) {
            InstrumentRegistration registration = new InstrumentRegistration(
                    "gauge", name, description, unit, callback);
            registrations.add(registration);
            return new ObservableDoubleGauge() {
                @Override
                public void close() {
                    registration.close();
                }
            };
        }
    }

    private static final class InstrumentRegistration {
        private final String kind;
        private final String name;
        private final String description;
        private final String unit;
        private final Consumer<ObservableDoubleMeasurement> callback;
        private boolean closed;

        private InstrumentRegistration(
                String kind,
                String name,
                String description,
                String unit,
                Consumer<ObservableDoubleMeasurement> callback) {
            this.kind = kind;
            this.name = name;
            this.description = description;
            this.unit = unit;
            this.callback = callback;
        }

        private RecordedMeasurement record() {
            RecordingMeasurement measurement = new RecordingMeasurement();
            callback.accept(measurement);
            return measurement.recordedMeasurement;
        }

        private void close() {
            closed = true;
        }
    }

    private static final class RecordingMeasurement implements ObservableDoubleMeasurement {
        private RecordedMeasurement recordedMeasurement;

        @Override
        public void record(double value) {
            recordedMeasurement = new RecordedMeasurement(value, Attributes.empty());
        }

        @Override
        public void record(double value, Attributes attributes) {
            recordedMeasurement = new RecordedMeasurement(value, attributes);
        }
    }

    private static final class RecordedMeasurement {
        private final double value;
        private final Attributes attributes;

        private RecordedMeasurement(double value, Attributes attributes) {
            this.value = value;
            this.attributes = attributes;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof RecordedMeasurement)) {
                return false;
            }
            RecordedMeasurement that = (RecordedMeasurement) obj;
            return Double.compare(that.value, value) == 0 && attributes.equals(that.attributes);
        }

        @Override
        public int hashCode() {
            int result = Double.hashCode(value);
            result = 31 * result + attributes.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "RecordedMeasurement{" + "value=" + value + ", attributes=" + attributes + '}';
        }
    }
}
