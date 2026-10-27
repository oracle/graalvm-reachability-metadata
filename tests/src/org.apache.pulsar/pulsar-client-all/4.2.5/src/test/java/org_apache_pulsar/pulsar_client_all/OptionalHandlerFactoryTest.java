/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Date;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

public class OptionalHandlerFactoryTest {
    @Test
    void loadsSqlDateHandlersForRoundTrip() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Date original = Date.valueOf("2020-01-02");

        String json = mapper.writeValueAsString(original);
        Date restored = mapper.readValue(json, Date.class);

        assertThat(restored).isEqualTo(original);
    }
}
