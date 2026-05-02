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

public class StdKeyDeserializerInnerStringFactoryKeyDeserializerTest {
    @Test
    void mapKeysAreCreatedWithStaticStringFactoryMethod() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JavaType mapType = mapper.getTypeFactory().constructMapType(
                LinkedHashMap.class,
                FactoryBackedKey.class,
                Integer.class);

        Map<FactoryBackedKey, Integer> result = mapper.readValue(
                "{\"primary\":1,\"secondary\":2}",
                mapType);

        assertThat(result)
                .containsEntry(FactoryBackedKey.valueOf("primary"), Integer.valueOf(1))
                .containsEntry(FactoryBackedKey.valueOf("secondary"), Integer.valueOf(2));
        assertThat(result.keySet())
                .extracting(FactoryBackedKey::getValue)
                .containsExactly("factory:primary", "factory:secondary");
    }

    public static class FactoryBackedKey {
        private final String value;
        private final int marker;

        private FactoryBackedKey(String value, int marker) {
            this.value = value;
            this.marker = marker;
        }

        public static FactoryBackedKey valueOf(String value) {
            return new FactoryBackedKey("factory:" + value, 83);
        }

        public String getValue() {
            return value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof FactoryBackedKey)) {
                return false;
            }
            FactoryBackedKey otherKey = (FactoryBackedKey) other;
            return marker == otherKey.marker && value.equals(otherKey.value);
        }

        @Override
        public int hashCode() {
            return 31 * value.hashCode() + marker;
        }
    }
}
