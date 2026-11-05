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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises format validation for an application's webhook subscription API.
public class WebhookFormatValidationTest {
    @Test
    @Timeout(60)
    public void validatesWebhookContactAndDeliveryFields() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder()
                .formatAssertionsEnabled(true)
                .build();
        JsonSchema subscriptionSchema = JsonSchemaFactory
                .getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema("""
                        {
                          "type": "object",
                          "required": ["callback", "ownerEmail", "createdAt"],
                          "properties": {
                            "callback": {"type": "string", "format": "uri"},
                            "ownerEmail": {"type": "string", "format": "email"},
                            "createdAt": {"type": "string", "format": "date-time"}
                          },
                          "additionalProperties": false
                        }
                        """, config);

        JsonNode validSubscription = objectMapper.readTree("""
                {
                  "callback": "https://hooks.example.test/orders",
                  "ownerEmail": "alerts@example.com",
                  "createdAt": "2025-04-12T09:30:00Z"
                }
                """);
        JsonNode invalidSubscription = objectMapper.readTree("""
                {
                  "callback": "orders.example.test/webhook",
                  "ownerEmail": "alerts.example",
                  "createdAt": "April 12, 2025"
                }
                """);

        Set<ValidationMessage> validMessages = subscriptionSchema.validate(validSubscription);
        Set<ValidationMessage> invalidMessages = subscriptionSchema.validate(invalidSubscription);
        Set<String> invalidLocations = invalidMessages.stream()
                .map(message -> message.getInstanceLocation().toString())
                .collect(Collectors.toSet());

        assertTrue(validMessages.isEmpty(), validMessages.toString());
        assertEquals(Set.of("/callback", "/ownerEmail", "/createdAt"), invalidLocations);
    }
}
