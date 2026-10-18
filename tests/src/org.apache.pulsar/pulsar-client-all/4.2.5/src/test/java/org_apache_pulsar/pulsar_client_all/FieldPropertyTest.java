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
    void deserializesJsonDirectlyIntoPublicFields() throws Exception {
        final FieldMessage message = new ObjectMapper().readValue(
                "{\"topic\":\"orders\",\"sequence\":17}", FieldMessage.class);

        assertThat(message.topic).isEqualTo("orders");
        assertThat(message.sequence).isEqualTo(17);
    }

    public static final class FieldMessage {
        public String topic;
        public int sequence;
    }
}
