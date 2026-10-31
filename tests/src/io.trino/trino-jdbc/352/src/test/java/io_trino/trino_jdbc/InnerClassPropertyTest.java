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

public class InnerClassPropertyTest {
    @Test
    void constructsInnerValueUsingItsOwningBean() throws Exception {
        Envelope envelope = new ObjectMapper().readValue("{\"body\":{\"text\":\"payload\"}}", Envelope.class);
        assertEquals("payload", envelope.getBody().text);
        assertEquals("mail:payload", envelope.getBody().describe());
    }

    public static class Envelope {
        private Body content;

        public Body getBody() {
            return content;
        }

        public void setBody(Body body) {
            content = body;
        }

        public class Body {
            public String text;

            public String describe() {
                return "mail:" + text;
            }
        }
    }
}
