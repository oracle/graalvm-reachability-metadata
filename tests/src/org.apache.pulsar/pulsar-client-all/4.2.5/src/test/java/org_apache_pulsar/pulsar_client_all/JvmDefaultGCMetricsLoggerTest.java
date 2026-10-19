/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.apache.pulsar.common.stats.JvmDefaultGCMetricsLogger;
import org.apache.pulsar.common.stats.Metrics;
import org.junit.jupiter.api.Test;

public class JvmDefaultGCMetricsLoggerTest {
    @Test
    void refreshesGarbageCollectorAndSafepointMetrics() {
        JvmDefaultGCMetricsLogger logger = new JvmDefaultGCMetricsLogger();

        logger.refresh();
        logger.refresh();
        Metrics metrics = Metrics.create(Map.of("component", "client"));
        logger.logMetrics(metrics);

        assertThat(metrics.getDimensions()).containsEntry("component", "client");
        assertThat(metrics.getMetrics()).containsKeys("jvm_full_gc_pause", "jvm_full_gc_count");
        assertThat(metrics.getMetrics().get("jvm_full_gc_pause")).isInstanceOf(Long.class);
        assertThat(metrics.getMetrics().get("jvm_full_gc_count")).isInstanceOf(Long.class);
    }
}
