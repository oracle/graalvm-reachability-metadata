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

public class StdKeyDeserializerInnerStringCtorKeyDeserializerTest {
    @Test
    void constructsMapKeysFromStrings() throws Exception {
        Map<ConstructorKey, Integer> result = new ObjectMapper()
            .readValue("{\"alpha\":1}", new TypeReference<Map<ConstructorKey, Integer>>() {});

        assertThat(result).containsEntry(new ConstructorKey("alpha"), 1);
    }

    public static class ConstructorKey {
        private final String value;

        public ConstructorKey(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ConstructorKey && value.equals(((ConstructorKey) other).value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
