/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.pulsar.common.stats.JvmG1GCMetricsLogger;
import org.apache.pulsar.common.stats.JvmMetrics;
import org.apache.pulsar.common.stats.Metrics;
import org.junit.jupiter.api.Test;

public class JvmMetricsTest {

    @Test
    void createsConfiguredGarbageCollectorMetricsLoggerByClassName() {
        final JvmMetrics metrics = JvmMetrics.create(null, "broker", JvmG1GCMetricsLogger.class.getName());

        final List<Metrics> generatedMetrics = metrics.generate();

        assertThat(generatedMetrics).hasSize(1);
        assertThat(generatedMetrics.get(0).getDimensions()).containsEntry("metric", "jvm_metrics");
        assertThat(generatedMetrics.get(0).getMetrics())
                .containsKeys(
                        "jvm_gc_young_pause",
                        "jvm_gc_young_count",
                        "jvm_gc_old_pause",
                        "jvm_gc_old_count",
                        "broker_default_pool_allocated",
                        "broker_default_pool_used");
    }
}
