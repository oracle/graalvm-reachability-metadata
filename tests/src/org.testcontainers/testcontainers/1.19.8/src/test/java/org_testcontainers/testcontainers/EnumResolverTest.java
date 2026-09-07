/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import com.fasterxml.jackson.annotation.JsonValue;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

public class EnumResolverTest {
    @Test
    void resolvesEnumConstantsUsingAJsonValueMethod() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        assertThat(mapper.readValue("\"active-code\"", State.class)).isEqualTo(State.ACTIVE);
    }

    public enum State {
        ACTIVE("active-code"),
        IDLE("idle-code");

        private final String code;

        State(String code) {
            this.code = code;
        }

        @JsonValue
        public String code() {
            return code;
        }
    }
}
