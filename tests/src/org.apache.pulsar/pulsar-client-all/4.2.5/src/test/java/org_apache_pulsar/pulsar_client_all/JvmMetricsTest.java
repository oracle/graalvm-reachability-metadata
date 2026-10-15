/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pulsar.common.stats.JvmGCMetricsLogger;
import org.apache.pulsar.common.stats.JvmMetrics;
import org.apache.pulsar.common.stats.Metrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class JvmMetricsTest {
    @Test
    void loadsConfiguredGcLoggerAndGeneratesMetrics() {
        RecordingLogger.logCalls.set(0);
        JvmMetrics metrics = JvmMetrics.create(
                null, "pulsar_client", RecordingLogger.class.getName());

        assertThat(metrics.generate()).hasSize(1);
        assertThat(RecordingLogger.logCalls.get()).isGreaterThan(0);
    }

    public static final class RecordingLogger implements JvmGCMetricsLogger {
        private static final AtomicInteger logCalls = new AtomicInteger();

        public RecordingLogger() { }

        @Override
        public void logMetrics(Metrics metrics) {
            logCalls.incrementAndGet();
            metrics.put("recording_gc_count", 1L);
        }

        @Override
        public void refresh() { }
    }
}
