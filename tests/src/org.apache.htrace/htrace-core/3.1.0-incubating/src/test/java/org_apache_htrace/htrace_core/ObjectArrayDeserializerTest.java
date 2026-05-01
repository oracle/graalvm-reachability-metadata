/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_htrace.htrace_core;

import org.apache.htrace.fasterxml.jackson.databind.DeserializationFeature;
import org.apache.htrace.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectArrayDeserializerTest {
    @Test
    void deserializesSingleJsonValueAsObjectArrayWithConfiguredCoercion() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

        TraceRecord[] values = mapper.readValue("{\"name\":\"single-value\"}", TraceRecord[].class);

        assertThat(values).hasSize(1);
        assertThat(values[0].name).isEqualTo("single-value");
        assertThat(values.getClass()).isSameAs(TraceRecord[].class);
    }

    public static class TraceRecord {
        public String name;
    }
}
