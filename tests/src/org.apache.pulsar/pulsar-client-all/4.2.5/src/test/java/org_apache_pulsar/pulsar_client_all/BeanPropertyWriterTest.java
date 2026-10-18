/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

public class BeanPropertyWriterTest {

    @Test
    void serializesFieldAndGetterPropertiesAsArrayElements() throws Exception {
        final ArrayMessage message = new ArrayMessage();
        message.topic = "orders";
        message.sequence = 41;

        assertThat(new ObjectMapper().writeValueAsString(message)).isEqualTo("[\"orders\",41]");
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({"topic", "sequence"})
    public static final class ArrayMessage {
        public String topic;
        private int sequence;

        public int getSequence() {
            return sequence;
        }
    }
}
