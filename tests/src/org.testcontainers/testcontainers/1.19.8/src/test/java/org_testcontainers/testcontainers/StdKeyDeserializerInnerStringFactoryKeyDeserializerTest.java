/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.core.type.TypeReference;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class StdKeyDeserializerInnerStringFactoryKeyDeserializerTest {
    @Test
    void createsMapKeysThroughAStringFactory() throws Exception {
        Map<FactoryKey, Integer> result = new ObjectMapper()
            .readValue("{\"beta\":2}", new TypeReference<Map<FactoryKey, Integer>>() {});

        assertThat(result).containsEntry(FactoryKey.valueOf("beta"), 2);
    }

    public static class FactoryKey {
        private final String value;

        private FactoryKey(String value) {
            this.value = value;
        }

        public static FactoryKey valueOf(String value) {
            return new FactoryKey(value);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof FactoryKey && value.equals(((FactoryKey) other).value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
