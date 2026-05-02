/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import java.util.LinkedHashMap;
import java.util.Map;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.JavaType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StdKeyDeserializerInnerStringCtorKeyDeserializerTest {
    @Test
    void mapKeysAreCreatedWithSingleStringConstructor() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JavaType mapType = mapper.getTypeFactory().constructMapType(
                LinkedHashMap.class,
                ConstructorBackedKey.class,
                Integer.class);

        Map<ConstructorBackedKey, Integer> result = mapper.readValue(
                "{\"primary\":1,\"secondary\":2}",
                mapType);

        assertThat(result)
                .containsEntry(new ConstructorBackedKey("primary"), Integer.valueOf(1))
                .containsEntry(new ConstructorBackedKey("secondary"), Integer.valueOf(2));
        assertThat(result.keySet())
                .extracting(ConstructorBackedKey::getValue)
                .containsExactly("primary", "secondary");
    }

    public static class ConstructorBackedKey {
        private final String value;

        public ConstructorBackedKey(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ConstructorBackedKey)) {
                return false;
            }
            ConstructorBackedKey otherKey = (ConstructorBackedKey) other;
            return value.equals(otherKey.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
