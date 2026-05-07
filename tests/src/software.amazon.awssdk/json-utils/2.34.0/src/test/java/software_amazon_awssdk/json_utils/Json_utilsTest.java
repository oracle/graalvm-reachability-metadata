/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.json_utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.protocols.jsoncore.JsonNode;
import software.amazon.awssdk.protocols.jsoncore.JsonNodeParser;
import software.amazon.awssdk.protocols.jsoncore.JsonNodeVisitor;
import software.amazon.awssdk.protocols.jsoncore.JsonValueNodeFactory;
import software.amazon.awssdk.protocols.jsoncore.JsonWriter;
import software.amazon.awssdk.thirdparty.jackson.core.JsonFactory;
import software.amazon.awssdk.thirdparty.jackson.core.JsonParser;
import software.amazon.awssdk.thirdparty.jackson.core.JsonToken;
import software.amazon.awssdk.thirdparty.jackson.core.json.JsonReadFeature;
import org.junit.jupiter.api.Test;

public class Json_utilsTest {
    @Test
    void parserBuildsNavigableTreeForObjectsArraysAndScalarValues() {
        String json = """
            {
              "name": "example",
              "count": 42,
              "enabled": true,
              "missing": null,
              "items": ["first", -3.50, false, {"nested": "value"}]
            }
            """;

        JsonNode root = JsonNode.parser().parse(json);

        assertThat(root.isObject()).isTrue();
        assertThat(root.text()).isNull();
        assertThat(root.asObject().keySet()).containsExactly("name", "count", "enabled", "missing", "items");
        assertThat(root.field("name")).hasValueSatisfying(node -> {
            assertThat(node.isString()).isTrue();
            assertThat(node.asString()).isEqualTo("example");
            assertThat(node.text()).isEqualTo("example");
        });
        assertThat(root.field("count")).hasValueSatisfying(node -> {
            assertThat(node.isNumber()).isTrue();
            assertThat(node.asNumber()).isEqualTo("42");
            assertThat(node.text()).isEqualTo("42");
        });
        assertThat(root.field("enabled")).hasValueSatisfying(node -> {
            assertThat(node.isBoolean()).isTrue();
            assertThat(node.asBoolean()).isTrue();
            assertThat(node.text()).isEqualTo("true");
        });
        assertThat(root.field("missing")).hasValueSatisfying(node -> assertThat(node.isNull()).isTrue());
        assertThat(root.field("absent")).isEmpty();

        JsonNode items = root.field("items").orElseThrow();
        assertThat(items.isArray()).isTrue();
        assertThat(items.asArray()).hasSize(4);
        assertThat(items.index(0)).map(JsonNode::asString).contains("first");
        assertThat(items.index(1)).map(JsonNode::asNumber).contains("-3.50");
        assertThat(items.index(2)).map(JsonNode::asBoolean).contains(false);
        assertThat(items.index(3).orElseThrow().field("nested")).map(JsonNode::asString).contains("value");
        assertThat(items.index(-1)).isEmpty();
        assertThat(items.index(4)).isEmpty();
    }

    @Test
    void parserSupportsStringByteArrayInputStreamEmptyDocumentAndEmptyObjectFactory() {
        JsonNode fromString = JsonNode.parser().parse("{\"array\":[1,2],\"text\":\"value\"}");
        JsonNode fromBytes = JsonNode.parser().parse(
            "{\"array\":[1,2],\"text\":\"value\"}".getBytes(StandardCharsets.UTF_8));
        JsonNode fromStream = JsonNode.parser().parse(new ByteArrayInputStream(
            "{\"array\":[1,2],\"text\":\"value\"}".getBytes(StandardCharsets.UTF_8)));

        assertThat(fromBytes).isEqualTo(fromString);
        assertThat(fromStream).isEqualTo(fromString);
        assertThat(fromStream.field("array").orElseThrow().index(1)).map(JsonNode::asNumber).contains("2");
        assertThat(JsonNode.parserBuilder().build().parse("")).isNull();
        assertThat(JsonNode.emptyObjectNode().isObject()).isTrue();
        assertThat(JsonNode.emptyObjectNode().asObject()).isEmpty();
    }

