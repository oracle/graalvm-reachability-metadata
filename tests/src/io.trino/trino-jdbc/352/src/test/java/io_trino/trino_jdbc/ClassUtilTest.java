/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.jackson.core.JsonParser;
import io.trino.jdbc.$internal.jackson.databind.DeserializationContext;
import io.trino.jdbc.$internal.jackson.databind.JsonDeserializer;
import io.trino.jdbc.$internal.jackson.databind.ObjectMapper;
import io.trino.jdbc.$internal.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClassUtilTest {
    @Test
    void instantiatesAnnotationSelectedDeserializer() throws Exception {
        Label label = new ObjectMapper().readValue("\"code:delta\"", Label.class);
        assertEquals("delta", label.text);
    }

    @JsonDeserialize(using = LabelDeserializer.class)
    public static class Label {
        public final String text;

        public Label(String text) {
            this.text = text;
        }
    }

    public static class LabelDeserializer extends JsonDeserializer<Label> {
        @Override
        public Label deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            return new Label(parser.getText().substring("code:".length()));
        }
    }
}
