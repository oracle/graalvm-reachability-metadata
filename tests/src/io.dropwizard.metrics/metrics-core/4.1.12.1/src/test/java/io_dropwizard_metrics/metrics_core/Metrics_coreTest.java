/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_dropwizard_metrics.metrics_core;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.codahale.metrics.CachedGauge;
import com.codahale.metrics.Clock;
import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Counter;
import com.codahale.metrics.CsvReporter;
import com.codahale.metrics.DerivativeGauge;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.InstrumentedExecutorService;
import com.codahale.metrics.InstrumentedScheduledExecutorService;
import com.codahale.metrics.InstrumentedThreadFactory;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricAttribute;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricRegistryListener;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.RatioGauge;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.SlidingTimeWindowReservoir;
import com.codahale.metrics.SlidingWindowReservoir;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.codahale.metrics.UniformSnapshot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Metrics_coreTest {
    @Test
    void metricRegistryBuildsNamesRegistersMetricSetsAndNotifiesListeners() {
        MetricRegistry registry = new MetricRegistry();
        RecordingMetricRegistryListener listener = new RecordingMetricRegistryListener();
        AtomicInteger createdGauges = new AtomicInteger();
        MetricRegistry.MetricSupplier<Gauge> queueDepthSupplier = new MetricRegistry.MetricSupplier<Gauge>() {
            @Override
            public Gauge newMetric() {
                createdGauges.incrementAndGet();
                return (Gauge<Integer>) () -> 42;
            }
        };

        registry.addListener(listener);

        Gauge<?> queueDepth = registry.gauge("queue.depth", queueDepthSupplier);
        Gauge<?> sameQueueDepth = registry.gauge("queue.depth", queueDepthSupplier);
        Counter requests = registry.counter("requests");
        Meter throughput = registry.meter("throughput");

        Map<String, Metric> nestedMetrics = new TreeMap<>();
        nestedMetrics.put("size", new Histogram(new SlidingWindowReservoir(3)));
        Map<String, Metric> serviceMetrics = new TreeMap<>();
        serviceMetrics.put("latency", new Timer());
        serviceMetrics.put("nested", new StaticMetricSet(nestedMetrics));
        registry.registerAll("service", new StaticMetricSet(serviceMetrics));

        requests.inc(2);
        throughput.mark(3);

        assertThat(MetricRegistry.name("root", "child", null, "leaf")).isEqualTo("root.child.leaf");
        assertThat(MetricRegistry.name(Metrics_coreTest.class, "requests", null, "total"))
                .isEqualTo("io_dropwizard_metrics.metrics_core.Metrics_coreTest.requests.total");
        assertThat(queueDepth.getValue()).isEqualTo(42);
        assertThat(sameQueueDepth).isSameAs(queueDepth);
        assertThat(createdGauges).hasValue(1);
        assertThat(registry.getNames())
                .contains("queue.depth", "requests", "throughput", "service.latency", "service.nested.size");
        assertThat(registry.getCounters(MetricFilter.startsWith("req"))).containsOnlyKeys("requests");
        assertThat(registry.getGauges(MetricFilter.endsWith("depth"))).containsOnlyKeys("queue.depth");
        assertThat(registry.getHistograms(MetricFilter.contains("nested"))).containsOnlyKeys("service.nested.size");
        assertThat(listener.added)
                .contains("gauge:queue.depth", "counter:requests", "meter:throughput", "timer:service.latency",
                        "histogram:service.nested.size");

        registry.removeMatching(MetricFilter.startsWith("service"));
        assertThat(registry.remove("requests")).isTrue();
        assertThat(registry.remove("missing")).isFalse();
        assertThat(registry.getNames()).contains("queue.depth", "throughput").doesNotContain("requests",
                "service.latency", "service.nested.size");
        assertThat(listener.removed)
                .contains("timer:service.latency", "histogram:service.nested.size", "counter:requests");
    }

    @Test
    void countersMetersHistogramsAndTimersTrackMeasurements() {
        Counter counter = new Counter();
        counter.inc();
        counter.inc(4);
        counter.dec(2);

        Meter meter = new Meter();
        meter.mark();
        meter.mark(2);

        Histogram histogram = new Histogram(new SlidingWindowReservoir(3));
        histogram.update(10);
        histogram.update(20);
        histogram.update(30);
        histogram.update(40);

        StepClock clock = new StepClock();
        Timer timer = new Timer(new SlidingWindowReservoir(5), clock);
        String timedValue = timer.timeSupplier(() -> {
            clock.addNanos(3);
            return "done";
        });
        timer.time(() -> clock.addNanos(7));
        Timer.Context context = timer.time();
        clock.addNanos(11);
        long stoppedDuration = context.stop();
        timer.update(Duration.ofNanos(13));

        Snapshot histogramSnapshot = histogram.getSnapshot();
        Snapshot timerSnapshot = timer.getSnapshot();

        assertThat(counter.getCount()).isEqualTo(3);
        assertThat(meter.getCount()).isEqualTo(3);
        assertThat(meter.getMeanRate()).isGreaterThanOrEqualTo(0.0d);
        assertThat(histogram.getCount()).isEqualTo(4);
        assertThat(histogramSnapshot.getValues()).containsExactly(20L, 30L, 40L);
        assertThat(histogramSnapshot.getMin()).isEqualTo(20L);
        assertThat(histogramSnapshot.getMax()).isEqualTo(40L);
        assertThat(timedValue).isEqualTo("done");
        assertThat(stoppedDuration).isEqualTo(11L);
        assertThat(timer.getCount()).isEqualTo(4L);
        assertThat(timer.getMeanRate()).isGreaterThanOrEqualTo(0.0d);
        assertThat(timerSnapshot.getValues()).containsExactly(3L, 7L, 11L, 13L);
        assertThat(timerSnapshot.getMin()).isEqualTo(3L);
        assertThat(timerSnapshot.getMax()).isEqualTo(13L);
    }

    @Test
    void gaugesCacheTransformAndComputeRatios() {
        StepClock clock = new StepClock();
        AtomicInteger loads = new AtomicInteger();

        CachedGauge<Integer> cachedGauge = new CachedGauge<Integer>(clock, 10, TimeUnit.NANOSECONDS) {
            @Override
            protected Integer loadValue() {
                return loads.incrementAndGet();
            }
        };
        DerivativeGauge<String, Integer> lengthGauge = new DerivativeGauge<String, Integer>(() -> "dropwizard") {
            @Override
            protected Integer transform(String value) {
                return value.length();
            }
        };
        RatioGauge successRatio = new RatioGauge() {
            @Override
            protected Ratio getRatio() {
                return Ratio.of(3, 4);
            }
        };

        assertThat(cachedGauge.getValue()).isEqualTo(1);
        clock.addNanos(5);
        assertThat(cachedGauge.getValue()).isEqualTo(1);
        clock.addNanos(6);
        assertThat(cachedGauge.getValue()).isEqualTo(2);
        assertThat(loads).hasValue(2);
        assertThat(lengthGauge.getValue()).isEqualTo(10);
        assertThat(successRatio.getValue()).isEqualTo(0.75d);
        assertThat(RatioGauge.Ratio.of(9, 12).getValue()).isEqualTo(0.75d);
    }

    @Test
    void reservoirsAndSnapshotsRetainExpectedSamples() throws Exception {
        StepClock clock = new StepClock();
        SlidingTimeWindowReservoir timeWindowReservoir = new SlidingTimeWindowReservoir(10, TimeUnit.NANOSECONDS,
                clock);

        timeWindowReservoir.update(5);
        clock.addNanos(9);
        timeWindowReservoir.update(9);
        clock.addNanos(2);

        Snapshot prunedSnapshot = timeWindowReservoir.getSnapshot();
        UniformSnapshot uniformSnapshot = new UniformSnapshot(new long[] {4, 1, 3, 2 });
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        uniformSnapshot.dump(output);

        assertThat(prunedSnapshot.getValues()).containsExactly(9L);
        clock.addNanos(1);
        timeWindowReservoir.update(12);
        assertThat(timeWindowReservoir.getSnapshot().getValues()).containsExactly(9L, 12L);
        assertThat(uniformSnapshot.size()).isEqualTo(4);
        assertThat(uniformSnapshot.getValues()).containsExactly(1L, 2L, 3L, 4L);
        assertThat(uniformSnapshot.getMedian()).isEqualTo(2.5d);
        assertThat(uniformSnapshot.get75thPercentile()).isEqualTo(3.75d);
        assertThat(output.toString(StandardCharsets.UTF_8)).isEqualTo("1\n2\n3\n4\n");
    }

    @Test
    void sharedMetricRegistriesReuseNamedRegistriesAndTrackDefaultRegistry() {
        SharedMetricRegistries.clear();

        MetricRegistry sharedRegistry = new MetricRegistry();
        MetricRegistry defaultRegistry = new MetricRegistry();

        assertThat(SharedMetricRegistries.add("shared", sharedRegistry)).isNull();
        assertThat(SharedMetricRegistries.add("shared", new MetricRegistry())).isSameAs(sharedRegistry);
        assertThat(SharedMetricRegistries.getOrCreate("shared")).isSameAs(sharedRegistry);

        MetricRegistry createdRegistry = SharedMetricRegistries.getOrCreate("created");
        assertThat(createdRegistry).isNotNull();
        assertThat(SharedMetricRegistries.names()).contains("shared", "created");

        SharedMetricRegistries.remove("created");
        assertThat(SharedMetricRegistries.names()).contains("shared").doesNotContain("created");
        assertThat(SharedMetricRegistries.tryGetDefault()).isNull();
        assertThat(SharedMetricRegistries.setDefault("default", defaultRegistry)).isSameAs(defaultRegistry);
        assertThat(SharedMetricRegistries.getDefault()).isSameAs(defaultRegistry);
        assertThat(SharedMetricRegistries.tryGetDefault()).isSameAs(defaultRegistry);
        assertThatThrownBy(() -> SharedMetricRegistries.setDefault("other"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already set");
    }

    @Test
    void consoleReporterRendersMetricsAndSkipsDisabledRateAttributes() {
        MetricRegistry registry = new MetricRegistry();
        registry.register("reporting.gauge", (Gauge<Integer>) () -> 42);

        Counter counter = registry.counter("reporting.counter");
        counter.inc(7);

        Histogram histogram = registry.histogram("reporting.histogram");
        histogram.update(10);
        histogram.update(20);

        Meter meter = registry.meter("reporting.meter");
        meter.mark(3);

        Timer timer = registry.timer("reporting.timer");
        timer.update(Duration.ofMillis(5));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ConsoleReporter reporter = ConsoleReporter.forRegistry(registry)
                .outputTo(new PrintStream(output, true, StandardCharsets.UTF_8))
                .formattedFor(Locale.US)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .disabledMetricAttributes(EnumSet.of(MetricAttribute.M1_RATE, MetricAttribute.M5_RATE,
                        MetricAttribute.M15_RATE))
                .build();

        reporter.report();

        String report = output.toString(StandardCharsets.UTF_8);
        assertThat(report).contains("reporting.gauge", "value = 42");
        assertThat(report).contains("reporting.counter", "count = 7");
        assertThat(report).contains("reporting.histogram", "max = 20");
        assertThat(report).contains("reporting.meter", "mean rate =");
        assertThat(report).contains("reporting.timer", "min = 5.00 milliseconds", "max = 5.00 milliseconds");
        assertThat(report).doesNotContain("1-minute rate", "5-minute rate", "15-minute rate");
    }

    @Test
    void csvReporterWritesFilteredMetricsAndAppendsSamplesAcrossReports() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        Counter counter = registry.counter("csv.counter");
        counter.inc(5);

        Timer timer = registry.timer("csv.timer");
        timer.update(Duration.ofMillis(8));
        registry.register("skip.gauge", (Gauge<Integer>) () -> 99);

        StepClock clock = new StepClock();
        clock.addNanos(TimeUnit.SECONDS.toNanos(123));
        Path reportDirectory = Files.createTempDirectory("metrics-core-csv-");

        CsvReporter reporter = CsvReporter.forRegistry(registry)
                .withClock(clock)
                .filter((name, metric) -> name.startsWith("csv."))
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build(reportDirectory.toFile());
        reporter.report();

        counter.inc(2);
        timer.update(Duration.ofMillis(10));
        clock.addNanos(TimeUnit.SECONDS.toNanos(1));
        reporter.report();

        List<String> reportFiles;
        try (Stream<Path> paths = Files.list(reportDirectory)) {
            reportFiles = paths.map(path -> path.getFileName().toString()).sorted().toList();
        }

        Path counterReport = reportDirectory.resolve("csv.counter.csv");
        Path timerReport = reportDirectory.resolve("csv.timer.csv");
        List<String> counterLines = Files.readAllLines(counterReport, StandardCharsets.UTF_8);
        List<String> timerLines = Files.readAllLines(timerReport, StandardCharsets.UTF_8);

        assertThat(reportFiles).containsExactly("csv.counter.csv", "csv.timer.csv");
        assertThat(counterLines).containsExactly("t,count", "123,5", "124,7");
        assertThat(timerLines)
                .hasSize(3)
                .first().isEqualTo("t,count,max,mean,min,stddev,p50,p75,p95,p98,p99,p999,mean_rate,m1_rate,m5_rate,m15_rate,rate_unit,duration_unit");
        assertThat(timerLines.get(1)).startsWith("123,1,8.000000,8.000000,8.000000,0.000000,8.000000,8.000000")
                .endsWith(",calls/second,milliseconds");
        assertThat(timerLines.get(2)).startsWith("124,2,10.000000,9.000000,8.000000,1.000000,10.000000,10.000000")
                .endsWith(",calls/second,milliseconds");
        assertThat(Files.exists(reportDirectory.resolve("skip.gauge.csv"))).isFalse();
        reporter.stop();
    }

    @Test
    void instrumentedConcurrencyComponentsPublishMetrics() throws Exception {
        MetricRegistry executorRegistry = new MetricRegistry();
        CountDownLatch executorLatch = new CountDownLatch(2);
        ExecutorService delegateExecutor = Executors.newFixedThreadPool(1);
        InstrumentedExecutorService executor = new InstrumentedExecutorService(delegateExecutor, executorRegistry,
                "workers");

        Future<Integer> submittedValue = executor.submit(() -> {
            executorLatch.countDown();
            return 7;
        });
        executor.execute(executorLatch::countDown);

        assertThat(submittedValue.get(5, TimeUnit.SECONDS)).isEqualTo(7);
        assertThat(executorLatch.await(5, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(executorRegistry.getMeters().get("workers.submitted").getCount()).isGreaterThanOrEqualTo(2L);
        assertThat(executorRegistry.getMeters().get("workers.completed").getCount()).isGreaterThanOrEqualTo(2L);
        assertThat(executorRegistry.getTimers().get("workers.duration").getCount()).isGreaterThanOrEqualTo(2L);
        assertThat(executorRegistry.getTimers().get("workers.idle").getCount()).isGreaterThanOrEqualTo(1L);
        assertThat(executorRegistry.getCounters().get("workers.running").getCount()).isZero();

        MetricRegistry scheduledRegistry = new MetricRegistry();
        CountDownLatch scheduledLatch = new CountDownLatch(2);
        ScheduledExecutorService delegateScheduler = Executors.newSingleThreadScheduledExecutor();
        InstrumentedScheduledExecutorService scheduler = new InstrumentedScheduledExecutorService(delegateScheduler,
                scheduledRegistry, "scheduler");

        ScheduledFuture<String> scheduledValue = scheduler.schedule(() -> {
            scheduledLatch.countDown();
            return "ready";
        }, 5, TimeUnit.MILLISECONDS);
        assertThat(scheduledValue.get(5, TimeUnit.SECONDS)).isEqualTo("ready");

        ScheduledFuture<?> periodicTask = scheduler.scheduleAtFixedRate(scheduledLatch::countDown, 0, 5,
                TimeUnit.MILLISECONDS);
        assertThat(scheduledLatch.await(5, TimeUnit.SECONDS)).isTrue();
        periodicTask.cancel(true);

        Future<Integer> submittedScheduledValue = scheduler.submit(() -> 9);
        assertThat(submittedScheduledValue.get(5, TimeUnit.SECONDS)).isEqualTo(9);
        scheduler.shutdown();
        assertThat(scheduler.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(scheduledRegistry.getMeters().get("scheduler.scheduled.once").getCount()).isEqualTo(1L);
        assertThat(scheduledRegistry.getMeters().get("scheduler.scheduled.repetitively").getCount()).isEqualTo(1L);
        assertThat(scheduledRegistry.getMeters().get("scheduler.submitted").getCount()).isGreaterThanOrEqualTo(1L);
        assertThat(scheduledRegistry.getMeters().get("scheduler.completed").getCount()).isGreaterThanOrEqualTo(3L);
        assertThat(scheduledRegistry.getTimers().get("scheduler.duration").getCount()).isGreaterThanOrEqualTo(3L);
        assertThat(scheduledRegistry.getHistograms().get("scheduler.scheduled.percent-of-period").getCount())
                .isGreaterThanOrEqualTo(1L);
        assertThat(scheduledRegistry.getCounters().get("scheduler.scheduled.overrun").getCount())
                .isGreaterThanOrEqualTo(0L);
        assertThat(scheduledRegistry.getCounters().get("scheduler.running").getCount()).isZero();

        MetricRegistry threadFactoryRegistry = new MetricRegistry();
        CountDownLatch threadLatch = new CountDownLatch(1);
        InstrumentedThreadFactory threadFactory = new InstrumentedThreadFactory(Executors.defaultThreadFactory(),
                threadFactoryRegistry, "threads");

        Thread thread = threadFactory.newThread(threadLatch::countDown);
        thread.start();
        assertThat(threadLatch.await(5, TimeUnit.SECONDS)).isTrue();
        thread.join(5_000L);

        assertThat(threadFactoryRegistry.getMeters().get("threads.created").getCount()).isEqualTo(1L);
        assertThat(threadFactoryRegistry.getMeters().get("threads.terminated").getCount()).isEqualTo(1L);
        assertThat(threadFactoryRegistry.getCounters().get("threads.running").getCount()).isZero();
    }

    private static final class StepClock extends Clock {
        private long tick;

        @Override
        public long getTick() {
            return tick;
        }

        @Override
        public long getTime() {
            return TimeUnit.NANOSECONDS.toMillis(tick);
        }

        void addNanos(long nanos) {
            tick += nanos;
        }
    }

    private static final class StaticMetricSet implements MetricSet {
        private final Map<String, Metric> metrics;

        private StaticMetricSet(Map<String, Metric> metrics) {
            this.metrics = metrics;
        }

        @Override
        public Map<String, Metric> getMetrics() {
            return metrics;
        }
    }

    private static final class RecordingMetricRegistryListener extends MetricRegistryListener.Base {
        private final List<String> added = new ArrayList<>();
        private final List<String> removed = new ArrayList<>();

        @Override
        public void onGaugeAdded(String name, Gauge<?> gauge) {
            added.add("gauge:" + name);
        }

        @Override
        public void onGaugeRemoved(String name) {
            removed.add("gauge:" + name);
        }

        @Override
        public void onCounterAdded(String name, Counter counter) {
            added.add("counter:" + name);
        }

        @Override
        public void onCounterRemoved(String name) {
            removed.add("counter:" + name);
        }

        @Override
        public void onHistogramAdded(String name, Histogram histogram) {
            added.add("histogram:" + name);
        }

        @Override
        public void onHistogramRemoved(String name) {
            removed.add("histogram:" + name);
        }

        @Override
        public void onMeterAdded(String name, Meter meter) {
            added.add("meter:" + name);
        }

        @Override
        public void onMeterRemoved(String name) {
            removed.add("meter:" + name);
        }

        @Override
        public void onTimerAdded(String name, Timer timer) {
            added.add("timer:" + name);
        }

        @Override
        public void onTimerRemoved(String name) {
            removed.add("timer:" + name);
        }
    }
}
