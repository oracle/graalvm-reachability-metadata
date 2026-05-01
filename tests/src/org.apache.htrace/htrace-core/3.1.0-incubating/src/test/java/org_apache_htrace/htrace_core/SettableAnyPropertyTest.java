/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_htrace.htrace_core;

import org.apache.htrace.fasterxml.jackson.annotation.JsonAnySetter;
import org.apache.htrace.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class SettableAnyPropertyTest {
    @Test
    void deserializesUnknownFieldsByInvokingAnySetterMethod() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        TraceAnnotations annotations = mapper.readValue(
                """
                {
                  "spanId":"span-1",
                  "latencyMs":37,
                  "sampled":true,
                  "labels":{"component":"rpc","peer":"worker-1"}
                }
                """,
                TraceAnnotations.class);

        assertThat(annotations.spanId).isEqualTo("span-1");
        assertThat(annotations.attributes)
                .containsEntry("latencyMs", 37)
                .containsEntry("sampled", true);

        Object labels = annotations.attributes.get("labels");
        assertThat(labels).isInstanceOf(Map.class);
        Map<?, ?> labelMap = (Map<?, ?>) labels;
        assertThat(labelMap.get("component")).isEqualTo("rpc");
        assertThat(labelMap.get("peer")).isEqualTo("worker-1");
    }

    public static class TraceAnnotations {
        public String spanId;
        private final Map<String, Object> attributes = new LinkedHashMap<>();

        @JsonAnySetter
        public void addAttribute(String name, Object value) {
            attributes.put(name, value);
        }
    }
}
