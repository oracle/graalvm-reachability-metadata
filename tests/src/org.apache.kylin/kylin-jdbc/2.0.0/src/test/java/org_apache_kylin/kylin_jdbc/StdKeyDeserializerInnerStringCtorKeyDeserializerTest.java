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

public class StdKeyDeserializerInnerStringCtorKeyDeserializerTest {
    @Test
    void createsMapKeysUsingAStringConstructor() throws Exception {
        Map<Region, Integer> values = new ObjectMapper().readValue(
                "{\"north\":4}", new TypeReference<Map<Region, Integer>>() {});

        assertThat(values).containsEntry(new Region("north"), 4);
    }

    public static class Region {
        private final String value;

        public Region(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Region && value.equals(((Region) other).value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
