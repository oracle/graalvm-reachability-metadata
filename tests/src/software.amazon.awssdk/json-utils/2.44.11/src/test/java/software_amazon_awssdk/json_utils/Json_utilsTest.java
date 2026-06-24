/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.json_utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.protocols.jsoncore.JsonNode;
import software.amazon.awssdk.protocols.jsoncore.JsonNodeParser;
import software.amazon.awssdk.protocols.jsoncore.JsonNodeVisitor;
import software.amazon.awssdk.protocols.jsoncore.JsonValueNodeFactory;
import software.amazon.awssdk.protocols.jsoncore.JsonWriter;
import software.amazon.awssdk.thirdparty.jackson.core.JsonFactory;
import software.amazon.awssdk.thirdparty.jackson.core.JsonParseException;
import software.amazon.awssdk.thirdparty.jackson.core.JsonToken;
import software.amazon.awssdk.thirdparty.jackson.core.json.JsonReadFeature;

public class Json_utilsTest {
    private static final String DOCUMENT = """
            {
              // The default parser factory accepts Java-style comments.
              "name": "example",
              "enabled": true,
              "count": 3,
              "missing": null,
              "items": ["first", false, 4.5, {"nested": "value"}]
            }
            """;

    @Test
    void parserReadsObjectsArraysAndScalarValuesFromStrings() {
        JsonNode root = JsonNode.parser().parse(DOCUMENT);

        assertThat(root.isObject()).isTrue();
        assertThat(root.text()).isNull();
        assertThat(root.asObject().keySet())
                .containsExactly("name", "enabled", "count", "missing", "items");
        assertThat(root.index(0)).isEmpty();

        JsonNode name = requiredField(root, "name");
        assertThat(name.isString()).isTrue();
        assertThat(name.asString()).isEqualTo("example");
        assertThat(name.text()).isEqualTo("example");
        assertThat(name.field("irrelevant")).isEmpty();

        JsonNode enabled = requiredField(root, "enabled");
        assertThat(enabled.isBoolean()).isTrue();
        assertThat(enabled.asBoolean()).isTrue();
        assertThat(enabled.text()).isEqualTo("true");

        JsonNode count = requiredField(root, "count");
        assertThat(count.isNumber()).isTrue();
        assertThat(count.asNumber()).isEqualTo("3");
        assertThat(count.text()).isEqualTo("3");

        JsonNode missing = requiredField(root, "missing");
        assertThat(missing.isNull()).isTrue();
        assertThat(missing.text()).isNull();

        JsonNode items = requiredField(root, "items");
        assertThat(items.isArray()).isTrue();
        assertThat(items.asArray()).hasSize(4);
        assertThat(items.field("irrelevant")).isEmpty();
        assertThat(items.index(-1)).isEmpty();
        assertThat(items.index(4)).isEmpty();
        assertThat(requiredIndex(items, 0).asString()).isEqualTo("first");
        assertThat(requiredIndex(items, 1).asBoolean()).isFalse();
        assertThat(requiredIndex(items, 2).asNumber()).isEqualTo("4.5");
        assertThat(requiredField(requiredIndex(items, 3), "nested").asString()).isEqualTo("value");
    }

    @Test
    void parserAndWriterHandleTopLevelScalarDocuments() {
        JsonNode parsedString = JsonNode.parser().parse("\"standalone\"");
        JsonNode parsedBoolean = JsonNode.parser().parse("false");
        JsonNode parsedNull = JsonNode.parser().parse("null");
        JsonWriter numericWriter = JsonWriter.create().writeNumber("-12.75");
        JsonNode parsedNumber = JsonNode.parser().parse(numericWriter.getBytes());

        assertThat(parsedString.isString()).isTrue();
        assertThat(parsedString.asString()).isEqualTo("standalone");
        assertThat(parsedString.field("anything")).isEmpty();
        assertThat(parsedString.index(0)).isEmpty();
        assertThat(parsedBoolean.isBoolean()).isTrue();
        assertThat(parsedBoolean.asBoolean()).isFalse();
        assertThat(parsedNull.isNull()).isTrue();
        assertThat(parsedNull.text()).isNull();
        assertThat(parsedNumber.isNumber()).isTrue();
        assertThat(parsedNumber.asNumber()).isEqualTo("-12.75");
    }

