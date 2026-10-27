/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.pulsar.common.stats.JvmG1GCMetricsLogger;
import org.apache.pulsar.common.stats.JvmMetrics;
import org.apache.pulsar.common.stats.Metrics;
import org.junit.jupiter.api.Test;

public class JvmMetricsTest {
    @Test
    void createsConfiguredGarbageCollectorLoggerByClassName() {
        JvmMetrics metrics = JvmMetrics.create(null, "client", JvmG1GCMetricsLogger.class.getName());

        List<Metrics> generated = metrics.generate();

        assertThat(generated).hasSize(1);
        assertThat(generated.get(0).getDimensions()).containsEntry("metric", "jvm_metrics");
        assertThat(generated.get(0).getMetrics())
                .containsKeys("jvm_gc_young_pause", "jvm_gc_old_pause", "client_default_pool_allocated");
    }
}
