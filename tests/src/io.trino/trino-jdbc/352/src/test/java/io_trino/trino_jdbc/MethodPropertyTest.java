/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.jackson.annotation.JsonIdentityInfo;
import io.trino.jdbc.$internal.jackson.annotation.ObjectIdGenerators;
import io.trino.jdbc.$internal.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class MethodPropertyTest {
    @Test
    void bindsAndUpdatesThroughSetter() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Message message = mapper.readValue("{\"text\":\"hello\"}", Message.class);
        assertEquals("hello", message.getText());
        mapper.readerForUpdating(message).readValue("{\"text\":\"updated\"}");
        assertEquals("updated", message.getText());
    }

    @Test
    void bindsIdentityThroughSetterAndResolvesReference() throws Exception {
        MethodNode node = new ObjectMapper().readValue("{\"id\":\"root\",\"next\":\"root\"}", MethodNode.class);
        assertEquals("root", node.getId());
        assertSame(node, node.next);
    }

    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
    public static class MethodNode {
        private String id;
        public MethodNode next;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
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
