/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_networknt_json_schema_validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.ExecutionConfig;
import com.networknt.schema.ExecutionContext;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationResult;
import com.networknt.schema.annotation.JsonNodeAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises schema annotation collection for an application's generated product catalog.
public class SchemaAnnotationCatalogTest {
    @Test
    @Timeout(60)
    public void collectsOnlyDisplayAnnotationsForValidProductData() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonSchema productSchema = JsonSchemaFactory
                .getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema("""
                        {
                          "type": "object",
                          "title": "Product",
                          "description": "A product shown in the storefront",
                          "required": ["sku", "name", "price"],
                          "properties": {
                            "sku": {
                              "type": "string",
                              "title": "SKU",
                              "description": "The merchant product code",
                              "pattern": "^[A-Z]{3}-[0-9]{4}$"
                            },
                            "name": {
                              "type": "string",
                              "title": "Display name"
                            },
                            "price": {
                              "type": "number",
                              "title": "Price",
                              "minimum": 0,
                              "default": 0
                            }
                          },
                          "additionalProperties": false
                        }
                        """);
        JsonNode product = objectMapper.readTree("""
                {
                  "sku": "ABC-2048",
                  "name": "Desk lamp",
                  "price": 29.95
                }
                """);

        ExecutionConfig executionConfig = new ExecutionConfig();
        executionConfig.setAnnotationCollectionEnabled(true);
        executionConfig.setAnnotationCollectionFilter(keyword ->
                Set.of("title", "description").contains(keyword));
        ValidationResult result = productSchema.validateAndCollect(
                new ExecutionContext(executionConfig), product);

        assertTrue(result.getValidationMessages().isEmpty());
        Map<JsonNodePath, List<JsonNodeAnnotation>> annotations = result.getExecutionContext()
                .getAnnotations().asMap();
        Map<String, String> displayText = annotations.entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().toString(),
                        entry -> entry.getValue().stream()
                                .map(annotation -> annotation.getKeyword() + "=" + annotation.getValue())
                                .collect(Collectors.joining(","))));

        assertEquals(Set.of("$", "$.sku", "$.name", "$.price"), displayText.keySet());
        assertEquals("title=Product,description=A product shown in the storefront",
                displayText.get("$"));
        assertEquals("title=SKU,description=The merchant product code", displayText.get("$.sku"));
        assertEquals("title=Display name", displayText.get("$.name"));
        assertEquals("title=Price", displayText.get("$.price"));
    }
}
