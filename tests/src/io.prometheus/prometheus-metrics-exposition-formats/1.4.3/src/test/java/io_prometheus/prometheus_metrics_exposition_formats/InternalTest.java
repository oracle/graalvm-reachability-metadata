/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_prometheus.prometheus_metrics_exposition_formats;

import io.prometheus.metrics.expositionformats.generated.com_google_protobuf_4_33_0.Metrics;
import io.prometheus.metrics.shaded.com_google_protobuf_4_33_0.TextFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InternalTest {
    @Test
    void parsesTextUsingGeneratedMessageClass() throws Exception {
        Metrics.MetricFamily family = TextFormat.parse("""
                name: "completed_jobs"
                type: COUNTER
                metric {
                  label { name: "result" value: "success" }
                  counter { value: 9 }
                }
                """, Metrics.MetricFamily.class);

        assertThat(family.getName()).isEqualTo("completed_jobs");
        assertThat(family.getType()).isEqualTo(Metrics.MetricType.COUNTER);
        assertThat(family.getMetric(0).getCounter().getValue()).isEqualTo(9);
        assertThat(family.getMetric(0).getLabel(0).getValue()).isEqualTo("success");
    }
}
