/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.junit.jupiter.api.Test;

public class BuilderBasedDeserializerTest {
    @Test
    void finishesObjectThroughConfiguredBuilder() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        BuiltMessage message = mapper.readValue("{\"text\":\"ready\"}", BuiltMessage.class);

        assertThat(message.text).isEqualTo("ready");
    }

    @JsonDeserialize(builder = MessageBuilder.class)
    public static class BuiltMessage {
        private final String text;

        private BuiltMessage(String text) {
            this.text = text;
        }
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class MessageBuilder {
        private String text;

        public MessageBuilder text(String value) {
            text = value;
            return this;
        }

        public BuiltMessage build() {
            return new BuiltMessage(text);
        }
    }
}
