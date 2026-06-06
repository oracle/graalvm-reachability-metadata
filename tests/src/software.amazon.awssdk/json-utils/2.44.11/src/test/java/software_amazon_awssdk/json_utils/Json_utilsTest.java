/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.json_utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.protocols.jsoncore.JsonNode;
import software.amazon.awssdk.protocols.jsoncore.JsonNodeParser;
import software.amazon.awssdk.protocols.jsoncore.JsonNodeVisitor;
import software.amazon.awssdk.protocols.jsoncore.JsonValueNodeFactory;
import software.amazon.awssdk.protocols.jsoncore.JsonWriter;
import software.amazon.awssdk.thirdparty.jackson.core.JsonFactory;
import software.amazon.awssdk.thirdparty.jackson.core.JsonGenerator;
import software.amazon.awssdk.thirdparty.jackson.core.JsonParseException;
import software.amazon.awssdk.thirdparty.jackson.core.JsonParser;
import software.amazon.awssdk.thirdparty.jackson.core.JsonToken;
import software.amazon.awssdk.thirdparty.jackson.core.json.JsonReadFeature;
import software.amazon.awssdk.thirdparty.jackson.core.json.JsonWriteFeature;

public class Json_utilsTest {
    @Test
    void parsesJsonDocumentAndTraversesNodes() {
        String json = """
            {
              // Java-style comments are accepted by the default parser.
              "message": "hello \\\"json\\\"",
              "count": 12.50,
              "enabled": false,
              "missing": null,
              "items": [true, {"nested": "value"}, -3],
              "empty": {}
            }
            """;

        JsonNode root = JsonNode.parser().parse(json);

        assertThat(root.isObject()).isTrue();
        assertThat(root.text()).isNull();
        assertThat(root.asObject()).containsOnlyKeys("message", "count", "enabled", "missing", "items", "empty");
        assertThat(root.field("absent")).isEmpty();

        JsonNode message = required(root.field("message"));
        assertThat(message.isString()).isTrue();
        assertThat(message.asString()).isEqualTo("hello \"json\"");
        assertThat(message.text()).isEqualTo("hello \"json\"");

        JsonNode count = required(root.field("count"));
        assertThat(count.isNumber()).isTrue();
        assertThat(count.asNumber()).isEqualTo("12.50");
        assertThat(count.text()).isEqualTo("12.50");

        JsonNode enabled = required(root.field("enabled"));
        assertThat(enabled.isBoolean()).isTrue();
        assertThat(enabled.asBoolean()).isFalse();
        assertThat(enabled.text()).isEqualTo("false");

        JsonNode missing = required(root.field("missing"));
        assertThat(missing.isNull()).isTrue();
        assertThat(missing.text()).isNull();

        JsonNode items = required(root.field("items"));
        assertThat(items.isArray()).isTrue();
        assertThat(items.asArray()).hasSize(3);
        assertThat(items.index(-1)).isEmpty();
        assertThat(items.index(3)).isEmpty();
        assertThat(required(items.index(0)).asBoolean()).isTrue();
        assertThat(required(required(items.index(1)).field("nested")).asString()).isEqualTo("value");
        assertThat(required(items.index(2)).asNumber()).isEqualTo("-3");

        assertThat(required(root.field("empty")).asObject()).isEmpty();
        assertThat(JsonNode.emptyObjectNode()).isEqualTo(required(root.field("empty")));
    }

    @Test
    void visitsEveryStandardJsonNodeType() {
        JsonNode root = JsonNode.parser().parse("[null, true, 123, \"abc\", [], {\"k\": \"v\"}]");
        List<JsonNode> nodes = root.asArray();
        DescribingVisitor visitor = new DescribingVisitor();

        assertThat(nodes.get(0).visit(visitor)).isEqualTo("null");
        assertThat(nodes.get(1).visit(visitor)).isEqualTo("boolean:true");
        assertThat(nodes.get(2).visit(visitor)).isEqualTo("number:123");
        assertThat(nodes.get(3).visit(visitor)).isEqualTo("string:abc");
        assertThat(nodes.get(4).visit(visitor)).isEqualTo("array:0");
        assertThat(nodes.get(5).visit(visitor)).isEqualTo("object:k");
    }

    @Test
    void rejectsUnsupportedConversionsWithClearStandardExceptions() {
        JsonNode object = JsonNode.parser().parse("{\"value\": [1]}");
        JsonNode array = required(object.field("value"));
        JsonNode number = required(array.index(0));

        assertThatThrownBy(object::asString)
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("object")
            .hasMessageContaining("string");
        assertThatThrownBy(array::asObject)
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("array")
            .hasMessageContaining("object");
        assertThatThrownBy(number::asBoolean)
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("number")
            .hasMessageContaining("boolean");
    }