    @Test
    void defaultParserAllowsJavaCommentsAndCustomJsonFactoryCanRejectThem() {
        String jsonWithComment = "{/* supported by the default SDK factory */ \"answer\": 42}";
        assertThat(JsonNodeParser.create().parse(jsonWithComment).field("answer"))
            .map(JsonNode::asNumber)
            .contains("42");

        JsonFactory strictFactory = JsonFactory.builder()
            .configure(JsonReadFeature.ALLOW_JAVA_COMMENTS, false)
            .build();
        JsonNodeParser strictParser = JsonNodeParser.builder()
            .jsonFactory(strictFactory)
            .build();

        assertThatThrownBy(() -> strictParser.parse(jsonWithComment))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Unexpected character");
    }

    @Test
    void parserCanUseCustomValueNodeFactoryForEveryScalarToken() {
        JsonValueNodeFactory describingFactory = (JsonParser parser, JsonToken token) ->
            new ScalarDescriptionNode(token.name() + ":" + parser.getText());
        JsonNodeParser parser = JsonNodeParser.builder()
            .jsonValueNodeFactory(describingFactory)
            .build();

        JsonNode root = parser.parse("""
            {"string":"text","integer":7,"decimal":1.25,"yes":true,"no":false,"none":null}
            """);

        assertThat(root.field("string")).map(JsonNode::asString).contains("VALUE_STRING:text");
        assertThat(root.field("integer")).map(JsonNode::asString).contains("VALUE_NUMBER_INT:7");
        assertThat(root.field("decimal")).map(JsonNode::asString).contains("VALUE_NUMBER_FLOAT:1.25");
        assertThat(root.field("yes")).map(JsonNode::asString).contains("VALUE_TRUE:true");
        assertThat(root.field("no")).map(JsonNode::asString).contains("VALUE_FALSE:false");
        assertThat(root.field("none")).map(JsonNode::asString).contains("VALUE_NULL:null");
    }

    @Test
    void visitorDispatchesToMethodsMatchingParsedNodeTypes() {
        JsonNode root = JsonNode.parser().parse("""
            {"string":"text","number":5,"bool":false,"none":null,"array":[1],"object":{"a":1}}
            """);
        DescribingVisitor visitor = new DescribingVisitor();

        assertThat(root.field("string").orElseThrow().visit(visitor)).isEqualTo("string:text");
        assertThat(root.field("number").orElseThrow().visit(visitor)).isEqualTo("number:5");
        assertThat(root.field("bool").orElseThrow().visit(visitor)).isEqualTo("boolean:false");
        assertThat(root.field("none").orElseThrow().visit(visitor)).isEqualTo("null");
        assertThat(root.field("array").orElseThrow().visit(visitor)).isEqualTo("array:1");
        assertThat(root.field("object").orElseThrow().visit(visitor)).isEqualTo("object:a");
    }

    @Test
    void invalidTypeConversionsFailWithUsefulExceptions() {
        JsonNode string = JsonNode.parser().parse("\"not-a-number\"");
        JsonNode array = JsonNode.parser().parse("[true]");
        JsonNode object = JsonNode.parser().parse("{\"key\":\"value\"}");

        assertThatThrownBy(string::asNumber)
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("JSON string cannot be converted to a number");
        assertThatThrownBy(array::asObject)
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("JSON array cannot be converted to an object");
        assertThatThrownBy(object::asArray)
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("JSON object cannot be converted to an array");
    }

