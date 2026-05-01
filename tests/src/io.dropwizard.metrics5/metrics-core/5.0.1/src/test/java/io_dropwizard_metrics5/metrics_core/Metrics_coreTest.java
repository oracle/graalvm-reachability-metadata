/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_dropwizard_metrics5.metrics_core;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

import io.dropwizard.metrics5.CachedGauge;
import io.dropwizard.metrics5.Clock;
import io.dropwizard.metrics5.ConsoleReporter;
import io.dropwizard.metrics5.Counter;
import io.dropwizard.metrics5.CsvReporter;
import io.dropwizard.metrics5.DerivativeGauge;
import io.dropwizard.metrics5.EWMA;
import io.dropwizard.metrics5.ExponentiallyDecayingReservoir;
import io.dropwizard.metrics5.FixedNameCsvFileProvider;
import io.dropwizard.metrics5.Gauge;
import io.dropwizard.metrics5.Histogram;
import io.dropwizard.metrics5.InstrumentedExecutorService;
import io.dropwizard.metrics5.InstrumentedScheduledExecutorService;
import io.dropwizard.metrics5.InstrumentedThreadFactory;
import io.dropwizard.metrics5.Meter;
import io.dropwizard.metrics5.Metric;
import io.dropwizard.metrics5.MetricAttribute;
import io.dropwizard.metrics5.MetricFilter;
import io.dropwizard.metrics5.MetricName;
import io.dropwizard.metrics5.MetricRegistry;
import io.dropwizard.metrics5.MetricRegistryListener;
import io.dropwizard.metrics5.MetricSet;
import io.dropwizard.metrics5.RatioGauge;
import io.dropwizard.metrics5.SharedMetricRegistries;
import io.dropwizard.metrics5.SlidingTimeWindowReservoir;
import io.dropwizard.metrics5.SlidingWindowReservoir;
import io.dropwizard.metrics5.Snapshot;
import io.dropwizard.metrics5.Timer;
import io.dropwizard.metrics5.UniformSnapshot;
import io.dropwizard.metrics5.WeightedSnapshot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

public class Metrics_coreTest {
    @Test
    void metricNamesComposeTagsResolveAndSortDeterministically() {
        MetricName serviceName = MetricName.build("service", "requests");
        MetricName taggedName = serviceName.tagged("env", "test", "region", "eu");
        MetricName resolvedName = MetricName.build("service").resolve("latency");
        MetricName joinedName = MetricName.build("root").append(MetricName.build("child", "leaf"));
        TreeMap<MetricName, Integer> sortedNames = new TreeMap<>();

        sortedNames.put(MetricName.build("zeta"), 3);
        sortedNames.put(resolvedName, 2);
        sortedNames.put(serviceName, 1);

        assertThat(serviceName.getKey()).isEqualTo("service.requests");
        assertThat(taggedName.getKey()).isEqualTo("service.requests");
        assertThat(taggedName.getTags()).containsEntry("env", "test").containsEntry("region", "eu");
        assertThat(resolvedName.getKey()).isEqualTo("service.latency");
        assertThat(joinedName.getKey()).isEqualTo("root.child.leaf");
        assertThat(MetricRegistry.name(Metrics_coreTest.class, "operations", "total").getKey())
                .isEqualTo("io_dropwizard_metrics5.metrics_core.Metrics_coreTest.operations.total");
        assertThat(sortedNames.keySet()).containsExactly(resolvedName, serviceName, MetricName.build("zeta"));
        assertThat(serviceName).isNotEqualTo(taggedName);
    }

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

        Gauge<?> queueDepth = registry.gauge(MetricName.build("queue", "depth"), queueDepthSupplier);
        Gauge<?> sameQueueDepth = registry.gauge(MetricName.build("queue", "depth"), queueDepthSupplier);
        Counter requests = registry.counter("requests");
        Meter throughput = registry.meter("throughput");

