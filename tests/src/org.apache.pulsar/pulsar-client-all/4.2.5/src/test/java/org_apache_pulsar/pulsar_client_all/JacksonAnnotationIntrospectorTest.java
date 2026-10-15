/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class JacksonAnnotationIntrospectorTest {
    @Test
    void honorsEnumNamesAndAliases() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        String encoded = mapper.writeValueAsString(Mode.SHARED);
        Mode alias = mapper.readValue("\"shared-subscription\"", Mode.class);

        assertThat(encoded).isEqualTo("\"shared\"");
        assertThat(alias).isEqualTo(Mode.SHARED);
    }

    private enum Mode {
        @JsonProperty("shared")
        @JsonAlias("shared-subscription")
        SHARED
    }
}
