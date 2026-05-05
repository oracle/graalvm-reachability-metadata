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

public class StdKeyDeserializerInnerStringCtorKeyDeserializerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void deserializesMapKeyUsingStringConstructor() throws Exception {
        JavaType mapType = MAPPER.getTypeFactory().constructMapType(
                LinkedHashMap.class,
                StringConstructorKey.class,
                String.class);

        Map<StringConstructorKey, String> values = MAPPER.readValue(
                "{\"alpha\":\"first\",\"beta\":\"second\"}",
                mapType);

        assertThat(values)
                .containsEntry(new StringConstructorKey("alpha"), "first")
                .containsEntry(new StringConstructorKey("beta"), "second");
    }

    public static final class StringConstructorKey {
        private final String value;

        public StringConstructorKey(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof StringConstructorKey)) {
                return false;
            }
            StringConstructorKey that = (StringConstructorKey) other;
            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
