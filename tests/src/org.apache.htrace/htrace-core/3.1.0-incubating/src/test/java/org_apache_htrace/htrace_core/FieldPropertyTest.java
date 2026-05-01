/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_htrace.htrace_core;

import org.apache.htrace.fasterxml.jackson.databind.ObjectMapper;
import org.apache.htrace.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.htrace.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldPropertyTest {
    @Test
    void deserializesPojoBuilderByWritingBuilderFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        TraceRecord record = mapper.readValue(
                """
                {
                  "traceId":"trace-field-property",
                  "spanCount":3,
                  "sampled":true
                }
                """,
                TraceRecord.class);

        assertThat(record.getTraceId()).isEqualTo("trace-field-property");
        assertThat(record.getSpanCount()).isEqualTo(3);
        assertThat(record.isSampled()).isTrue();
    }

    @JsonDeserialize(builder = TraceRecord.Builder.class)
    public static class TraceRecord {
        private final String traceId;
        private final int spanCount;
        private final boolean sampled;

        private TraceRecord(Builder builder) {
            this.traceId = builder.traceId;
            this.spanCount = builder.spanCount;
            this.sampled = builder.sampled;
        }

        public String getTraceId() {
            return traceId;
        }

        public int getSpanCount() {
            return spanCount;
        }

        public boolean isSampled() {
            return sampled;
        }

        @JsonPOJOBuilder(withPrefix = "")
        public static class Builder {
            public String traceId;
            public int spanCount;
            public boolean sampled;

            public TraceRecord build() {
                return new TraceRecord(this);
            }
        }
    }
}
