/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mortbay_jetty.jetty_util;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mortbay.util.ajax.JSON;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class JSONTest {
    @Test
    public void reconstructsConvertibleFromItsJsonClassProperty() {
        JSON json = new JSON();
        Message original = new Message();
        original.setText("hello \"Jetty\"");
        original.setSequence(23);
        String document = json.toJSON(original);
        Message restored = (Message) json.fromJSON(document);
        assertNotSame(original, restored);
        assertEquals("hello \"Jetty\"", restored.getText());
        assertEquals(23, restored.getSequence());
    }

    public static class Message implements JSON.Convertible {
        private String text;
        private long sequence;

        public Message() {
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public long getSequence() {
            return sequence;
        }

        public void setSequence(long sequence) {
            this.sequence = sequence;
        }

        @Override
        public void toJSON(JSON.Output output) {
            output.addClass(Message.class);
            output.add("text", text);
            output.add("sequence", sequence);
        }

        @Override
        public void fromJSON(Map properties) {
            text = (String) properties.get("text");
            sequence = ((Number) properties.get("sequence")).longValue();
        }
    }
}
