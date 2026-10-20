/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.annotation.JsonValue;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonValueSerializerTest {
    @Test
    void serializesAnObjectThroughItsJsonValueMethod() throws Exception {
        assertThat(new ObjectMapper().writeValueAsString(new Identifier("K-19"))).isEqualTo("\"K-19\"");
    }

    public static class Identifier {
        private final String value;

        public Identifier(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return value;
        }
    }
}