    @Test
    void writerSerializesStructuredJsonAndAllSupportedValueOverloads() {
        ByteBuffer binary = ByteBuffer.wrap(new byte[] {1, 2, 3, 4});
        JsonWriter writer = JsonWriter.create();
        writer.writeStartObject()
            .writeFieldName("text").writeValue("line\n\"quoted\"")
            .writeFieldName("boolean").writeValue(true)
            .writeFieldName("nullValue").writeNull()
            .writeFieldName("numbers").writeStartArray()
                .writeValue((short) 2)
                .writeValue(3)
                .writeValue(4L)
                .writeValue(1.25f)
                .writeValue(2.5d)
                .writeValue(new BigInteger("12345678901234567890"))
                .writeNumber("6.75")
            .writeEndArray()
            .writeFieldName("decimalAsString").writeValue(new BigDecimal("123.4500"))
            .writeFieldName("binary").writeValue(binary)
            .writeFieldName("instant").writeValue(Instant.parse("1970-01-01T00:00:01.500Z"))
            .writeEndObject();

        JsonNode root = JsonNode.parser().parse(new String(writer.getBytes(), StandardCharsets.UTF_8));

        assertThat(root.field("text")).map(JsonNode::asString).contains("line\n\"quoted\"");
        assertThat(root.field("boolean")).map(JsonNode::asBoolean).contains(true);
        assertThat(root.field("nullValue")).hasValueSatisfying(node -> assertThat(node.isNull()).isTrue());
        assertThat(root.field("numbers")).hasValueSatisfying(numbers -> {
            assertThat(numbers.asArray()).hasSize(7);
            assertThat(numbers.index(0)).map(JsonNode::asNumber).contains("2");
            assertThat(numbers.index(1)).map(JsonNode::asNumber).contains("3");
            assertThat(numbers.index(2)).map(JsonNode::asNumber).contains("4");
            assertThat(numbers.index(3)).map(JsonNode::asNumber).contains("1.25");
            assertThat(numbers.index(4)).map(JsonNode::asNumber).contains("2.5");
            assertThat(numbers.index(5)).map(JsonNode::asNumber).contains("12345678901234567890");
            assertThat(numbers.index(6)).map(JsonNode::asNumber).contains("6.75");
        });
        assertThat(root.field("decimalAsString")).map(JsonNode::asString).contains("123.4500");
        assertThat(root.field("binary")).map(JsonNode::asString).contains("AQIDBA==");
        assertThat(root.field("instant")).map(JsonNode::asNumber).contains("1.500");
    }

    @Test
    void writerBuilderAcceptsCustomJsonFactoryAndWrapsGenerationErrors() {
        JsonWriter writer = JsonWriter.builder()
            .jsonFactory(JsonNodeParser.DEFAULT_JSON_FACTORY)
            .build();
        writer.writeStartArray().writeValue("created by custom factory").writeEndArray();

        assertThat(JsonNode.parser().parse(writer.getBytes()).index(0))
            .map(JsonNode::asString)
            .contains("created by custom factory");

        assertThatThrownBy(() -> JsonWriter.builder()
            .jsonGeneratorFactory(outputStream -> {
                throw new IOException("boom");
            })
            .build())
            .isInstanceOf(JsonWriter.JsonGenerationException.class)
            .hasMessageContaining("boom");
    }

    private static final class ScalarDescriptionNode implements JsonNode {
        private final String description;

        private ScalarDescriptionNode(String description) {
            this.description = description;
        }

        @Override
        public boolean isString() {
            return true;
        }

        @Override
        public String asNumber() {
            throw new UnsupportedOperationException("This test node stores scalar descriptions as strings.");
        }

        @Override
        public String asString() {
            return description;
        }

        @Override
        public boolean asBoolean() {
            throw new UnsupportedOperationException("This test node stores scalar descriptions as strings.");
        }

        @Override
        public List<JsonNode> asArray() {
            throw new UnsupportedOperationException("This test node stores scalar descriptions as strings.");
        }

        @Override
        public Map<String, JsonNode> asObject() {
            throw new UnsupportedOperationException("This test node stores scalar descriptions as strings.");
        }

        @Override
        public Object asEmbeddedObject() {
            throw new UnsupportedOperationException("This test node stores scalar descriptions as strings.");
        }

        @Override
        public <T> T visit(JsonNodeVisitor<T> visitor) {
            return visitor.visitString(description);
        }

        @Override
        public String text() {
            return description;
        }
    }

    private static final class DescribingVisitor implements JsonNodeVisitor<String> {
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
            return "object:" + String.join(",", value.keySet());
        }

        @Override
        public String visitEmbeddedObject(Object value) {
            return "embedded:" + value;
        }
    }
}
