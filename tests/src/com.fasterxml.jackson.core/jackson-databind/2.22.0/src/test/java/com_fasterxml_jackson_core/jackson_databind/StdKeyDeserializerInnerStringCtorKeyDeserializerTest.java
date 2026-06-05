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

public class StdKeyDeserializerInnerStringCtorKeyDeserializerTest {

    @Test
    void deserializesMapKeyUsingStringConstructor() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JavaType mapType = objectMapper.getTypeFactory()
                .constructMapType(LinkedHashMap.class, ConstructorBackedKey.class, Integer.class);

        Map<ConstructorBackedKey, Integer> result = objectMapper.readValue("""
                {"alpha": 7}
                """, mapType);

        assertThat(result).containsEntry(new ConstructorBackedKey("alpha"), 7);
    }

    public static final class ConstructorBackedKey {
        private final String value;

        public ConstructorBackedKey(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ConstructorBackedKey)) {
                return false;
            }
            ConstructorBackedKey that = (ConstructorBackedKey) other;
            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }
}
