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
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.junit.jupiter.api.Test;

public class BeanAsArrayBuilderDeserializerTest {
    @Test
    void finishesArrayShapedObjectThroughBuilder() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        ArrayMessage message = mapper.readValue("[\"ready\",3]", ArrayMessage.class);

        assertThat(message.text).isEqualTo("ready");
        assertThat(message.priority).isEqualTo(3);
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({"text", "priority"})
    @JsonDeserialize(builder = ArrayMessageBuilder.class)
    public static class ArrayMessage {
        private final String text;
        private final int priority;

        private ArrayMessage(String text, int priority) {
            this.text = text;
            this.priority = priority;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({"text", "priority"})
    @JsonPOJOBuilder(withPrefix = "")
    public static class ArrayMessageBuilder {
        private String text;
        private int priority;

        public ArrayMessageBuilder text(String value) {
            text = value;
            return this;
        }

        public ArrayMessageBuilder priority(int value) {
            priority = value;
            return this;
        }

        public ArrayMessage build() {
            return new ArrayMessage(text, priority);
        }
    }
}
