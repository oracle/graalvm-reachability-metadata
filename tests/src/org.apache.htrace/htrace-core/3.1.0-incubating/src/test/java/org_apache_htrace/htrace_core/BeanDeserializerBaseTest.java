/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_htrace.htrace_core;

import org.apache.htrace.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanDeserializerBaseTest {
    @Test
    void deserializesNonStaticInnerClassValuedProperty() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        TraceEnvelope envelope = mapper.readValue(
                "{\"span\":{\"traceId\":\"trace-1\",\"sampled\":true}}",
                TraceEnvelope.class);

        assertThat(envelope.span).isNotNull();
        assertThat(envelope.span.traceId).isEqualTo("trace-1");
        assertThat(envelope.span.sampled).isTrue();
        assertThat(envelope.span.owner()).isSameAs(envelope);
    }

    public static class TraceEnvelope {
        public SpanPayload span;

        public class SpanPayload {
            public String traceId;
            public boolean sampled;

            public TraceEnvelope owner() {
                return TraceEnvelope.this;
            }
        }
    }
}
