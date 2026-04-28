/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_dropwizard_metrics.metrics_jvm;

import java.io.ByteArrayOutputStream;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.jvm.BufferPoolMetricSet;
import com.codahale.metrics.jvm.CachedThreadStatesGaugeSet;
import com.codahale.metrics.jvm.ClassLoadingGaugeSet;
import com.codahale.metrics.jvm.CpuTimeClock;
import com.codahale.metrics.jvm.FileDescriptorRatioGauge;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.JmxAttributeGauge;
import com.codahale.metrics.jvm.JvmAttributeGaugeSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.ThreadDeadlockDetector;
import com.codahale.metrics.jvm.ThreadDump;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
import com.sun.management.UnixOperatingSystemMXBean;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Metrics_jvmTest {
    @Test
    void classLoadingAndJvmAttributeGaugesExposeMxBeanValues() {
        Map<String, Metric> classLoadingMetrics = new ClassLoadingGaugeSet(new FixedClassLoadingMXBean())
                .getMetrics();
        Map<String, Metric> jvmMetrics = new JvmAttributeGaugeSet().getMetrics();

        assertThat(classLoadingMetrics).containsOnlyKeys("loaded", "unloaded");
        assertThat(gaugeValue(classLoadingMetrics, "loaded", Long.class)).isEqualTo(123L);
        assertThat(gaugeValue(classLoadingMetrics, "unloaded", Long.class)).isEqualTo(4L);
        assertThat(jvmMetrics).containsOnlyKeys("name", "vendor", "uptime");
        assertThat(gaugeValue(jvmMetrics, "name", String.class))
                .isEqualTo(ManagementFactory.getRuntimeMXBean().getName());
        assertThat(gaugeValue(jvmMetrics, "vendor", String.class))
                .contains(ManagementFactory.getRuntimeMXBean().getVmVendor())
                .contains(ManagementFactory.getRuntimeMXBean().getVmName())
                .contains(ManagementFactory.getRuntimeMXBean().getVmVersion())
                .contains(ManagementFactory.getRuntimeMXBean().getSpecVersion());
        assertThat(gaugeValue(jvmMetrics, "uptime", Long.class)).isGreaterThanOrEqualTo(0L);
        assertThatThrownBy(() -> jvmMetrics.put("extra", (Gauge<Integer>) () -> 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void memoryUsageGaugeSetReportsAggregateHeapNonHeapAndPoolValues() {
        MemoryUsage heap = new MemoryUsage(10, 20, 30, 100);
        MemoryUsage nonHeap = new MemoryUsage(5, 7, 10, 20);
        MemoryUsage poolUsage = new MemoryUsage(1, 2, 4, -1);
        MemoryUsage collectionUsage = new MemoryUsage(0, 3, 6, 9);
        Map<String, Metric> metrics = new MemoryUsageGaugeSet(new FixedMemoryMXBean(heap, nonHeap),
                List.of(new FixedMemoryPoolMXBean("Code Cache Pool", poolUsage, collectionUsage))).getMetrics();

        assertThat(metrics)
                .containsKeys("total.init", "total.used", "total.max", "total.committed", "heap.init",
                        "heap.used", "heap.max", "heap.committed", "heap.usage", "non-heap.init",
                        "non-heap.used", "non-heap.max", "non-heap.committed", "non-heap.usage",
                        "pools.Code-Cache-Pool.init", "pools.Code-Cache-Pool.used",
                        "pools.Code-Cache-Pool.max", "pools.Code-Cache-Pool.committed",
                        "pools.Code-Cache-Pool.usage", "pools.Code-Cache-Pool.used-after-gc");
        assertThat(gaugeValue(metrics, "total.init", Long.class)).isEqualTo(15L);
        assertThat(gaugeValue(metrics, "total.used", Long.class)).isEqualTo(27L);
        assertThat(gaugeValue(metrics, "total.max", Long.class)).isEqualTo(120L);
        assertThat(gaugeValue(metrics, "total.committed", Long.class)).isEqualTo(40L);
        assertThat(gaugeValue(metrics, "heap.usage", Double.class)).isEqualTo(0.2d);
        assertThat(gaugeValue(metrics, "non-heap.usage", Double.class)).isEqualTo(0.35d);
        assertThat(gaugeValue(metrics, "pools.Code-Cache-Pool.max", Long.class)).isEqualTo(-1L);
        assertThat(gaugeValue(metrics, "pools.Code-Cache-Pool.usage", Double.class)).isEqualTo(0.5d);
        assertThat(gaugeValue(metrics, "pools.Code-Cache-Pool.used-after-gc", Long.class)).isEqualTo(3L);
        assertThatThrownBy(() -> metrics.remove("heap.used")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void garbageCollectorBufferPoolAndJmxAttributeGaugesReadJmxValues() throws Exception {
        Map<String, Metric> gcMetrics = new GarbageCollectorMetricSet(List.of(
                new FixedGarbageCollectorMXBean("Copy GC", 7, 11),
                new FixedGarbageCollectorMXBean("Old Collector", 13, 17))).getMetrics();

        assertThat(gcMetrics).containsOnlyKeys("Copy-GC.count", "Copy-GC.time", "Old-Collector.count",
                "Old-Collector.time");
        assertThat(gaugeValue(gcMetrics, "Copy-GC.count", Long.class)).isEqualTo(7L);
        assertThat(gaugeValue(gcMetrics, "Copy-GC.time", Long.class)).isEqualTo(11L);
        assertThat(gaugeValue(gcMetrics, "Old-Collector.count", Long.class)).isEqualTo(13L);
        assertThat(gaugeValue(gcMetrics, "Old-Collector.time", Long.class)).isEqualTo(17L);
        assertThatThrownBy(() -> gcMetrics.clear()).isInstanceOf(UnsupportedOperationException.class);

        Map<String, Metric> bufferMetrics = new BufferPoolMetricSet(ManagementFactory.getPlatformMBeanServer())
                .getMetrics();
        assertThat(bufferMetrics.keySet()).allMatch(name -> name.startsWith("direct.") || name.startsWith("mapped."));
        bufferMetrics.values().forEach(metric -> assertThat(((Gauge<?>) metric).getValue()).isInstanceOf(Number.class));
        assertThatThrownBy(() -> bufferMetrics.put("invalid", (Gauge<Integer>) () -> 1))
                .isInstanceOf(UnsupportedOperationException.class);

        ObjectName runtimeName = new ObjectName("java.lang:type=Runtime");
        assertThat(new JmxAttributeGauge(runtimeName, "Name").getValue())
                .isEqualTo(ManagementFactory.getRuntimeMXBean().getName());
        assertThat(new JmxAttributeGauge(runtimeName, "NoSuchAttribute").getValue()).isNull();
    }

    @Test
    void threadStateDeadlockAndThreadDumpUtilitiesInspectRunningJvm() {
        ThreadStatesGaugeSet threadStates = new ThreadStatesGaugeSet();
        Map<String, Metric> metrics = threadStates.getMetrics();

        for (Thread.State state : Thread.State.values()) {
            assertThat(metrics).containsKey(state.toString().toLowerCase() + ".count");
            assertThat(gaugeValue(metrics, state.toString().toLowerCase() + ".count", Integer.class))
                    .isGreaterThanOrEqualTo(0);
        }
        Integer threadCount = gaugeValue(metrics, "count", Integer.class);
        Integer daemonThreadCount = gaugeValue(metrics, "daemon.count", Integer.class);
        assertThat(threadCount).isGreaterThanOrEqualTo(1);
        assertThat(daemonThreadCount).isBetween(0, threadCount);
        assertThat(gaugeValue(metrics, "peak.count", Integer.class)).isGreaterThanOrEqualTo(threadCount);
        assertThat(gaugeValue(metrics, "total_started.count", Long.class))
                .isGreaterThanOrEqualTo(threadCount.longValue());
        assertThat(gaugeValue(metrics, "deadlock.count", Integer.class)).isGreaterThanOrEqualTo(0);
        assertThat(gaugeValue(metrics, "deadlocks", Set.class)).isNotNull();

        Map<String, Metric> cachedMetrics = new CachedThreadStatesGaugeSet(1, TimeUnit.MINUTES).getMetrics();
        Integer firstCachedThreadCount = gaugeValue(cachedMetrics, "count", Integer.class);
        Integer secondCachedThreadCount = gaugeValue(cachedMetrics, "count", Integer.class);
        assertThat(firstCachedThreadCount).isGreaterThanOrEqualTo(1);
        assertThat(secondCachedThreadCount).isGreaterThanOrEqualTo(1);
        assertThat(new ThreadDeadlockDetector().getDeadlockedThreads()).isNotNull();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new ThreadDump(ManagementFactory.getThreadMXBean()).dump(false, false, output);
        String dump = output.toString(StandardCharsets.UTF_8);
        if (isNativeImageRuntime()) {
            assertThat(dump).isNotNull();
        } else {
            assertThat(dump).contains("id=", "state=", Thread.currentThread().getName());
        }
    }

    @Test
    void memoryUsageGaugeSetOmitsPostGcPoolGaugeWhenCollectionUsageUnavailable() {
        MemoryUsage heap = new MemoryUsage(1, 2, 4, 8);
        MemoryUsage nonHeap = new MemoryUsage(1, 3, 6, 12);
        MemoryUsage poolUsage = new MemoryUsage(2, 4, 8, 16);
        Map<String, Metric> metrics = new MemoryUsageGaugeSet(new FixedMemoryMXBean(heap, nonHeap),
                List.of(new FixedMemoryPoolMXBean("Metaspace No Collection", poolUsage, null))).getMetrics();

        assertThat(metrics)
                .containsKeys("pools.Metaspace-No-Collection.init", "pools.Metaspace-No-Collection.used",
                        "pools.Metaspace-No-Collection.max", "pools.Metaspace-No-Collection.committed",
                        "pools.Metaspace-No-Collection.usage")
                .doesNotContainKey("pools.Metaspace-No-Collection.used-after-gc");
        assertThat(gaugeValue(metrics, "pools.Metaspace-No-Collection.init", Long.class)).isEqualTo(2L);
        assertThat(gaugeValue(metrics, "pools.Metaspace-No-Collection.usage", Double.class)).isEqualTo(0.25d);
    }

    @Test
    void cpuTimeClockAndFileDescriptorRatioGaugeReturnRuntimeValues() {
        CpuTimeClock clock = new CpuTimeClock();
        long before = clock.getTick();
        long after = clock.getTick();
        assertThat(after >= before || after == -1L).isTrue();

        Double fileDescriptorUsage = new FileDescriptorRatioGauge().getValue();
        assertThat(fileDescriptorUsage.isNaN() || (fileDescriptorUsage >= 0.0d && fileDescriptorUsage <= 1.0d))
                .isTrue();
    }

    @Test
    void fileDescriptorRatioGaugeUsesProvidedUnixOperatingSystemMxBean() {
        FileDescriptorRatioGauge gauge = new FileDescriptorRatioGauge(new FixedUnixOperatingSystemMXBean(8L, 32L));

        assertThat(gauge.getValue()).isEqualTo(0.25d);
    }

    @SuppressWarnings("unchecked")
    private static <T> T gaugeValue(Map<String, Metric> metrics, String name, Class<T> valueType) {
        assertThat(metrics).containsKey(name);
        Object value = ((Gauge<?>) metrics.get(name)).getValue();
        assertThat(value).isInstanceOf(valueType);
        return (T) value;
    }

    private static ObjectName objectName(String name) {
        try {
            return new ObjectName(name);
        } catch (MalformedObjectNameException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    private static final class FixedClassLoadingMXBean implements ClassLoadingMXBean {
        @Override
        public long getTotalLoadedClassCount() {
            return 123L;
        }

        @Override
        public int getLoadedClassCount() {
            return 12;
        }

        @Override
        public long getUnloadedClassCount() {
            return 4L;
        }

        @Override
        public boolean isVerbose() {
            return false;
        }

        @Override
        public void setVerbose(boolean value) {
            throw new UnsupportedOperationException("Fixed MXBean stubs are immutable");
        }

        @Override
        public ObjectName getObjectName() {
            return objectName("java.lang:type=ClassLoading");
        }
    }

    private static final class FixedMemoryMXBean implements MemoryMXBean {
        private final MemoryUsage heap;
        private final MemoryUsage nonHeap;

        FixedMemoryMXBean(MemoryUsage heap, MemoryUsage nonHeap) {
            this.heap = heap;
            this.nonHeap = nonHeap;
        }

        @Override
        public int getObjectPendingFinalizationCount() {
            return 0;
        }

        @Override
        public MemoryUsage getHeapMemoryUsage() {
            return heap;
        }

        @Override
        public MemoryUsage getNonHeapMemoryUsage() {
            return nonHeap;
        }

        @Override
        public boolean isVerbose() {
            return false;
        }

        @Override
        public void setVerbose(boolean value) {
            throw new UnsupportedOperationException("Fixed MXBean stubs are immutable");
        }

        @Override
        public void gc() {
            throw new UnsupportedOperationException("Fixed MXBean stubs do not manage memory");
        }

        @Override
        public ObjectName getObjectName() {
            return objectName("java.lang:type=Memory");
        }
    }

    private static final class FixedMemoryPoolMXBean implements MemoryPoolMXBean {
        private final String name;
        private final MemoryUsage usage;
        private final MemoryUsage collectionUsage;

        FixedMemoryPoolMXBean(String name, MemoryUsage usage, MemoryUsage collectionUsage) {
            this.name = name;
            this.usage = usage;
            this.collectionUsage = collectionUsage;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public MemoryType getType() {
            return MemoryType.NON_HEAP;
        }

        @Override
        public MemoryUsage getUsage() {
            return usage;
        }

        @Override
        public MemoryUsage getPeakUsage() {
            return usage;
        }

        @Override
        public void resetPeakUsage() {
            throw new UnsupportedOperationException("Fixed MXBean stubs are immutable");
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public String[] getMemoryManagerNames() {
            return new String[] {"test-manager" };
        }

        @Override
        public long getUsageThreshold() {
            return 0;
        }

        @Override
        public void setUsageThreshold(long threshold) {
            throw new UnsupportedOperationException("Fixed MXBean stubs are immutable");
        }

        @Override
        public boolean isUsageThresholdExceeded() {
            return false;
        }

        @Override
        public long getUsageThresholdCount() {
            return 0;
        }

        @Override
        public boolean isUsageThresholdSupported() {
            return false;
        }

        @Override
        public long getCollectionUsageThreshold() {
            return 0;
        }

        @Override
        public void setCollectionUsageThreshold(long threshold) {
            throw new UnsupportedOperationException("Fixed MXBean stubs are immutable");
        }

        @Override
        public boolean isCollectionUsageThresholdExceeded() {
            return false;
        }

        @Override
        public long getCollectionUsageThresholdCount() {
            return 0;
        }

        @Override
        public MemoryUsage getCollectionUsage() {
            return collectionUsage;
        }

        @Override
        public boolean isCollectionUsageThresholdSupported() {
            return false;
        }

        @Override
        public ObjectName getObjectName() {
            return objectName("java.lang:type=MemoryPool,name=" + name);
        }
    }

    private static final class FixedUnixOperatingSystemMXBean implements UnixOperatingSystemMXBean {
        private final long openFileDescriptorCount;
        private final long maxFileDescriptorCount;

        FixedUnixOperatingSystemMXBean(long openFileDescriptorCount, long maxFileDescriptorCount) {
            this.openFileDescriptorCount = openFileDescriptorCount;
            this.maxFileDescriptorCount = maxFileDescriptorCount;
        }

        @Override
        public long getOpenFileDescriptorCount() {
            return openFileDescriptorCount;
        }

        @Override
        public long getMaxFileDescriptorCount() {
            return maxFileDescriptorCount;
        }

        @Override
        public long getCommittedVirtualMemorySize() {
            return 0L;
        }

        @Override
        public long getTotalSwapSpaceSize() {
            return 0L;
        }

        @Override
        public long getFreeSwapSpaceSize() {
            return 0L;
        }

        @Override
        public long getProcessCpuTime() {
            return 0L;
        }

        @Override
        public long getFreeMemorySize() {
            return 0L;
        }

        @Override
        public long getTotalMemorySize() {
            return 0L;
        }

        @Override
        public double getCpuLoad() {
            return 0.0d;
        }

        @Override
        public double getProcessCpuLoad() {
            return 0.0d;
        }

        @Override
        public String getName() {
            return "test-os";
        }

        @Override
        public String getArch() {
            return "test-arch";
        }

        @Override
        public String getVersion() {
            return "test-version";
        }

        @Override
        public int getAvailableProcessors() {
            return 1;
        }

        @Override
        public double getSystemLoadAverage() {
            return 0.0d;
        }

        @Override
        public ObjectName getObjectName() {
            return objectName("java.lang:type=OperatingSystem");
        }
    }

    private static final class FixedGarbageCollectorMXBean implements GarbageCollectorMXBean {
        private final String name;
        private final long collectionCount;
        private final long collectionTime;

        FixedGarbageCollectorMXBean(String name, long collectionCount, long collectionTime) {
            this.name = name;
            this.collectionCount = collectionCount;
            this.collectionTime = collectionTime;
        }

        @Override
        public long getCollectionCount() {
            return collectionCount;
        }

        @Override
        public long getCollectionTime() {
            return collectionTime;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public String[] getMemoryPoolNames() {
            return new String[] {"test-pool" };
        }

        @Override
        public ObjectName getObjectName() {
            return objectName("java.lang:type=GarbageCollector,name=" + name);
        }
    }
}
