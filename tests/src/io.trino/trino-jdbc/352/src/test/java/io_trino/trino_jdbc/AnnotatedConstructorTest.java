/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.jackson.annotation.JsonCreator;
import io.trino.jdbc.$internal.jackson.annotation.JsonProperty;
import io.trino.jdbc.$internal.jackson.databind.ObjectMapper;
import io.trino.jdbc.$internal.jackson.databind.introspect.AnnotatedConstructor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AnnotatedConstructorTest {
    @Test
    void usesPropertyAndDelegatingConstructors() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Point point = mapper.readValue("{\"x\":3,\"y\":8}", Point.class);
        assertEquals(3, point.x);
        assertEquals(8, point.y);
        assertEquals("token", mapper.readValue("\"token\"", Token.class).value);
        assertEquals("ready", mapper.readValue("{}", Defaults.class).state);
    }

    @Test
    void restoresIntrospectedCreatorAndConstructsNewValues() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AnnotatedConstructor creator = mapper.getDeserializationConfig()
                .introspect(mapper.constructType(Token.class)).getConstructors().get(0);
        AnnotatedConstructor restored = SerializationRoundTrip.copy(creator);

        assertEquals(Token.class, restored.getDeclaringClass());
        assertEquals(1, restored.getParameterCount());
        assertEquals(String.class, restored.getRawParameterType(0));
        assertEquals("restored", ((Token) restored.call1("restored")).value);
        assertEquals("another", ((Token) restored.call1("another")).value);
    }

    public static class Point {
        public final int x;
        public final int y;

        @JsonCreator
        public Point(@JsonProperty("x") int x, @JsonProperty("y") int y) {
            this.x = x;
            this.y = y;
        }
    }

    public static class Token {
        public final String value;

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public Token(String value) {
            this.value = value;
        }
    }

    public static class Defaults {
        public String state = "ready";
    }
}
