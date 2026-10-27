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

public class EnumResolverTest {
    @Test
    void resolvesAnEnumUsingItsJsonValueMethod() throws Exception {
        Shade shade = new ObjectMapper().readValue("\"r\"", Shade.class);

        assertThat(shade).isEqualTo(Shade.RED);
    }

    public enum Shade {
        RED("r"),
        BLUE("b");

        private final String code;

        Shade(String code) {
            this.code = code;
        }

        @JsonValue
        public String code() {
            return code;
        }
    }
}
