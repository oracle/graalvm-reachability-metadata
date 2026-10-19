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

public class StdKeyDeserializerInnerStringCtorKeyDeserializerTest {
    @Test
    void createsMapKeyThroughStringConstructor() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        Map<ConstructorKey, Integer> result = mapper.readValue(
                "{\"message\":7}", new TypeReference<Map<ConstructorKey, Integer>>() { });

        assertThat(result).hasSize(1);
        assertThat(result.keySet().iterator().next().value).isEqualTo("message");
        assertThat(result.values()).containsExactly(7);
    }

    public static class ConstructorKey {
        private final String value;

        public ConstructorKey(String value) {
            this.value = value;
        }
    }
}
