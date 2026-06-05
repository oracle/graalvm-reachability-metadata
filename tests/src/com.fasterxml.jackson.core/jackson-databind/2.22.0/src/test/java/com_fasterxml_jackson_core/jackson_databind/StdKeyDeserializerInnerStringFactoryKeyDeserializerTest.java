/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StdKeyDeserializerInnerStringFactoryKeyDeserializerTest {

    @Test
    void deserializesMapKeyUsingStringFactoryMethod() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JavaType mapType = objectMapper.getTypeFactory()
                .constructMapType(LinkedHashMap.class, FactoryBackedKey.class, Integer.class);

        Map<FactoryBackedKey, Integer> result = objectMapper.readValue("""
                {"alpha": 7}
                """, mapType);

        assertThat(result).containsEntry(FactoryBackedKey.valueOf("alpha"), 7);
    }

    public static final class FactoryBackedKey {
        private final String value;

        private FactoryBackedKey(String value) {
            this.value = value;
        }

        public static FactoryBackedKey valueOf(String value) {
            return new FactoryBackedKey(value);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof FactoryBackedKey)) {
                return false;
            }
            FactoryBackedKey that = (FactoryBackedKey) other;
            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }
}