    @Test
    void parserReadsEquivalentDocumentsFromBytesAndInputStreams() {
        byte[] bytes = DOCUMENT.getBytes(StandardCharsets.UTF_8);
        JsonNode fromBytes = JsonNode.parser().parse(bytes);
        JsonNode fromStream = JsonNode.parser().parse(new ByteArrayInputStream(bytes));

        assertThat(requiredField(fromBytes, "name").asString()).isEqualTo("example");
        assertThat(requiredField(fromStream, "items").asArray()).hasSize(4);
        assertThat(fromBytes).isEqualTo(fromStream);
        assertThat(fromBytes.hashCode()).isEqualTo(fromStream.hashCode());
        assertThat(JsonNode.emptyObjectNode()).isEqualTo(JsonNode.parser().parse("{}"));
    }

    @Test
    void parserDoesNotCloseCallerProvidedInputStreams() throws IOException {
        CloseTrackingInputStream inputStream = new CloseTrackingInputStream("{\"status\":\"open\"}"
                .getBytes(StandardCharsets.UTF_8));

        try {
            JsonNode parsed = JsonNodeParser.create().parse(inputStream);

            assertThat(requiredField(parsed, "status").asString()).isEqualTo("open");
            assertThat(inputStream.isClosed()).isFalse();
        } finally {
            inputStream.close();
        }
        assertThat(inputStream.isClosed()).isTrue();
    }

    @Test
    void visitorsReceiveTheConcreteJsonNodeKindAndValue() {
        JsonNode root = JsonNode.parser().parse(DOCUMENT);
        JsonNode items = requiredField(root, "items");
        JsonNode nested = requiredIndex(items, 3);
        JsonTypeDescribingVisitor visitor = new JsonTypeDescribingVisitor();

        assertThat(requiredField(root, "name").visit(visitor)).isEqualTo("string:example");
        assertThat(requiredField(root, "enabled").visit(visitor)).isEqualTo("boolean:true");
        assertThat(requiredField(root, "count").visit(visitor)).isEqualTo("number:3");
        assertThat(requiredField(root, "missing").visit(visitor)).isEqualTo("null");
        assertThat(items.visit(visitor)).isEqualTo("array:4");
        assertThat(nested.visit(visitor)).isEqualTo("object:1");
    }

    @Test
    void writerSerializesAllSupportedJsonValueTypes() {
        ByteBuffer binary = ByteBuffer.wrap(new byte[] {1, 2, 3, 4});
        JsonWriter writer = JsonWriter.builder()
                .jsonFactory(JsonFactory.builder().build())
                .build();

        writer.writeStartObject()
                .writeFieldName("string").writeValue("hello \"json\"")
                .writeFieldName("boolean").writeValue(true)
                .writeFieldName("long").writeValue(9_007_199_254_740_993L)
                .writeFieldName("double").writeValue(12.5D)
                .writeFieldName("float").writeValue(2.25F)
                .writeFieldName("short").writeValue((short) 7)
                .writeFieldName("int").writeValue(42)
                .writeFieldName("bytes").writeValue(binary)
                .writeFieldName("instant").writeValue(Instant.parse("2020-01-02T03:04:05.678Z"))
                .writeFieldName("decimal").writeValue(new BigDecimal("12345.6789"))
                .writeFieldName("integer").writeValue(new BigInteger("12345678901234567890"))
                .writeFieldName("rawNumber").writeNumber("6.022E23")
                .writeFieldName("nullValue").writeNull()
                .writeEndObject();

        JsonNode parsed = JsonNode.parser().parse(writer.getBytes());
        assertThat(binary.position()).isZero();
        assertThat(requiredField(parsed, "string").asString()).isEqualTo("hello \"json\"");
        assertThat(requiredField(parsed, "boolean").asBoolean()).isTrue();
        assertThat(requiredField(parsed, "long").asNumber()).isEqualTo("9007199254740993");
        assertThat(requiredField(parsed, "double").asNumber()).isEqualTo("12.5");
        assertThat(requiredField(parsed, "float").asNumber()).isEqualTo("2.25");
        assertThat(requiredField(parsed, "short").asNumber()).isEqualTo("7");
        assertThat(requiredField(parsed, "int").asNumber()).isEqualTo("42");
        assertThat(requiredField(parsed, "bytes").asString()).isEqualTo("AQIDBA==");
        assertThat(requiredField(parsed, "instant").asNumber()).isEqualTo("1577934245.678");
        assertThat(requiredField(parsed, "decimal").asString()).isEqualTo("12345.6789");
        assertThat(requiredField(parsed, "integer").asNumber()).isEqualTo("12345678901234567890");
        assertThat(requiredField(parsed, "rawNumber").asNumber()).isEqualTo("6.022E23");
        assertThat(requiredField(parsed, "nullValue").isNull()).isTrue();
    }

