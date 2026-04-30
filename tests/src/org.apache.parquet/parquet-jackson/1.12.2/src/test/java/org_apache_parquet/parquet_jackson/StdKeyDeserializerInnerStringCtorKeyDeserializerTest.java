/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.databind.JavaType;
import shaded.parquet.com.fasterxml.jackson.databind.ObjectMapper;

public class StdKeyDeserializerInnerStringCtorKeyDeserializerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void deserializesMapKeyUsingStringConstructor() throws Exception {
        JavaType mapType = MAPPER.getTypeFactory()
                .constructMapType(LinkedHashMap.class, HeaderName.class, String.class);

        Map<HeaderName, String> values = MAPPER.readValue("""
                {
                  "x-request-id": "7f2a",
                  "x-region": "eu-central"
                }
                """, mapType);

        assertThat(values)
                .containsEntry(new HeaderName("x-request-id"), "7f2a")
                .containsEntry(new HeaderName("x-region"), "eu-central");
    }

    public static final class HeaderName {
        private final String value;

        public HeaderName(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof HeaderName)) {
                return false;
            }
            HeaderName headerName = (HeaderName) other;
            return Objects.equals(value, headerName.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }
}
