/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import java.util.Map;

import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.core.type.TypeReference;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StdKeyDeserializerInnerStringFactoryKeyDeserializerTest {
    @Test
    void createsMapKeysUsingAStringFactory() throws Exception {
        Map<Metric, Integer> values = new ObjectMapper().readValue(
                "{\"revenue\":8}", new TypeReference<Map<Metric, Integer>>() {});

        assertThat(values).containsEntry(Metric.valueOf("revenue"), 8);
    }

    public static class Metric {
        private final String value;

        private Metric(String value) {
            this.value = value;
        }

        public static Metric valueOf(String value) {
            return new Metric(value);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Metric && value.equals(((Metric) other).value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
