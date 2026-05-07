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
    void createInstantiatesConfiguredGCMetricsLoggerClass() {
        JvmMetrics jvmMetrics = JvmMetrics.create(null, "jvm_metrics_test", JvmG1GCMetricsLogger.class.getName());

        List<Metrics> generatedMetrics = jvmMetrics.generate();

        assertThat(generatedMetrics).singleElement().satisfies(metrics -> {
            assertThat(metrics.getMetrics())
                    .containsKeys(
                            "jvm_start_time",
                            "jvm_heap_used",
                            "jvm_gc_young_pause",
                            "jvm_gc_young_count",
                            "jvm_gc_old_pause",
                            "jvm_gc_old_count",
                            "jvm_metrics_test_default_pool_allocated",
                            "jvm_metrics_test_default_pool_used")
                    .doesNotContainKey("jvm_full_gc_pause");
        });
    }
}
