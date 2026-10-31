/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.jackson.annotation.JsonCreator;
import io.trino.jdbc.$internal.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AnnotatedCreatorCollectorTest {
    @Test
    void usesFactoryAnnotationFromMixIn() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(Code.class, CodeMixIn.class);
        assertEquals("X-17", mapper.readValue("\"17\"", Code.class).text);
    }

    public static class Code {
        public final String text;

        private Code(String text) {
            this.text = text;
        }

        public static Code parse(String text) {
            return new Code("X-" + text);
        }
    }

    public abstract static class CodeMixIn {
        @JsonCreator
        public static Code parse(String text) {
            return Code.parse(text);
        }
    }
}
