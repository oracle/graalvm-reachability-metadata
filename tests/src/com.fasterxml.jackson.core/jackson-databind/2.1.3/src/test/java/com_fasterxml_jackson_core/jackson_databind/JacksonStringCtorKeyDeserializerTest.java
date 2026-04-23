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

public class JacksonStringCtorKeyDeserializerTest {

    @Test
    void stringConstructorKeyDeserializerCreatesMapKeys() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<CtorKey, Integer> values = mapper.readValue("{\"alpha\":1}", new TypeReference<Map<CtorKey, Integer>>() { });
        assertThat(values).containsEntry(new CtorKey("alpha"), 1);
    }

    static class CtorKey {

        private final String value;

        CtorKey(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CtorKey)) {
                return false;
            }
            return value.equals(((CtorKey) other).value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
