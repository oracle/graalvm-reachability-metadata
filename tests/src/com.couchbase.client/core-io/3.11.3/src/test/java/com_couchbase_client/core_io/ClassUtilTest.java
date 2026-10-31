/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.couchbase.client.core.deps.com.fasterxml.jackson.core.JsonParser;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.DeserializationContext;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.DeserializationFeature;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.ObjectMapper;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.util.Locale;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClassUtilTest {
    @Test
    void mapsApplicationData() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Code result = mapper.readValue("\"  ab-12  \"", Code.class);
        assertEquals("AB-12", result.value);
        mapper.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE);
        assertEquals(State.OTHER, mapper.readValue("\"unrecognized\"", State.class));
    }

    @JsonDeserialize(using = CodeDeserializer.class)
    public static class Code {
        public final String value;

        public Code(String value) {
            this.value = value;
        }
    }

    public static class CodeDeserializer extends StdDeserializer<Code> {
        public CodeDeserializer() {
            super(Code.class);
        }

        @Override
        public Code deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            return new Code(parser.getValueAsString().trim().toUpperCase(Locale.ROOT));
        }
    }

    public enum State {
        READY,
        @JsonEnumDefaultValue OTHER
    }
}
