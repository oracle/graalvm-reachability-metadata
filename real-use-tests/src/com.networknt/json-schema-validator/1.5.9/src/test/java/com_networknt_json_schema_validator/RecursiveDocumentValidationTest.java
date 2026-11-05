/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_networknt_json_schema_validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises dynamic recursive references for an application's hierarchical document validation.
public class RecursiveDocumentValidationTest {
    @Test
    @Timeout(60)
    public void validatesNestedDocumentNodesThroughDynamicReference() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonSchema documentSchema = JsonSchemaFactory
                .getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema("""
                        {
                          "$schema": "https://json-schema.org/draft/2020-12/schema",
                          "$id": "https://schemas.example.test/document.json",
                          "$dynamicAnchor": "node",
                          "type": "object",
                          "required": ["name"],
                          "properties": {
                            "name": {"type": "string", "minLength": 1},
                            "children": {
                              "type": "array",
                              "items": {"$dynamicRef": "#node"}
                            }
                          },
                          "additionalProperties": false
                        }
                        """);
        JsonNode validDocument = objectMapper.readTree("""
                {
                  "name": "Company",
                  "children": [
                    {"name": "Engineering", "children": [{"name": "Platform"}]},
                    {"name": "Sales"}
                  ]
                }
                """);
        JsonNode invalidDocument = objectMapper.readTree("""
                {
                  "name": "Company",
                  "children": [{"name": "Engineering"}, {"children": []}]
                }
                """);

        Set<ValidationMessage> validMessages = documentSchema.validate(validDocument);
        Set<ValidationMessage> invalidMessages = documentSchema.validate(invalidDocument);
        Set<String> invalidLocations = invalidMessages.stream()
                .map(message -> message.getInstanceLocation().toString())
                .collect(Collectors.toSet());

        assertTrue(validMessages.isEmpty());
        assertEquals(Set.of("$.children[1]"), invalidLocations);
    }
}
