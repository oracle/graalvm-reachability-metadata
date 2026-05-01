/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_htrace.htrace_core;

import org.apache.htrace.fasterxml.jackson.annotation.JsonAutoDetect;
import org.apache.htrace.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class SetterlessPropertyTest {
    @Test
    void deserializesArrayIntoExistingCollectionReturnedByGetter() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        TracePayload payload = mapper.readValue(
                """
                {
                  "traceId":"trace-1",
                  "tags":["client", "sampled", "rpc"]
                }
                """,
                TracePayload.class);

        assertThat(payload.getTraceId()).isEqualTo("trace-1");
        assertThat(payload.getGetterInvocations()).isGreaterThan(0);
        assertThat(payload.getTags()).containsExactly("client", "sampled", "rpc");
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE)
    public static class TracePayload {
        private String traceId;
        private final List<String> tagValues = new ArrayList<>();
        private int getterInvocations;

        public String getTraceId() {
            return traceId;
        }

        public void setTraceId(String traceId) {
            this.traceId = traceId;
        }

        public List<String> getTags() {
            getterInvocations++;
            return tagValues;
        }

        public int getGetterInvocations() {
            return getterInvocations;
        }
    }
}
