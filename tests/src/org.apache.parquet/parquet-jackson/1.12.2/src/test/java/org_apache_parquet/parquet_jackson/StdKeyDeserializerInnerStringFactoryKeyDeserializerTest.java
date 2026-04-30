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

public class StdKeyDeserializerInnerStringFactoryKeyDeserializerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void deserializesMapKeyUsingStringFactoryMethod() throws Exception {
        JavaType mapType = MAPPER.getTypeFactory()
                .constructMapType(LinkedHashMap.class, RoutingKey.class, Integer.class);

        Map<RoutingKey, Integer> weights = MAPPER.readValue("""
                {
                  "primary.us-east": 12,
                  "backup.eu-west": 7
                }
                """, mapType);

        assertThat(weights)
                .containsEntry(RoutingKey.valueOf("primary.us-east"), 12)
                .containsEntry(RoutingKey.valueOf("backup.eu-west"), 7);
    }

    public static final class RoutingKey {
        private final String value;
        private final String region;

        private RoutingKey(String value, String region) {
            this.value = value;
            this.region = region;
        }

        public static RoutingKey valueOf(String value) {
            int separatorIndex = value.indexOf('.');
            String region = separatorIndex >= 0 ? value.substring(separatorIndex + 1) : "global";
            return new RoutingKey(value, region);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof RoutingKey)) {
                return false;
            }
            RoutingKey routingKey = (RoutingKey) other;
            return Objects.equals(value, routingKey.value)
                    && Objects.equals(region, routingKey.region);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value, region);
        }
    }
}
