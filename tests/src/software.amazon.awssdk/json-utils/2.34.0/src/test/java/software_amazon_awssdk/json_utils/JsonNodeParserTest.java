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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.protocols.jsoncore.JsonNode;
import software.amazon.awssdk.protocols.jsoncore.JsonNodeParser;
import software.amazon.awssdk.protocols.jsoncore.JsonValueNodeFactory;
import software.amazon.awssdk.thirdparty.jackson.core.JsonFactory;
import software.amazon.awssdk.thirdparty.jackson.core.json.JsonReadFeature;

public class JsonNodeParserTest {
    @Test
    void parsesNestedDocumentAndNavigatesTypedValues() {
        JsonNode root = JsonNode.parser().parse("""
                {
                  "name": "order",
                  "quantity": 17,
                  "price": -12.50e2,
                  "active": true,
                  "deleted": false,
                  "items": [{"sku": "tea"}, null, [], {}]
                }
                """);

        assertThat(root.isObject()).isTrue();
        assertThat(root.asObject()).containsOnlyKeys("name", "quantity", "price", "active", "deleted", "items");
        JsonNode name = root.field("name").orElseThrow();
        assertThat(name.isString()).isTrue();
        assertThat(name.asString()).isEqualTo("order");
        assertThat(name.text()).isEqualTo("order");
        JsonNode quantity = root.field("quantity").orElseThrow();
        assertThat(quantity.isNumber()).isTrue();
        assertThat(quantity.asNumber()).isEqualTo("17");
        assertThat(quantity.text()).isEqualTo("17");
        assertThat(root.field("price").orElseThrow().asNumber()).isEqualTo("-12.50e2");
        JsonNode active = root.field("active").orElseThrow();
        assertThat(active.isBoolean()).isTrue();
        assertThat(active.asBoolean()).isTrue();
        assertThat(active.text()).isEqualTo("true");
        assertThat(root.field("deleted").orElseThrow().asBoolean()).isFalse();

        JsonNode items = root.field("items").orElseThrow();
        assertThat(items.isArray()).isTrue();
        assertThat(items.asArray()).hasSize(4);
        assertThat(items.index(0).orElseThrow().field("sku").orElseThrow().asString()).isEqualTo("tea");
        JsonNode nullItem = items.index(1).orElseThrow();
        assertThat(nullItem.isNull()).isTrue();
        assertThat(nullItem.text()).isNull();
        assertThat(items.index(2).orElseThrow().asArray()).isEmpty();
        assertThat(items.index(3).orElseThrow().asObject()).isEmpty();
        assertThat(items.text()).isNull();
        assertThat(root.text()).isNull();
        assertThat(root.field("absent")).isEmpty();
        assertThat(items.index(-1)).isEmpty();
        assertThat(items.index(4)).isEmpty();
        assertThat(name.field("name")).isEmpty();
        assertThat(name.index(0)).isEmpty();
        assertThat(name.isEmbeddedObject()).isFalse();
    }

    @Test
    void parsesUtf8BytesAndInputStream() throws Exception {
        String document = """
                {"message":"caf\u00e9 \\u263a","count":2}
                """;
        byte[] bytes = document.getBytes(StandardCharsets.UTF_8);
        JsonNodeParser parser = JsonNodeParser.create();
        JsonNode fromBytes = parser.parse(bytes);
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            assertThat(parser.parse(input)).isEqualTo(fromBytes);
        }
        assertThat(fromBytes.field("message").orElseThrow().asString()).isEqualTo("caf\u00e9 \u263a");
        assertThat(fromBytes.field("count").orElseThrow().asNumber()).isEqualTo("2");
    }

    @Test
    void supportsCommentsAndScalarRootValues() {
        JsonNodeParser parser = JsonNode.parser();
        assertThat(parser.parse("/* configuration */ [true, false]").asArray())
                .extracting(JsonNode::asBoolean).containsExactly(true, false);
        assertThat(parser.parse("\"hello\"").asString()).isEqualTo("hello");
        assertThat(parser.parse("42").asNumber()).isEqualTo("42");
        assertThat(parser.parse("null").isNull()).isTrue();
        assertThat(JsonNode.emptyObjectNode().asObject()).isEmpty();
        assertThat(JsonNode.emptyObjectNode()).isEqualTo(parser.parse("{}"));
    }

    @Test
    void usesConfiguredJsonAndValueFactories() {
        JsonFactory factory = JsonFactory.builder().enable(JsonReadFeature.ALLOW_SINGLE_QUOTES).build();
        AtomicInteger scalarCount = new AtomicInteger();
        JsonNodeParser parser = JsonNode.parserBuilder()
                .jsonFactory(factory)
                .jsonValueNodeFactory((input, token) -> {
                    scalarCount.incrementAndGet();
                    return JsonValueNodeFactory.DEFAULT.node(input, token);
                })
                .build();

        JsonNode root = parser.parse("{'values':['custom',7,true,false,null]}");
        assertThat(root.field("values").orElseThrow().asArray()).extracting(JsonNode::text)
                .containsExactly("custom", "7", "true", "false", null);
        assertThat(scalarCount.get()).isEqualTo(5);
    }

    @Test
    void rejectsInvalidJsonAndMismatchedValueAccess() {
        JsonNodeParser parser = JsonNodeParser.builder().removeErrorLocations(true).build();
        assertThatThrownBy(() -> parser.parse("{\"sensitive\": @}"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageNotContaining("sensitive");
        List<JsonNode> values = parser.parse("[\"text\", 1, true, null, [], {}]").asArray();
        assertThatThrownBy(() -> values.get(0).asNumber()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> values.get(1).asString()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> values.get(2).asArray()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> values.get(3).asBoolean()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> values.get(4).asObject()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> values.get(5).asEmbeddedObject()).isInstanceOf(UnsupportedOperationException.class);
    }
}
