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

public class StdKeyDeserializerInnerStringCtorKeyDeserializerTest {
    @Test
    void deserializesMapKeyUsingSingleStringConstructor() throws Exception {
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
                .containsEntry(new ConstructedKey("alpha"), "first")
                .containsEntry(new ConstructedKey("beta"), "second");
    }

    public static class KeyedValues {
        public Map<ConstructedKey, String> values;
    }

    public static final class ConstructedKey {
        private final String value;

        public ConstructedKey(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ConstructedKey)) {
                return false;
            }
            final ConstructedKey that = (ConstructedKey) other;
            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
