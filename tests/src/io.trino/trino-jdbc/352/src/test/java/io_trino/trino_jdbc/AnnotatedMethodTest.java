/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.jackson.annotation.JsonCreator;
import io.trino.jdbc.$internal.jackson.annotation.JsonProperty;
import io.trino.jdbc.$internal.jackson.annotation.JsonValue;
import io.trino.jdbc.$internal.jackson.databind.ObjectMapper;
import java.util.Locale;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AnnotatedMethodTest {
    @Test
    void invokesFactoriesAndJsonValueAccessor() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Label label = mapper.readValue("\"north\"", Label.class);
        assertEquals("NORTH", label.value());
        assertEquals("\"NORTH\"", mapper.writeValueAsString(label));
        Pair pair = mapper.readValue("{\"left\":\"a\",\"right\":\"b\"}", Pair.class);
        assertEquals("a:b", pair.joined);
    }

    public static class Label {
        private final String text;

        private Label(String text) {
            this.text = text;
        }

        @JsonCreator
        public static Label of(String text) {
            return new Label(text.toUpperCase(Locale.ROOT));
        }

        @JsonValue
        public String value() {
            return text;
        }
    }

    public static class Pair {
        public final String joined;

        private Pair(String joined) {
            this.joined = joined;
        }

        @JsonCreator
        public static Pair of(@JsonProperty("left") String left, @JsonProperty("right") String right) {
            return new Pair(left + ":" + right);
        }
    }
}
