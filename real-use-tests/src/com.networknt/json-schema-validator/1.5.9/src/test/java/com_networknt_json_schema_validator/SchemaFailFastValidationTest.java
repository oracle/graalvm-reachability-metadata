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
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises fail-fast request validation for an application's order endpoint.
public class SchemaFailFastValidationTest {
    @Test
    @Timeout(60)
    public void reportsTheFirstInvalidOrderField() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder()
                .failFast(true)
                .build();
        JsonSchema orderSchema = JsonSchemaFactory
                .getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema("""
                        {
                          "type": "object",
                          "required": ["orderId", "quantity"],
                          "properties": {
                            "orderId": {
                              "type": "string",
                              "pattern": "^ORD-[0-9]{6}$"
                            },
                            "quantity": {
                              "type": "integer",
                              "minimum": 1
                            }
                          },
                          "additionalProperties": false
                        }
                        """, config);
        JsonNode validOrder = objectMapper.readTree("""
                {
                  "orderId": "ORD-204800",
                  "quantity": 2
                }
                """);
        JsonNode invalidOrder = objectMapper.readTree("""
                {
                  "orderId": "204800",
                  "quantity": 0
                }
                """);

        assertTrue(orderSchema.validate(validOrder).isEmpty());
        Set<ValidationMessage> messages = orderSchema.validate(invalidOrder);

        assertEquals(1, messages.size());
        ValidationMessage message = messages.iterator().next();
        assertEquals("/orderId", message.getInstanceLocation().toString());
    }
}
