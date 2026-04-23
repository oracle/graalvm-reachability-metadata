/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonStringFactoryKeyDeserializerTest {

    @Test
    void stringFactoryKeyDeserializerCreatesMapKeys() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<FactoryKey, Integer> values = mapper.readValue("{\"beta\":2}", new TypeReference<Map<FactoryKey, Integer>>() { });
        assertThat(values).containsEntry(FactoryKey.valueOf("beta"), 2);
    }

    static class FactoryKey {

        private final String value;

        private FactoryKey(String value) {
            this.value = value;
        }

        public static FactoryKey valueOf(String value) {
            return new FactoryKey(value);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof FactoryKey)) {
                return false;
            }
            return value.equals(((FactoryKey) other).value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
