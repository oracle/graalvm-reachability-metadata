/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonAnnotationIntrospectorTest {
    @Test
    void usesTheJsonNameDeclaredOnAnEnumField() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        assertThat(mapper.writeValueAsString(State.READY)).isEqualTo("\"ready-to-run\"");
        assertThat(mapper.readValue("\"ready-to-run\"", State.class)).isEqualTo(State.READY);
    }

    public enum State {
        @JsonProperty("ready-to-run")
        READY
    }
}
