/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

public class FieldPropertyTest {
    @Test
    void deserializesDirectlyIntoFields() throws Exception {
        final FieldConfiguration configuration = new ObjectMapper().readValue(
                "{\"serviceUrl\":\"pulsar://broker:6650\",\"connections\":3}", FieldConfiguration.class);

        assertThat(configuration.serviceUrl).isEqualTo("pulsar://broker:6650");
        assertThat(configuration.connections).isEqualTo(3);
    }

    public static final class FieldConfiguration {
        public String serviceUrl;
        public int connections;
    }
}
