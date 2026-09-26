/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_json;

import com.azure.json.JsonOptions;
import com.azure.json.JsonProviders;
import com.azure.json.JsonReader;
import com.azure.json.JsonSerializable;
import com.azure.json.JsonToken;
import com.azure.json.JsonWriter;
import com.azure.json.models.JsonArray;
import com.azure.json.models.JsonNull;
import com.azure.json.models.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AzureJsonTest {
    @Test
    void roundTripsSerializableModelThroughStreams() throws IOException {
        Message original = new Message("created", 3, new byte[] { 1, 2, 3, 4 }, Arrays.asList("azure", "json"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        original.toJson(output);
        Message restored;
        try (JsonReader reader = JsonProviders.createReader(new ByteArrayInputStream(output.toByteArray()))) {
            restored = Message.fromJson(reader);
        }

        assertEquals("created", restored.name);
        assertEquals(3, restored.count);
        assertArrayEquals(new byte[] { 1, 2, 3, 4 }, restored.payload);
        assertEquals(Arrays.asList("azure", "json"), restored.labels);
        assertEquals(original.toJsonString(), new String(original.toJsonBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void writesAndReadsNestedUntypedValues() throws IOException {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("enabled", true);
        source.put("values", Arrays.asList(1, 2L, 3.5D, null));
        source.put("details", Map.of("name", "sample", "count", 4));

        StringWriter output = new StringWriter();
        try (JsonWriter writer = JsonProviders.createWriter(output)) {
            writer.writeUntyped(source).flush();
        }

        Object restored;
        try (JsonReader reader = JsonProviders.createReader(new StringReader(output.toString()))) {
            restored = reader.readUntyped();
        }

        Map<?, ?> restoredObject = (Map<?, ?>) restored;
        assertEquals(true, restoredObject.get("enabled"));
        assertEquals(Arrays.asList(1, 2, 3.5D, null), restoredObject.get("values"));
        assertEquals(Map.of("name", "sample", "count", 4), restoredObject.get("details"));
    }

    @Test
    void buffersAndReplaysAnObject() throws IOException {
        String json = "{\"first\":1,\"nested\":{\"ignored\":[1,2]},\"last\":\"done\"}";

        try (JsonReader reader = JsonProviders.createReader(json)) {
            assertEquals(JsonToken.START_OBJECT, reader.nextToken());
            try (JsonReader buffered = reader.bufferObject()) {
                assertTrue(buffered.isResetSupported());
                assertEquals(JsonToken.START_OBJECT, buffered.nextToken());
                assertEquals(json, buffered.readChildren());

                try (JsonReader replay = buffered.reset()) {
                    Map<String, Object> value = replay.readMap(JsonReader::readUntyped);
                    assertEquals(1, value.get("first"));
                    assertEquals("done", value.get("last"));
                    assertEquals(Map.of("ignored", Arrays.asList(1, 2)), value.get("nested"));
                }
            }
        }
    }

    @Test
    void roundTripsAndMutatesJsonElementTree() throws IOException {
        JsonArray values = new JsonArray().addElement("alpha").addElement(42).addElement(true)
            .addElement(JsonNull.getInstance());
        JsonObject source = new JsonObject().setProperty("name", "document").setProperty("values", values)
            .setProperty("missing", (String) null);

        JsonObject restored;
        try (JsonReader reader = JsonProviders.createReader(source.toJsonString())) {
            restored = JsonObject.fromJson(reader);
        }

        assertTrue(restored.isObject());
        assertEquals("document", restored.getProperty("name").asString().getValue());
        JsonArray restoredValues = restored.getProperty("values").asArray();
        assertEquals(4, restoredValues.size());
        assertEquals(42, restoredValues.getElement(1).asNumber().getValue());
        assertTrue(restoredValues.getElement(2).asBoolean().getValue());
        assertSame(JsonNull.getInstance(), restoredValues.getElement(3).asNull());
        assertTrue(restored.getProperty("missing").isNull());
        assertNull(restored.removeProperty("absent"));
        assertEquals(3, restored.size());
    }

    @Test
    void preservesRemainingObjectFieldsAsJson() throws IOException {
        String json = "{\"id\":\"record-7\",\"status\":\"active\","
            + "\"metadata\":{\"region\":\"west\",\"retries\":2},\"tags\":[\"primary\",\"archived\"]}";

        String remainingFields;
        try (JsonReader reader = JsonProviders.createReader(json)) {
            assertEquals(JsonToken.START_OBJECT, reader.nextToken());
            assertEquals(JsonToken.FIELD_NAME, reader.nextToken());
            assertEquals("id", reader.getFieldName());
            assertEquals(JsonToken.STRING, reader.nextToken());
            assertEquals("record-7", reader.getString());
            assertEquals(JsonToken.FIELD_NAME, reader.nextToken());
            assertEquals("status", reader.getFieldName());

            remainingFields = reader.readRemainingFieldsAsJsonObject();
        }

        try (JsonReader reader = JsonProviders.createReader(remainingFields)) {
            Map<String, Object> values = reader.readMap(JsonReader::readUntyped);
            assertEquals("active", values.get("status"));
            assertEquals(Map.of("region", "west", "retries", 2), values.get("metadata"));
            assertEquals(Arrays.asList("primary", "archived"), values.get("tags"));
        }
    }

    @Test
    void returnsJsonEscapedTextForStringTokens() throws IOException {
        String json = "{\"display\\nname\":\"line\\n\\\"quoted\\\"\"}";

        try (JsonReader reader = JsonProviders.createReader(json)) {
            assertEquals(JsonToken.START_OBJECT, reader.nextToken());
            assertEquals(JsonToken.FIELD_NAME, reader.nextToken());
            assertEquals("display\nname", reader.getText());
            assertEquals("display\\nname", reader.getRawText());

            assertEquals(JsonToken.STRING, reader.nextToken());
            assertEquals("line\n\"quoted\"", reader.getText());
            assertEquals("line\\n\\\"quoted\\\"", reader.getRawText());
            assertEquals(JsonToken.END_OBJECT, reader.nextToken());
        }
    }

    @Test
    void readsJsonWithConfiguredCommentsAndNonNumericNumbers() throws IOException {
        JsonOptions options = new JsonOptions().setJsoncSupported(true).setNonNumericNumbersSupported(true);
        String json = "/* configuration */ {\"limit\":NaN,\"active\":false}";

        try (JsonReader reader = JsonProviders.createReader(json, options)) {
            Map<String, Object> values = reader.readMap(JsonReader::readUntyped);
            assertTrue(Double.isNaN((Double) values.get("limit")));
            assertEquals(false, values.get("active"));
        }

        assertTrue(options.isJsoncSupported());
        assertTrue(options.isNonNumericNumbersSupported());
        assertFalse(new JsonOptions().isJsoncSupported());
    }

    private static final class Message implements JsonSerializable<Message> {
        private final String name;
        private final int count;
        private final byte[] payload;
        private final List<String> labels;

        private Message(String name, int count, byte[] payload, List<String> labels) {
            this.name = name;
            this.count = count;
            this.payload = payload;
            this.labels = labels;
        }

        @Override
        public JsonWriter toJson(JsonWriter writer) throws IOException {
            return writer.writeStartObject().writeStringField("name", name).writeIntField("count", count)
                .writeBinaryField("payload", payload).writeArrayField("labels", labels, JsonWriter::writeString)
                .writeEndObject();
        }

        private static Message fromJson(JsonReader reader) throws IOException {
            return reader.readObject(jsonReader -> {
                String name = null;
                int count = 0;
                byte[] payload = null;
                List<String> labels = null;

                while (jsonReader.nextToken() != JsonToken.END_OBJECT) {
                    String fieldName = jsonReader.getFieldName();
                    jsonReader.nextToken();
                    if ("name".equals(fieldName)) {
                        name = jsonReader.getString();
                    } else if ("count".equals(fieldName)) {
                        count = jsonReader.getInt();
                    } else if ("payload".equals(fieldName)) {
                        payload = jsonReader.getBinary();
                    } else if ("labels".equals(fieldName)) {
                        labels = jsonReader.readArray(JsonReader::getString);
                    } else {
                        jsonReader.skipChildren();
                    }
                }

                return new Message(name, count, payload, labels);
            });
        }
    }
}