    @Test
    void nodeToStringProducesParseableJsonRepresentation() {
        JsonNode source = JsonNode.parser().parse("""
            {
              "quote": "a\\\"b",
              "slash": "c\\\\d",
              "numbers": [1, 2],
              "flag": true,
              "missing": null
            }
            """);

        String rendered = source.toString();
        JsonNode reparsed = JsonNode.parser().parse(rendered);

        assertThat(rendered)
            .contains("\"quote\": \"a\\\"b\"")
            .contains("\"slash\": \"c\\\\d\"")
            .contains("\"numbers\": [1, 2]")
            .contains("\"flag\": true")
            .contains("\"missing\": null");
        assertThat(required(reparsed.field("quote")).asString()).isEqualTo("a\"b");
        assertThat(required(reparsed.field("slash")).asString()).isEqualTo("c\\d");
        assertThat(required(required(reparsed.field("numbers")).index(0)).asNumber()).isEqualTo("1");
        assertThat(required(required(reparsed.field("numbers")).index(1)).asNumber()).isEqualTo("2");
        assertThat(required(reparsed.field("flag")).asBoolean()).isTrue();
        assertThat(required(reparsed.field("missing")).isNull()).isTrue();
    }

    @Test
    void writesJsonValuesAndParsesGeneratedBytes() {
        JsonWriter writer = JsonWriter.create()
            .writeStartObject()
            .writeFieldName("string").writeValue("value")
            .writeFieldName("boolean").writeValue(true)
            .writeFieldName("long").writeValue(1234567890123L)
            .writeFieldName("double").writeValue(1.25d)
            .writeFieldName("float").writeValue(2.5f)
            .writeFieldName("short").writeValue((short) 7)
            .writeFieldName("int").writeValue(42)
            .writeFieldName("binary").writeValue(ByteBuffer.wrap("hello".getBytes(StandardCharsets.UTF_8)))
            .writeFieldName("instant").writeValue(Instant.ofEpochSecond(1_700_000_000L))
            .writeFieldName("decimal").writeValue(new BigDecimal("123.4500"))
            .writeFieldName("integer").writeValue(new BigInteger("9876543210123456789"))
            .writeFieldName("numberString").writeNumber("6.02e23")
            .writeFieldName("nullValue").writeNull()
            .writeFieldName("array").writeStartArray().writeValue("first").writeValue(2).writeEndArray()
            .writeEndObject();

        JsonNode generated = JsonNode.parser().parse(writer.getBytes());

        assertThat(required(generated.field("string")).asString()).isEqualTo("value");
        assertThat(required(generated.field("boolean")).asBoolean()).isTrue();
        assertThat(required(generated.field("long")).asNumber()).isEqualTo("1234567890123");
        assertThat(required(generated.field("double")).asNumber()).isEqualTo("1.25");
        assertThat(required(generated.field("float")).asNumber()).isEqualTo("2.5");
        assertThat(required(generated.field("short")).asNumber()).isEqualTo("7");
        assertThat(required(generated.field("int")).asNumber()).isEqualTo("42");
        assertThat(required(generated.field("binary")).asString()).isEqualTo("aGVsbG8=");
        assertThat(required(generated.field("instant")).asNumber()).isEqualTo("1700000000.000");
        assertThat(required(generated.field("decimal")).asString()).isEqualTo("123.4500");
        assertThat(required(generated.field("integer")).asNumber()).isEqualTo("9876543210123456789");
        assertThat(required(generated.field("numberString")).asNumber()).isEqualTo("6.02e23");
        assertThat(required(generated.field("nullValue")).isNull()).isTrue();
        assertThat(required(required(generated.field("array")).index(0)).asString()).isEqualTo("first");
        assertThat(required(required(generated.field("array")).index(1)).asNumber()).isEqualTo("2");
    }

    @Test
    void parserBuilderSupportsCustomFactoriesAndDoesNotCloseInputStream() {
        TrackingInputStream input = new TrackingInputStream("{\"answer\": 42}".getBytes(StandardCharsets.UTF_8));
        JsonNode parsed = JsonNode.parserBuilder()
            .jsonValueNodeFactory(prefixingValueFactory())
            .build()
            .parse(input);

        assertThat(input.closed()).isFalse();
        assertThat(required(parsed.field("answer")).text()).isEqualTo("VALUE_NUMBER_INT:42");

        JsonFactory strictFactory = JsonFactory.builder()
            .configure(JsonReadFeature.ALLOW_JAVA_COMMENTS, false)
            .build();
        JsonNodeParser strictParser = JsonNode.parserBuilder().jsonFactory(strictFactory).build();

        assertThrows(UncheckedIOException.class, () -> strictParser.parse("{/* comment */\"a\":1}"));
    }

