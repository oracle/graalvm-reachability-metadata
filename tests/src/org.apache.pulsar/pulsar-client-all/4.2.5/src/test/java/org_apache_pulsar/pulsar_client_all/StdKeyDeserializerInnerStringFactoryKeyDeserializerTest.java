/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.apache.pulsar.shade.com.fasterxml.jackson.core.type.TypeReference;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

public class StdKeyDeserializerInnerStringFactoryKeyDeserializerTest {
    @Test
    void createsMapKeyThroughStringFactory() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        Map<FactoryKey, Integer> result = mapper.readValue(
                "{\"message\":9}", new TypeReference<Map<FactoryKey, Integer>>() { });

        assertThat(result).hasSize(1);
        assertThat(result.keySet().iterator().next().value).isEqualTo("MESSAGE");
        assertThat(result.values()).containsExactly(9);
    }

    public static class FactoryKey {
        private final String value;

        private FactoryKey(String value, boolean normalized) {
            this.value = normalized ? value : value.toUpperCase(java.util.Locale.ROOT);
        }

        public static FactoryKey valueOf(String value) {
            return new FactoryKey(value.toUpperCase(java.util.Locale.ROOT), true);
        }
    }
}