        Map<MetricName, Metric> nestedMetrics = new TreeMap<>();
        nestedMetrics.put(MetricName.build("size"), new Histogram(new SlidingWindowReservoir(3)));
        Map<MetricName, Metric> serviceMetrics = new TreeMap<>();
        serviceMetrics.put(MetricName.build("latency"), new Timer());
        serviceMetrics.put(MetricName.build("nested"), new StaticMetricSet(nestedMetrics));
        registry.register(MetricName.build("service"), new StaticMetricSet(serviceMetrics));

        requests.inc(2);
        throughput.mark(3);

        assertThat(MetricRegistry.name("root", "child", "leaf").getKey()).isEqualTo("root.child.leaf");
        assertThat(queueDepth.getValue()).isEqualTo(42);
        assertThat(sameQueueDepth).isSameAs(queueDepth);
        assertThat(createdGauges).hasValue(1);
        assertThat(registry.getNames()).contains(metricName("queue", "depth"), metricName("requests"),
                metricName("throughput"), metricName("service", "latency"), metricName("service", "nested", "size"));
        assertThat(registry.getCounters(MetricFilter.startsWith("req"))).containsOnlyKeys(metricName("requests"));
        assertThat(registry.getGauges(MetricFilter.endsWith("depth"))).containsOnlyKeys(metricName("queue", "depth"));
        assertThat(registry.getHistograms(MetricFilter.contains("nested")))
                .containsOnlyKeys(metricName("service", "nested", "size"));
        assertThat(listener.added)
                .contains("gauge:queue.depth", "counter:requests", "meter:throughput", "timer:service.latency",
                        "histogram:service.nested.size");

