/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hadoop.metrics2.MetricsSource;
import org.apache.hadoop.metrics2.annotation.Metric;
import org.apache.hadoop.metrics2.annotation.Metrics;
import org.apache.hadoop.metrics2.lib.MetricsAnnotations;
import org.apache.hadoop.metrics2.lib.MetricsRegistry;
import org.apache.hadoop.metrics2.lib.MetricsSourceBuilder;
import org.apache.hadoop.metrics2.lib.MutableGaugeInt;
import org.junit.jupiter.api.Test;

public class MetricsSourceBuilderTest {
    @Test
    void annotatedFieldsAreInitializedInExistingRegistry() {
        AnnotatedMetricsSource source = new AnnotatedMetricsSource();

        MetricsSourceBuilder builder = MetricsAnnotations.newSourceBuilder(source);
        MetricsSource metricsSource = builder.build();

        assertThat(metricsSource).isNotNull();
        assertThat(builder.info().name()).isEqualTo("AnnotatedMetricsSource");
        assertThat(source.operations).isNotNull();
        assertThat(source.registry.get("Operations")).isSameAs(source.operations);
    }

    @Metrics(context = "metrics-source-builder-test")
    private static class AnnotatedMetricsSource {
        private final MetricsRegistry registry = new MetricsRegistry("ExistingRegistry");

        @Metric({ "Operations", "Number of operations" })
        private MutableGaugeInt operations;
    }
}
