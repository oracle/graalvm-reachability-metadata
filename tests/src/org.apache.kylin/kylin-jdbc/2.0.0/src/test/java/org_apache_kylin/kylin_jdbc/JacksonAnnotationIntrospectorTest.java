/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonAnnotationIntrospectorTest {
    @Test
    void usesTheJsonPropertyNameDeclaredOnAnEnumConstant() throws Exception {
        assertThat(new ObjectMapper().writeValueAsString(State.READY)).isEqualTo("\"ready-to-use\"");
    }

    public enum State {
        @JsonProperty("ready-to-use")
        READY
    }
}