    @Test
    void writerSupportsNestedArraysAndCustomGeneratorFactories() {
        AtomicBoolean factoryInvoked = new AtomicBoolean();
        JsonWriter writer = JsonWriter.builder()
                .jsonGeneratorFactory(outputStream -> {
                    factoryInvoked.set(true);
                    return JsonNodeParser.DEFAULT_JSON_FACTORY.createGenerator(outputStream);
                })
                .build();

        writer.writeStartArray()
                .writeValue("alpha")
                .writeStartObject()
                .writeFieldName("numbers")
                .writeStartArray()
                .writeValue(1)
                .writeValue(2)
                .writeEndArray()
                .writeEndObject()
                .writeNull()
                .writeEndArray();

        JsonNode array = JsonNode.parser().parse(writer.getBytes());
        assertThat(factoryInvoked.get()).isTrue();
        assertThat(array.isArray()).isTrue();
        assertThat(requiredIndex(array, 0).asString()).isEqualTo("alpha");
        assertThat(requiredIndex(requiredField(requiredIndex(array, 1), "numbers"), 0).asNumber()).isEqualTo("1");
        assertThat(requiredIndex(requiredField(requiredIndex(array, 1), "numbers"), 1).asNumber()).isEqualTo("2");
        assertThat(requiredIndex(array, 2).isNull()).isTrue();
    }

    @Test
    void parserBuilderSupportsCustomJsonFactoriesAndValueNodeFactories() {
        JsonFactory commentAwareFactory = JsonFactory.builder()
                .configure(JsonReadFeature.ALLOW_JAVA_COMMENTS, true)
                .build();
        JsonNodeParser parser = JsonNode.parserBuilder()
                .jsonFactory(commentAwareFactory)
                .jsonValueNodeFactory((jsonParser, token) -> {
                    if (token == JsonToken.VALUE_TRUE) {
                        return JsonNode.parser().parse("\"custom-true\"");
                    }
                    return JsonValueNodeFactory.DEFAULT.node(jsonParser, token);
                })
                .build();

        JsonNode parsed = parser.parse("{" +
                "\"flag\": true," +
                "\"number\": 12," +
                "\"text\": /* accepted by the configured factory */ \"value\"" +
                "}");

        assertThat(requiredField(parsed, "flag").isString()).isTrue();
        assertThat(requiredField(parsed, "flag").asString()).isEqualTo("custom-true");
        assertThat(requiredField(parsed, "number").asNumber()).isEqualTo("12");
        assertThat(requiredField(parsed, "text").asString()).isEqualTo("value");
    }

    @Test
    void parserCanRemoveLocationsFromMalformedJsonErrors() {
        JsonNodeParser parser = JsonNodeParser.builder()
                .removeErrorLocations(true)
                .build();

        assertThatExceptionOfType(UncheckedIOException.class)
                .isThrownBy(() -> parser.parse("{\"unterminated\": [1,"))
                .satisfies(exception -> {
                    assertThat(exception.getCause()).isInstanceOf(JsonParseException.class);
                    JsonParseException cause = (JsonParseException) exception.getCause();
                    assertThat(cause.getLocation()).isNull();
                });
    }

    private static JsonNode requiredField(JsonNode node, String fieldName) {
        return node.field(fieldName).orElseThrow(() -> new AssertionError("Missing JSON field " + fieldName));
    }

    private static JsonNode requiredIndex(JsonNode node, int index) {
        return node.index(index).orElseThrow(() -> new AssertionError("Missing JSON index " + index));
    }

    private static final class CloseTrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private CloseTrackingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }

        private boolean isClosed() {
            return closed;
        }
    }

    private static final class JsonTypeDescribingVisitor implements JsonNodeVisitor<String> {
        @Override
        public String visitNull() {
            return "null";
        }

        @Override
        public String visitBoolean(boolean value) {
            return "boolean:" + value;
        }

        @Override
        public String visitNumber(String value) {
            return "number:" + value;
        }

        @Override
        public String visitString(String value) {
            return "string:" + value;
        }

        @Override
        public String visitArray(List<JsonNode> value) {
            return "array:" + value.size();
        }

        @Override
        public String visitObject(Map<String, JsonNode> value) {
            return "object:" + value.size();
        }

        @Override
        public String visitEmbeddedObject(Object value) {
            return "embedded:" + value;
        }
    }
}