        registry.removeMatching(MetricFilter.startsWith("service"));
        assertThat(registry.remove(metricName("requests"))).isTrue();
        assertThat(registry.remove(metricName("missing"))).isFalse();
        assertThat(registry.getNames()).contains(metricName("queue", "depth"), metricName("throughput"))
                .doesNotContain(metricName("requests"), metricName("service", "latency"),
                        metricName("service", "nested", "size"));
        assertThat(listener.removed)
                .contains("timer:service.latency", "histogram:service.nested.size", "counter:requests");
    }

    @Test
    void countersMetersHistogramsTimersAndEwmasTrackMeasurements() {
        Counter counter = new Counter();
        counter.inc();
        counter.inc(4);
        counter.dec(2);

        StepClock meterClock = new StepClock();
        Meter meter = new Meter(meterClock);
        meter.mark();
        meter.mark(2);
        meterClock.addNanos(TimeUnit.SECONDS.toNanos(10));

        Histogram histogram = new Histogram(new SlidingWindowReservoir(3));
        histogram.update(10);
        histogram.update(20);
        histogram.update(30);
        histogram.update(40);

        StepClock timerClock = new StepClock();
        Timer timer = new Timer(new SlidingWindowReservoir(5), timerClock);
        String timedValue = timer.timeSupplier(() -> {
            timerClock.addNanos(3);
            return "done";
        });
        timer.time(() -> timerClock.addNanos(7));
        Timer.Context context = timer.time();
        timerClock.addNanos(11);
        long stoppedDuration = context.stop();
        timer.update(13, TimeUnit.NANOSECONDS);

        EWMA ewma = EWMA.oneMinuteEWMA();
        ewma.update(3);
        ewma.tick();

        Snapshot histogramSnapshot = histogram.getSnapshot();
        Snapshot timerSnapshot = timer.getSnapshot();

        assertThat(counter.getCount()).isEqualTo(3L);
        assertThat(meter.getCount()).isEqualTo(3L);
        assertThat(meter.getMeanRate()).isGreaterThanOrEqualTo(0.0d);
        assertThat(histogram.getCount()).isEqualTo(4L);
        assertThat(histogram.getSum()).isEqualTo(100L);
        assertThat(histogramSnapshot.getValues()).containsExactly(20L, 30L, 40L);
        assertThat(histogramSnapshot.getMin()).isEqualTo(20L);
        assertThat(histogramSnapshot.getMax()).isEqualTo(40L);
        assertThat(timedValue).isEqualTo("done");
        assertThat(stoppedDuration).isEqualTo(11L);
        assertThat(timer.getCount()).isEqualTo(4L);
        assertThat(timer.getSum()).isEqualTo(34L);
        assertThat(timer.getMeanRate()).isGreaterThanOrEqualTo(0.0d);
        assertThat(timerSnapshot.getValues()).containsExactly(3L, 7L, 11L, 13L);
        assertThat(timerSnapshot.getMin()).isEqualTo(3L);
        assertThat(timerSnapshot.getMax()).isEqualTo(13L);
        assertThat(ewma.getRate(TimeUnit.SECONDS)).isGreaterThan(0.0d);
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
        StepClock decayingClock = new StepClock();
        ExponentiallyDecayingReservoir decayingReservoir = new ExponentiallyDecayingReservoir(3, 0.015,
                decayingClock);

        timeWindowReservoir.update(5);
        clock.addNanos(9);
        timeWindowReservoir.update(9);
        clock.addNanos(2);
        Snapshot prunedSnapshot = timeWindowReservoir.getSnapshot();

        decayingReservoir.update(100, decayingClock.getTime());
        decayingClock.addNanos(TimeUnit.SECONDS.toNanos(1));
        decayingReservoir.update(200, decayingClock.getTime());
        decayingReservoir.update(300, decayingClock.getTime());
        decayingReservoir.update(400, decayingClock.getTime());
        Snapshot decayingSnapshot = decayingReservoir.getSnapshot();
        UniformSnapshot uniformSnapshot = new UniformSnapshot(new long[] {4, 1, 3, 2 });
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        uniformSnapshot.dump(output);

        assertThat(prunedSnapshot.getValues()).containsExactly(9L);
        clock.addNanos(1);
        timeWindowReservoir.update(12);
        assertThat(timeWindowReservoir.getSnapshot().getValues()).containsExactly(9L, 12L);
        assertThat(decayingReservoir.size()).isEqualTo(3);
        assertThat(decayingSnapshot.size()).isEqualTo(3);
        assertThat(uniformSnapshot.size()).isEqualTo(4);
        assertThat(uniformSnapshot.getValues()).containsExactly(1L, 2L, 3L, 4L);
        assertThat(uniformSnapshot.getMedian()).isEqualTo(2.5d);
        assertThat(uniformSnapshot.get75thPercentile()).isEqualTo(3.75d);
        assertThat(output.toString(StandardCharsets.UTF_8)).isEqualTo("1\n2\n3\n4\n");
    }

    @Test
    void weightedSnapshotsUseSampleWeightsForQuantilesAndStatistics() {
        WeightedSnapshot snapshot = new WeightedSnapshot(List.of(new WeightedSnapshot.WeightedSample(30, 6.0d),
                new WeightedSnapshot.WeightedSample(10, 1.0d), new WeightedSnapshot.WeightedSample(20, 3.0d)));

        assertThat(snapshot.size()).isEqualTo(3);
        assertThat(snapshot.getValues()).containsExactly(10L, 20L, 30L);
        assertThat(snapshot.getMin()).isEqualTo(10L);
        assertThat(snapshot.getMax()).isEqualTo(30L);
        assertThat(snapshot.getMean()).isEqualTo(25.0d);
        assertThat(snapshot.getStdDev()).isCloseTo(Math.sqrt(45.0d), offset(0.000001d));
        assertThat(snapshot.getValue(0.05d)).isEqualTo(10.0d);
        assertThat(snapshot.getValue(0.39d)).isEqualTo(20.0d);
        assertThat(snapshot.getValue(0.40d)).isEqualTo(30.0d);
        assertThatThrownBy(() -> snapshot.getValue(1.01d)).isInstanceOf(IllegalArgumentException.class);
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
        registry.registerGauge("reporting.gauge", (Gauge<Integer>) () -> 42);

        Counter counter = registry.counter("reporting.counter");
        counter.inc(7);

        Histogram histogram = registry.histogram("reporting.histogram");
        histogram.update(10);
        histogram.update(20);

        Meter meter = registry.meter("reporting.meter");
        meter.mark(3);

        Timer timer = registry.timer("reporting.timer");
        timer.update(5, TimeUnit.MILLISECONDS);

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
        reporter.stop();
    }

    @Test
    void csvReporterWritesFilteredMetricsAndAppendsSamplesAcrossReports() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        Counter counter = registry.counter("csv.counter");
        counter.inc(5);

        Timer timer = registry.timer("csv.timer");
        timer.update(8, TimeUnit.MILLISECONDS);
        registry.registerGauge("skip.gauge", (Gauge<Integer>) () -> 99);

        StepClock clock = new StepClock();
        clock.addNanos(TimeUnit.SECONDS.toNanos(123));
        Path reportDirectory = Files.createTempDirectory("metrics5-core-csv-");

        CsvReporter reporter = CsvReporter.forRegistry(registry)
                .withClock(clock)
                .filter((name, metric) -> name.getKey().startsWith("csv."))
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build(reportDirectory.toFile());
        reporter.report();

        counter.inc(2);
        timer.update(10, TimeUnit.MILLISECONDS);
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
        String timerHeader = "t,count,sum,max,mean,min,stddev,p50,p75,p95,p98,p99,p999,mean_rate,m1_rate,"
                + "m5_rate,m15_rate,rate_unit,duration_unit";
        assertThat(timerLines).hasSize(3).first().isEqualTo(timerHeader);
        assertThat(timerLines.get(1))
                .startsWith("123,1,8.000000,8.000000,8.000000,8.000000,0.000000,8.000000,8.000000")
                .endsWith(",calls/second,milliseconds");
        assertThat(timerLines.get(2))
                .startsWith("124,2,18.000000,10.000000,9.000000,8.000000,1.000000,10.000000,10.000000")
                .endsWith(",calls/second,milliseconds");
        assertThat(Files.exists(reportDirectory.resolve("skip.gauge.csv"))).isFalse();
        reporter.stop();
    }

    @Test
    void csvReporterUsesFixedNameFileProviderForSlashDelimitedMetricNames() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        Counter counter = registry.counter(MetricName.build("/api/requests"));
        counter.inc(12);
        registry.register(MetricName.build("/api/inflight"), (Gauge<Integer>) () -> 3);

        StepClock clock = new StepClock();
        clock.addNanos(TimeUnit.SECONDS.toNanos(5));
        Path reportDirectory = Files.createTempDirectory("metrics5-core-fixed-csv-");

        CsvReporter reporter = CsvReporter.forRegistry(registry)
                .withClock(clock)
                .withCsvFileProvider(new FixedNameCsvFileProvider())
                .build(reportDirectory.toFile());
        reporter.report();

        List<String> reportFiles;
        try (Stream<Path> paths = Files.list(reportDirectory)) {
            reportFiles = paths.map(path -> path.getFileName().toString()).sorted().toList();
        }

        assertThat(reportFiles).containsExactly("api.inflight.csv", "api.requests.csv");
        assertThat(Files.readAllLines(reportDirectory.resolve("api.inflight.csv"), StandardCharsets.UTF_8))
                .containsExactly("t,value", "5,3");
        assertThat(Files.readAllLines(reportDirectory.resolve("api.requests.csv"), StandardCharsets.UTF_8))
                .containsExactly("t,count", "5,12");
        assertThat(Files.isDirectory(reportDirectory.resolve("api"))).isFalse();
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
        assertThat(executorRegistry.getMeters().get(metricName("workers", "submitted")).getCount())
                .isGreaterThanOrEqualTo(2L);
        assertThat(executorRegistry.getMeters().get(metricName("workers", "completed")).getCount())
                .isGreaterThanOrEqualTo(2L);
        assertThat(executorRegistry.getTimers().get(metricName("workers", "duration")).getCount())
                .isGreaterThanOrEqualTo(2L);
        assertThat(executorRegistry.getTimers().get(metricName("workers", "idle")).getCount())
                .isGreaterThanOrEqualTo(1L);
        assertThat(executorRegistry.getCounters().get(metricName("workers", "running")).getCount()).isZero();

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
        assertThat(scheduledRegistry.getMeters().get(metricName("scheduler", "scheduled", "once")).getCount())
                .isEqualTo(1L);
        assertThat(scheduledRegistry.getMeters().get(metricName("scheduler", "scheduled", "repetitively")).getCount())
                .isEqualTo(1L);
        assertThat(scheduledRegistry.getMeters().get(metricName("scheduler", "submitted")).getCount())
                .isGreaterThanOrEqualTo(1L);
        assertThat(scheduledRegistry.getMeters().get(metricName("scheduler", "completed")).getCount())
                .isGreaterThanOrEqualTo(3L);
        assertThat(scheduledRegistry.getTimers().get(metricName("scheduler", "duration")).getCount())
                .isGreaterThanOrEqualTo(3L);
        assertThat(scheduledRegistry.getHistograms().get(metricName("scheduler", "scheduled", "percent-of-period"))
                .getCount()).isGreaterThanOrEqualTo(1L);
        assertThat(scheduledRegistry.getCounters().get(metricName("scheduler", "scheduled", "overrun")).getCount())
                .isGreaterThanOrEqualTo(0L);
        assertThat(scheduledRegistry.getCounters().get(metricName("scheduler", "running")).getCount()).isZero();

        MetricRegistry threadFactoryRegistry = new MetricRegistry();
        CountDownLatch threadLatch = new CountDownLatch(1);
        InstrumentedThreadFactory threadFactory = new InstrumentedThreadFactory(Executors.defaultThreadFactory(),
                threadFactoryRegistry, "threads");

        Thread thread = threadFactory.newThread(threadLatch::countDown);
        thread.start();
        assertThat(threadLatch.await(5, TimeUnit.SECONDS)).isTrue();
        thread.join(5_000L);

        assertThat(threadFactoryRegistry.getMeters().get(metricName("threads", "created")).getCount()).isEqualTo(1L);
        assertThat(threadFactoryRegistry.getMeters().get(metricName("threads", "terminated")).getCount()).isEqualTo(1L);
        assertThat(threadFactoryRegistry.getCounters().get(metricName("threads", "running")).getCount()).isZero();
    }

    private static MetricName metricName(String... names) {
        return MetricName.build(names);
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

    private static final class StaticMetricSet implements MetricSet, Metric {
        private final Map<MetricName, Metric> metrics;

        private StaticMetricSet(Map<MetricName, Metric> metrics) {
            this.metrics = metrics;
        }

        @Override
        public Map<MetricName, Metric> getMetrics() {
            return metrics;
        }
    }

    private static final class RecordingMetricRegistryListener extends MetricRegistryListener.Base {
        private final List<String> added = new ArrayList<>();
        private final List<String> removed = new ArrayList<>();

        @Override
        public void onGaugeAdded(MetricName name, Gauge<?> gauge) {
            added.add("gauge:" + name.getKey());
        }

        @Override
        public void onGaugeRemoved(MetricName name) {
            removed.add("gauge:" + name.getKey());
        }

        @Override
        public void onCounterAdded(MetricName name, Counter counter) {
            added.add("counter:" + name.getKey());
        }

        @Override
        public void onCounterRemoved(MetricName name) {
            removed.add("counter:" + name.getKey());
        }

        @Override
        public void onHistogramAdded(MetricName name, Histogram histogram) {
            added.add("histogram:" + name.getKey());
        }

        @Override
        public void onHistogramRemoved(MetricName name) {
            removed.add("histogram:" + name.getKey());
        }

        @Override
        public void onMeterAdded(MetricName name, Meter meter) {
            added.add("meter:" + name.getKey());
        }

        @Override
        public void onMeterRemoved(MetricName name) {
            removed.add("meter:" + name.getKey());
        }

        @Override
        public void onTimerAdded(MetricName name, Timer timer) {
            added.add("timer:" + name.getKey());
        }

        @Override
        public void onTimerRemoved(MetricName name) {
            removed.add("timer:" + name.getKey());
        }
    }
}
