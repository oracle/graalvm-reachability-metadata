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

public class InnerClassPropertyTest {
    @Test
    void deserializesNonStaticInnerClassProperty() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        Envelope envelope = mapper.readValue("{\"message\":{\"text\":\"hello\"}}", Envelope.class);

        assertThat(envelope.message).isNotNull();
        assertThat(envelope.message.text).isEqualTo("hello");
    }

    public static class Envelope {
        public Message message;

        public class Message {
            public String text;
        }
    }
}
