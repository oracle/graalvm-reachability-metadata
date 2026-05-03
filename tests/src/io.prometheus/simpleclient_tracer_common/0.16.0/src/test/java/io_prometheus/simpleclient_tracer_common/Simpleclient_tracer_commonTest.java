/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_prometheus.simpleclient_tracer_common;

import io.prometheus.client.exemplars.tracer.common.SpanContextSupplier;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class Simpleclient_tracer_commonTest {
    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN_ID = "00f067aa0ba902b7";

    @Test
    void sampledSpanContextExposesTraceIdSpanIdAndSamplingDecision() {
        SpanContextSupplier supplier = new FixedSpanContextSupplier(TRACE_ID, SPAN_ID, true);

        assertThat(supplier.getTraceId()).isEqualTo(TRACE_ID);
        assertThat(supplier.getSpanId()).isEqualTo(SPAN_ID);
        assertThat(supplier.isSampled()).isTrue();
    }

    @Test
    void unsampledSpanContextStillExposesTraceAndSpanIdentifiers() {
        SpanContextSupplier supplier = new FixedSpanContextSupplier(TRACE_ID, SPAN_ID, false);

        assertThat(supplier.getTraceId()).isEqualTo(TRACE_ID);
        assertThat(supplier.getSpanId()).isEqualTo(SPAN_ID);
        assertThat(supplier.isSampled()).isFalse();
    }

    @Test
    void unsampledSpanContextIsNotSelectedForExemplarLabels() {
        SpanContextSupplier supplier = new FixedSpanContextSupplier(TRACE_ID, SPAN_ID, false);

        assertThat(exemplarLabelsFor(supplier)).isEmpty();
    }

    @Test
    void supplierCanRepresentAbsenceOfCurrentSpan() {
        SpanContextSupplier supplier = new FixedSpanContextSupplier(null, null, false);

        assertThat(supplier.getTraceId()).isNull();
        assertThat(supplier.getSpanId()).isNull();
        assertThat(supplier.isSampled()).isFalse();
        assertThat(exemplarLabelsFor(supplier)).isEmpty();
    }

    @Test
    void consumerCanUseSpanContextSupplierWithoutKnowingImplementationType() {
        SpanContextSupplier supplier = new SpanContextSupplier() {
            @Override
            public String getTraceId() {
                return TRACE_ID;
            }

            @Override
            public String getSpanId() {
                return SPAN_ID;
            }

            @Override
            public boolean isSampled() {
                return true;
            }
        };

        assertThat(exemplarLabelsFor(supplier))
                .containsExactly(
                        Map.entry("trace_id", TRACE_ID),
                        Map.entry("span_id", SPAN_ID));
    }

    @Test
    void sameSupplierInstanceCanRepresentChangingCurrentSpan() {
        MutableSpanContextSupplier supplier = new MutableSpanContextSupplier();

        supplier.setCurrentSpan(TRACE_ID, SPAN_ID, true);

        assertThat(supplier.getTraceId()).isEqualTo(TRACE_ID);
        assertThat(supplier.getSpanId()).isEqualTo(SPAN_ID);
        assertThat(supplier.isSampled()).isTrue();

        String nextTraceId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String nextSpanId = "bbbbbbbbbbbbbbbb";
        supplier.setCurrentSpan(nextTraceId, nextSpanId, false);

        assertThat(supplier.getTraceId()).isEqualTo(nextTraceId);
        assertThat(supplier.getSpanId()).isEqualTo(nextSpanId);
        assertThat(supplier.isSampled()).isFalse();
    }

    private static Map<String, String> exemplarLabelsFor(SpanContextSupplier supplier) {
        if (supplier.getTraceId() == null || supplier.getSpanId() == null || !supplier.isSampled()) {
            return Map.of();
        }

        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("trace_id", supplier.getTraceId());
        labels.put("span_id", supplier.getSpanId());
        return labels;
    }

    private static final class FixedSpanContextSupplier implements SpanContextSupplier {
        private final String traceId;
        private final String spanId;
        private final boolean sampled;

        private FixedSpanContextSupplier(String traceId, String spanId, boolean sampled) {
            this.traceId = traceId;
            this.spanId = spanId;
            this.sampled = sampled;
        }

        @Override
        public String getTraceId() {
            return traceId;
        }

        @Override
        public String getSpanId() {
            return spanId;
        }

        @Override
        public boolean isSampled() {
            return sampled;
        }
    }

    private static final class MutableSpanContextSupplier implements SpanContextSupplier {
        private String traceId;
        private String spanId;
        private boolean sampled;

        private void setCurrentSpan(String traceId, String spanId, boolean sampled) {
            this.traceId = traceId;
            this.spanId = spanId;
            this.sampled = sampled;
        }

        @Override
        public String getTraceId() {
            return traceId;
        }

        @Override
        public String getSpanId() {
            return spanId;
        }

        @Override
        public boolean isSampled() {
            return sampled;
        }
    }
}
