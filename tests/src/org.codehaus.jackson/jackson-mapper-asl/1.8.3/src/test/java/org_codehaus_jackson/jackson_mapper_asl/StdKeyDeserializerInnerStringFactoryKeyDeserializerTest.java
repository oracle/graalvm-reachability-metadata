/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.JavaType;
import org.junit.jupiter.api.Test;

public class StdKeyDeserializerInnerStringFactoryKeyDeserializerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void deserializesMapKeyUsingStaticStringFactoryMethod() throws Exception {
        JavaType mapType = MAPPER.getTypeFactory().constructMapType(
                LinkedHashMap.class,
                StringFactoryKey.class,
                String.class);

        Map<StringFactoryKey, String> values = MAPPER.readValue(
                "{\"alpha\":\"first\",\"beta\":\"second\"}",
                mapType);

        assertThat(values)
                .containsEntry(StringFactoryKey.valueOf("alpha"), "first")
                .containsEntry(StringFactoryKey.valueOf("beta"), "second");
    }

    public static final class StringFactoryKey {
        private final String value;

        private StringFactoryKey(String value, boolean fromFactory) {
            this.value = value;
        }

        public static StringFactoryKey valueOf(String value) {
            return new StringFactoryKey(value, true);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof StringFactoryKey)) {
                return false;
            }
            StringFactoryKey that = (StringFactoryKey) other;
            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
