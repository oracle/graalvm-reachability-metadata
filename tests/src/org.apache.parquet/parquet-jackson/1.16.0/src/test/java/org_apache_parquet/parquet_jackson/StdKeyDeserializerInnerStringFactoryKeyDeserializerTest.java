/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.databind.ObjectMapper;

public class StdKeyDeserializerInnerStringFactoryKeyDeserializerTest {
    @Test
    void deserializesMapKeyUsingStaticStringFactoryMethod() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();

        final KeyedValues values = mapper.readValue("""
                {
                  "values" : {
                    "alpha" : "first",
                    "beta" : "second"
                  }
                }
                """, KeyedValues.class);

        assertThat(values.values)
                .containsEntry(StringFactoryKey.valueOf("alpha"), "first")
                .containsEntry(StringFactoryKey.valueOf("beta"), "second");
    }

    public static class KeyedValues {
        public Map<StringFactoryKey, String> values;
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
            final StringFactoryKey that = (StringFactoryKey) other;
            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
