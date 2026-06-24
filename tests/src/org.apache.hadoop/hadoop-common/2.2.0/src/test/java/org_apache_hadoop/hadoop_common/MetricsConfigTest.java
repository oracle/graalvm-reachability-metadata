/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.configuration.SubsetConfiguration;
import org.apache.hadoop.metrics2.MetricsRecord;
import org.apache.hadoop.metrics2.MetricsSink;
import org.apache.hadoop.metrics2.impl.MetricsSystemImpl;
import org.junit.jupiter.api.Test;

public class MetricsConfigTest {
    private static final String PREFIX = "metricsconfigtest";
    private static final String CONFIG_FILE = "hadoop-metrics2-" + PREFIX + ".properties";
    private static final AtomicInteger SINK_INIT_COUNT = new AtomicInteger();
    private static final AtomicReference<String> SINK_CLASS_PROPERTY = new AtomicReference<>();

    @Test
    void configuredSinkPluginIsLoadedAndInitialized() throws IOException {
        Path configFile = Paths.get(CONFIG_FILE).toAbsolutePath();
        Files.write(configFile, metricsConfig().getBytes(StandardCharsets.UTF_8));
        SINK_INIT_COUNT.set(0);
        SINK_CLASS_PROPERTY.set(null);

        MetricsSystemImpl metricsSystem = new MetricsSystemImpl();
        try {
            metricsSystem.init(PREFIX);

            assertThat(SINK_INIT_COUNT.get()).isEqualTo(1);
            assertThat(SINK_CLASS_PROPERTY.get()).isEqualTo(RecordingSink.class.getName());
        } finally {
            metricsSystem.shutdown();
            Files.deleteIfExists(configFile);
        }
    }

    private static String metricsConfig() {
        return String.join(System.lineSeparator(),
                PREFIX + ".sink.recording.class=" + RecordingSink.class.getName(),
                PREFIX + ".sink.recording.period=1",
                PREFIX + ".sink.recording.queue.capacity=1",
                "");
    }

    public static class RecordingSink implements MetricsSink {
        public RecordingSink() {
        }

        @Override
        public void init(SubsetConfiguration conf) {
            SINK_INIT_COUNT.incrementAndGet();
            SINK_CLASS_PROPERTY.set(conf.getString("class"));
        }

        @Override
        public void putMetrics(MetricsRecord record) {
        }

        @Override
        public void flush() {
        }
    }
}
