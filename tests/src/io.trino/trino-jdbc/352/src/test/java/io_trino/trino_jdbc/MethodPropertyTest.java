/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MethodPropertyTest {
    @Test
    void bindsAndUpdatesThroughSetter() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Message message = mapper.readValue("{\"text\":\"hello\"}", Message.class);
        assertEquals("hello", message.getText());
        mapper.readerForUpdating(message).readValue("{\"text\":\"updated\"}");
        assertEquals("updated", message.getText());
    }

    public static class Message {
        private String text;

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }
}