    @Test
    void parserCanRemoveLocationsFromParsingErrors() {
        JsonNodeParser parser = JsonNode.parserBuilder().removeErrorLocations(true).build();

        UncheckedIOException exception = assertThrows(UncheckedIOException.class, () -> parser.parse("{\"secret\":"));

        assertThat(exception.getCause()).isInstanceOf(JsonParseException.class);
        JsonParseException parseException = (JsonParseException) exception.getCause();
        assertThat(parseException.getLocation()).isNull();
    }

    @Test
    void writerBuilderAcceptsJsonFactoryConfiguration() {
        JsonFactory escapingFactory = JsonFactory.builder()
            .configure(JsonWriteFeature.ESCAPE_NON_ASCII, true)
            .build();

        JsonWriter writer = JsonWriter.builder()
            .jsonFactory(escapingFactory)
            .build()
            .writeStartObject()
            .writeFieldName("word").writeValue("caf\u00e9")
            .writeEndObject();

        String json = new String(writer.getBytes(), StandardCharsets.UTF_8);
        JsonNode parsed = JsonNode.parser().parse(json);

        assertThat(json).contains("\\u");
        assertThat(json).doesNotContain("caf\u00e9");
        assertThat(required(parsed.field("word")).asString()).isEqualTo("caf\u00e9");
    }

    @Test
    void writerBuilderSupportsCustomGeneratorFactory() {
        JsonWriter writer = JsonWriter.builder()
            .jsonGeneratorFactory(Json_utilsTest::prettyPrintingGenerator)
            .build()
            .writeStartObject()
            .writeFieldName("name").writeValue("json-utils")
            .writeFieldName("values").writeStartArray().writeValue(1).writeValue(2).writeEndArray()
            .writeEndObject();

        String json = new String(writer.getBytes(), StandardCharsets.UTF_8);
        JsonNode parsed = JsonNode.parser().parse(json);

        assertThat(json).contains(System.lineSeparator());
        assertThat(required(parsed.field("name")).asString()).isEqualTo("json-utils");
        assertThat(required(required(parsed.field("values")).index(0)).asNumber()).isEqualTo("1");
        assertThat(required(required(parsed.field("values")).index(1)).asNumber()).isEqualTo("2");
    }

    private static JsonGenerator prettyPrintingGenerator(OutputStream outputStream) throws IOException {
        JsonGenerator generator = JsonNodeParser.DEFAULT_JSON_FACTORY.createGenerator(outputStream);
        generator.useDefaultPrettyPrinter();
        return generator;
    }

    private static JsonValueNodeFactory prefixingValueFactory() {
        return (JsonParser parser, JsonToken token) -> new PrefixValueNode(token + ":" + parser.getText());
    }

    private static JsonNode required(Optional<JsonNode> node) {
        assertThat(node).isPresent();
        return node.get();
    }

    private static final class DescribingVisitor implements JsonNodeVisitor<String> {
        @Override
        public String visitNull() {
            return "null";
        }

        @Override
        public String visitBoolean(boolean bool) {
            return "boolean:" + bool;
        }

        @Override
        public String visitNumber(String number) {
            return "number:" + number;
        }

        @Override
        public String visitString(String string) {
            return "string:" + string;
        }

        @Override
        public String visitArray(List<JsonNode> array) {
            return "array:" + array.size();
        }

        @Override
        public String visitObject(Map<String, JsonNode> object) {
            return "object:" + String.join(",", object.keySet());
        }

        @Override
        public String visitEmbeddedObject(Object embeddedObject) {
            return "embedded:" + embeddedObject;
        }
    }

    private static final class PrefixValueNode implements JsonNode {
        private final String value;

        private PrefixValueNode(String value) {
            this.value = value;
        }

        @Override
        public boolean isString() {
            return true;
        }

        @Override
        public String asNumber() {
            throw new UnsupportedOperationException("Custom value is not a JSON number.");
        }

        @Override
        public String asString() {
            return value;
        }

        @Override
        public boolean asBoolean() {
            throw new UnsupportedOperationException("Custom value is not a JSON boolean.");
        }

        @Override
        public List<JsonNode> asArray() {
            throw new UnsupportedOperationException("Custom value is not a JSON array.");
        }

        @Override
        public Map<String, JsonNode> asObject() {
            throw new UnsupportedOperationException("Custom value is not a JSON object.");
        }

        @Override
        public Object asEmbeddedObject() {
            throw new UnsupportedOperationException("Custom value is not an embedded object.");
        }

        @Override
        public <T> T visit(JsonNodeVisitor<T> visitor) {
            return visitor.visitString(value);
        }

        @Override
        public String text() {
            return value;
        }
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private TrackingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }

        private boolean closed() {
            return closed;
        }
    }
}
