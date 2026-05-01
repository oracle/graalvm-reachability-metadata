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

public class BuilderBasedDeserializerTest {
    @Test
    void deserializesObjectByInvokingPojoBuilderBuildMethod() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        TraceSummary summary = mapper.readValue(
                "{\"description\":\"builder-created\",\"traceId\":42,\"sampled\":true}",
                TraceSummary.class);

        assertThat(summary.getDescription()).isEqualTo("builder-created");
        assertThat(summary.getTraceId()).isEqualTo(42L);
        assertThat(summary.isSampled()).isTrue();
    }

    @JsonDeserialize(builder = TraceSummary.Builder.class)
    public static class TraceSummary {
        private final String description;
        private final long traceId;
        private final boolean sampled;

        private TraceSummary(Builder builder) {
            this.description = builder.description;
            this.traceId = builder.traceId;
            this.sampled = builder.sampled;
        }

        public String getDescription() {
            return description;
        }

        public long getTraceId() {
            return traceId;
        }

        public boolean isSampled() {
            return sampled;
        }

        @JsonPOJOBuilder(withPrefix = "with")
        public static class Builder {
            private String description;
            private long traceId;
            private boolean sampled;

            public Builder withDescription(String description) {
                this.description = description;
                return this;
            }

            public Builder withTraceId(long traceId) {
                this.traceId = traceId;
                return this;
            }

            public Builder withSampled(boolean sampled) {
                this.sampled = sampled;
                return this;
            }

            public TraceSummary build() {
                return new TraceSummary(this);
            }
        }
    }
}
