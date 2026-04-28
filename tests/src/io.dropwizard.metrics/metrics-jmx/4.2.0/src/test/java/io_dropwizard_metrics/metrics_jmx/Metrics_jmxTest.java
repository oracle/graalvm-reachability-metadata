/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_dropwizard_metrics.metrics_jmx;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SlidingWindowReservoir;
import com.codahale.metrics.Timer;
import com.codahale.metrics.jmx.DefaultObjectNameFactory;
import com.codahale.metrics.jmx.JmxReporter;
import com.codahale.metrics.jmx.ObjectNameFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Metrics_jmxTest {
    @Test
    void reporterPublishesExistingAndDynamicMetricsAsJmxMBeans() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        MBeanServer server = MBeanServerFactory.newMBeanServer();
        String domain = uniqueDomain("published");

        registry.register("queue.depth", (Gauge<Integer>) () -> 17);
        Counter requests = registry.counter("requests");
        requests.inc(5);
        Meter throughput = registry.meter("throughput");
        throughput.mark(3);
        Histogram payloadSize = new Histogram(new SlidingWindowReservoir(5));
        payloadSize.update(100);
        payloadSize.update(200);
        payloadSize.update(300);
        registry.register("payload.size", payloadSize);
        Timer requestTimer = registry.timer("request.timer");
        requestTimer.update(1_500, TimeUnit.MICROSECONDS);

        ObjectName gaugeName = metricName(domain, "gauges", "queue.depth");
        ObjectName counterName = metricName(domain, "counters", "requests");
        ObjectName meterName = metricName(domain, "meters", "throughput");
        ObjectName histogramName = metricName(domain, "histograms", "payload.size");
        ObjectName timerName = metricName(domain, "timers", "request.timer");

        try (JmxReporter reporter = JmxReporter.forRegistry(registry)
                .registerWith(server)
                .inDomain(domain)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build()) {
            reporter.start();

            assertThat(server.isRegistered(gaugeName)).isTrue();
            assertThat(server.isRegistered(counterName)).isTrue();
            assertThat(server.isRegistered(meterName)).isTrue();
            assertThat(server.isRegistered(histogramName)).isTrue();
            assertThat(server.isRegistered(timerName)).isTrue();
            assertThat(server.getAttribute(gaugeName, "Value")).isEqualTo(17);
            assertThat(server.getAttribute(gaugeName, "Number")).isEqualTo(17);
            assertThat(server.getAttribute(counterName, "Count")).isEqualTo(5L);
            assertThat(server.getAttribute(meterName, "Count")).isEqualTo(3L);
            assertThat(server.getAttribute(meterName, "RateUnit")).isEqualTo("events/second");
            assertThat(server.getAttribute(histogramName, "Count")).isEqualTo(3L);
            assertThat(server.getAttribute(histogramName, "Min")).isEqualTo(100L);
            assertThat(server.getAttribute(histogramName, "Max")).isEqualTo(300L);
            assertThat(server.getAttribute(histogramName, "SnapshotSize")).isEqualTo(3L);
            assertThat(server.getAttribute(timerName, "Count")).isEqualTo(1L);
            assertThat(server.getAttribute(timerName, "DurationUnit")).isEqualTo("milliseconds");
            assertThat((Double) server.getAttribute(timerName, "Min")).isEqualTo(1.5d);

            Counter dynamicCounter = registry.counter("dynamic.created");
            dynamicCounter.inc(9);
            ObjectName dynamicCounterName = metricName(domain, "counters", "dynamic.created");
            assertThat(server.isRegistered(dynamicCounterName)).isTrue();
            assertThat(server.getAttribute(dynamicCounterName, "Count")).isEqualTo(9L);

            assertThat(registry.remove("requests")).isTrue();
            assertThat(server.isRegistered(counterName)).isFalse();
        }

        assertThat(server.queryNames(new ObjectName(domain + ":*"), null)).isEmpty();
    }

    @Test
    void reporterUsesPlatformMBeanServerWhenNoServerIsConfigured() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        registry.counter("platform.requests");
        MBeanServer platformServer = ManagementFactory.getPlatformMBeanServer();
        String domain = uniqueDomain("platform");
        ObjectName counterName = metricName(domain, "counters", "platform.requests");

        try (JmxReporter reporter = JmxReporter.forRegistry(registry)
                .inDomain(domain)
                .build()) {
            reporter.start();

            assertThat(platformServer.isRegistered(counterName)).isTrue();
        }

        assertThat(platformServer.isRegistered(counterName)).isFalse();
    }

    @Test
    void reporterStopUnregistersMBeansAndRestartPublishesCurrentMetrics() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        MBeanServer server = MBeanServerFactory.newMBeanServer();
        String domain = uniqueDomain("lifecycle");
        ObjectName initialCounterName = metricName(domain, "counters", "initial.requests");
        ObjectName restartedCounterName = metricName(domain, "counters", "restarted.requests");

        try (JmxReporter reporter = JmxReporter.forRegistry(registry)
                .registerWith(server)
                .inDomain(domain)
                .build()) {
            registry.counter("initial.requests");

            reporter.start();
            assertThat(server.isRegistered(initialCounterName)).isTrue();

            reporter.stop();
            assertThat(server.isRegistered(initialCounterName)).isFalse();

            registry.remove("initial.requests");
            registry.counter("restarted.requests");

            reporter.start();
            assertThat(server.isRegistered(initialCounterName)).isFalse();
            assertThat(server.isRegistered(restartedCounterName)).isTrue();
        }
    }

    @Test
    void reporterHonorsMetricFiltersAndCustomObjectNameFactories() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        registry.counter("visible.requests").inc(4);
        registry.counter("hidden.requests").inc(7);
        registry.register("visible.load", (Gauge<Double>) () -> 0.75d);
        MBeanServer server = MBeanServerFactory.newMBeanServer();
        String domain = uniqueDomain("filtered");
        RecordingObjectNameFactory objectNameFactory = new RecordingObjectNameFactory("primary");

        try (JmxReporter reporter = JmxReporter.forRegistry(registry)
                .registerWith(server)
                .inDomain(domain)
                .filter(MetricFilter.startsWith("visible"))
                .createsObjectNamesWith(objectNameFactory)
                .build()) {
            reporter.start();

            ObjectName visibleCounter = customMetricName(domain, "primary", "counters", "visible.requests");
            ObjectName visibleGauge = customMetricName(domain, "primary", "gauges", "visible.load");
            assertThat(server.isRegistered(visibleCounter)).isTrue();
            assertThat(server.isRegistered(visibleGauge)).isTrue();
            assertThat(server.getAttribute(visibleCounter, "Count")).isEqualTo(4L);
            assertThat(server.getAttribute(visibleGauge, "Value")).isEqualTo(0.75d);
            assertThat(server.queryNames(new ObjectName(domain + ":group=primary,metric=hidden.requests,*"), null))
                    .isEmpty();
            assertThat(objectNameFactory.requestedNames)
                    .contains("counters:visible.requests", "gauges:visible.load")
                    .doesNotContain("counters:hidden.requests");
        }
    }

    @Test
    void defaultObjectNameFactoryCreatesNonPatternNamesForWildcardMetricNames() throws Exception {
        String domain = uniqueDomain("quoted");
        DefaultObjectNameFactory factory = new DefaultObjectNameFactory();

        ObjectName quotedName = factory.createName("gauges", domain, "cache:*?depth");

        assertThat(quotedName.getDomain()).isEqualTo(domain);
        assertThat(quotedName.isPattern()).isFalse();
        assertThat(quotedName.getKeyProperty("name")).isEqualTo(ObjectName.quote("cache:*?depth"));
        assertThat(quotedName.toString()).contains(ObjectName.quote("cache:*?depth"));
    }

    @Test
    void perMetricRateAndDurationUnitsOverrideReporterDefaults() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        Meter defaultRateMeter = registry.meter("default.rate");
        defaultRateMeter.mark(2);
        Meter customRateMeter = registry.meter("custom.rate");
        customRateMeter.mark(3);
        Timer defaultDurationTimer = registry.timer("default.duration");
        defaultDurationTimer.update(2, TimeUnit.SECONDS);
        Timer customDurationTimer = registry.timer("custom.duration");
        customDurationTimer.update(2_500, TimeUnit.MILLISECONDS);
        MBeanServer server = MBeanServerFactory.newMBeanServer();
        String domain = uniqueDomain("units");

        try (JmxReporter reporter = JmxReporter.forRegistry(registry)
                .registerWith(server)
                .inDomain(domain)
                .convertRatesTo(TimeUnit.MINUTES)
                .convertDurationsTo(TimeUnit.SECONDS)
                .specificRateUnits(Map.of("custom.rate", TimeUnit.MILLISECONDS))
                .specificDurationUnits(Map.of("custom.duration", TimeUnit.MILLISECONDS))
                .build()) {
            reporter.start();

            ObjectName defaultRateName = metricName(domain, "meters", "default.rate");
            ObjectName customRateName = metricName(domain, "meters", "custom.rate");
            ObjectName defaultDurationName = metricName(domain, "timers", "default.duration");
            ObjectName customDurationName = metricName(domain, "timers", "custom.duration");
            assertThat(server.getAttribute(defaultRateName, "RateUnit")).isEqualTo("events/minute");
            assertThat(server.getAttribute(customRateName, "RateUnit")).isEqualTo("events/millisecond");
            assertThat(server.getAttribute(defaultDurationName, "DurationUnit")).isEqualTo("seconds");
            assertThat(server.getAttribute(customDurationName, "DurationUnit")).isEqualTo("milliseconds");
            assertThat((Double) server.getAttribute(defaultDurationName, "Min")).isEqualTo(2.0d);
            assertThat((Double) server.getAttribute(customDurationName, "Min")).isEqualTo(2_500.0d);
        }
    }

    private static String uniqueDomain(String suffix) {
        return "io.dropwizard.metrics.jmx.test." + suffix + "." + System.nanoTime();
    }

    private static ObjectName metricName(String domain, String type, String name) {
        return new DefaultObjectNameFactory().createName(type, domain, name);
    }

    private static ObjectName customMetricName(String domain, String group, String type, String metric) throws Exception {
        return new ObjectName(domain + ":group=" + ObjectName.quote(group) + ",type=" + ObjectName.quote(type)
                + ",metric=" + ObjectName.quote(metric));
    }

    private static final class RecordingObjectNameFactory implements ObjectNameFactory {
        private final String group;
        private final Set<String> requestedNames = new LinkedHashSet<>();

        private RecordingObjectNameFactory(String group) {
            this.group = group;
        }

        @Override
        public ObjectName createName(String type, String domain, String name) {
            requestedNames.add(type + ":" + name);
            try {
                return new ObjectName(domain + ":group=" + ObjectName.quote(group) + ",type=" + ObjectName.quote(type)
                        + ",metric=" + ObjectName.quote(name));
            } catch (Exception exception) {
                throw new IllegalArgumentException(exception);
            }
        }
    }
}
