/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_networknt_json_schema_validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.ApplyDefaultsStrategy;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.networknt.schema.ValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises schema-driven request defaulting for an application's API boundary.
public class RequestDefaultingValidationTest {
    @Test
    @Timeout(60)
    public void validatesAndNormalizesRequestDefaults() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder()
                .applyDefaultsStrategy(new ApplyDefaultsStrategy(true, false, false))
                .build();
        JsonSchema requestSchema = JsonSchemaFactory
                .getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema("""
                        {
                          "type": "object",
                          "required": ["customerId"],
                          "properties": {
                            "customerId": {
                              "type": "string",
                              "pattern": "^CUS-[0-9]{4}$"
                            },
                            "region": {
                              "type": "string",
                              "enum": ["us-east-1", "eu-west-1"],
                              "default": "us-east-1"
                            },
                            "notificationsEnabled": {
                              "type": "boolean",
                              "default": true
                            }
                          },
                          "additionalProperties": false
                        }
                        """, config);
        JsonNode request = objectMapper.readTree("""
                {
                  "customerId": "CUS-2048"
                }
                """);

        ValidationResult result = requestSchema.walk(request, true);
        Set<ValidationMessage> messages = result.getValidationMessages();

        assertTrue(messages.isEmpty());
        assertEquals("us-east-1", request.get("region").textValue());
        assertTrue(request.get("notificationsEnabled").booleanValue());
    }
}
