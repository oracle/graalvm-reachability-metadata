/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_json;

import com.azure.json.JsonProviders;
import com.azure.json.JsonReader;
import com.azure.json.JsonToken;
import com.azure.json.JsonWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsonProvidersTest {
    @Test
    void writesAndReadsObjectThroughDefaultProvider() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (JsonWriter writer = JsonProviders.createWriter(output)) {
            writer.writeStartObject();
            writer.writeStringField("message", "hello");
            writer.writeIntField("count", 2);
            writer.writeEndObject();
        }

        assertEquals("{\"message\":\"hello\",\"count\":2}", output.toString(StandardCharsets.UTF_8));

        try (JsonReader reader = JsonProviders.createReader(output.toByteArray())) {
            assertEquals(JsonToken.START_OBJECT, reader.nextToken());
            assertEquals(JsonToken.FIELD_NAME, reader.nextToken());
            assertEquals("message", reader.getFieldName());
            assertEquals(JsonToken.STRING, reader.nextToken());
            assertEquals("hello", reader.getString());
            assertEquals(JsonToken.FIELD_NAME, reader.nextToken());
            assertEquals("count", reader.getFieldName());
            assertEquals(JsonToken.NUMBER, reader.nextToken());
            assertEquals(2, reader.getInt());
            assertEquals(JsonToken.END_OBJECT, reader.nextToken());
            assertEquals(JsonToken.END_DOCUMENT, reader.nextToken());
        }
    }
}
