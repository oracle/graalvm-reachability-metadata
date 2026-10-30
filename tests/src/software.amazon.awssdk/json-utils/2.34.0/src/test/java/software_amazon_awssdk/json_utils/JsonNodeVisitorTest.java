/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.json_utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.protocols.jsoncore.JsonNode;
import software.amazon.awssdk.protocols.jsoncore.JsonNodeVisitor;

public class JsonNodeVisitorTest {
    @Test
    void convertsNestedDocumentToJavaValuesThroughVisitorDispatch() {
        JsonNode root = JsonNode.parser().parse("""
                {
                  "enabled": true,
                  "values": ["tea", 12.75, null, false],
                  "details": {"count": 2},
                  "empty": []
                }
                """);

        Object result = root.visit(new JavaValueVisitor());

        assertThat(result).isEqualTo(Map.of(
                "enabled", true,
                "values", Arrays.asList("tea", new BigDecimal("12.75"), null, false),
                "details", Map.of("count", new BigDecimal("2")),
                "empty", List.of()));
    }

    private static final class JavaValueVisitor implements JsonNodeVisitor<Object> {
        @Override
        public Object visitNull() {
            return null;
        }

        @Override
        public Object visitBoolean(boolean value) {
            return value;
        }

        @Override
        public Object visitNumber(String value) {
            return new BigDecimal(value);
        }

        @Override
        public Object visitString(String value) {
            return value;
        }

        @Override
        public Object visitArray(List<JsonNode> values) {
            return values.stream().map(value -> value.visit(this)).toList();
        }

        @Override
        public Object visitObject(Map<String, JsonNode> values) {
            Map<String, Object> converted = new LinkedHashMap<>();
            values.forEach((name, value) -> converted.put(name, value.visit(this)));
            return converted;
        }

        @Override
        public Object visitEmbeddedObject(Object value) {
            return value;
        }
    }
}
