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

/// Exercises unevaluated-property analysis for an application's composed event schema.
public class ComposedEventUnevaluatedPropertiesTest {
    @Test
    @Timeout(60)
    public void acceptsPropertiesConsumedByComposedSchemasAndRejectsUnknownOnes() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonSchema eventSchema = JsonSchemaFactory
                .getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema("""
                        {
                          "$schema": "https://json-schema.org/draft/2020-12/schema",
                          "type": "object",
                          "allOf": [
                            {"$ref": "#/$defs/identity"},
                            {"$ref": "#/$defs/delivery"}
                          ],
                          "unevaluatedProperties": false,
                          "$defs": {
                            "identity": {
                              "type": "object",
                              "required": ["eventId", "eventType"],
                              "properties": {
                                "eventId": {
                                  "type": "string",
                                  "pattern": "^EVT-[0-9]{6}$"
                                },
                                "eventType": {"const": "invoice.created"}
                              }
                            },
                            "delivery": {
                              "type": "object",
                              "required": ["occurredAt", "recipient"],
                              "properties": {
                                "occurredAt": {"type": "string", "format": "date-time"},
                                "recipient": {"type": "string", "format": "email"}
                              }
                            }
                          }
                        }
                        """);
        JsonNode validEvent = objectMapper.readTree("""
                {
                  "eventId": "EVT-000042",
                  "eventType": "invoice.created",
                  "occurredAt": "2025-04-12T09:30:00Z",
                  "recipient": "billing@example.com"
                }
                """);
        JsonNode eventWithUnknownProperty = objectMapper.readTree("""
                {
                  "eventId": "EVT-000042",
                  "eventType": "invoice.created",
                  "occurredAt": "2025-04-12T09:30:00Z",
                  "recipient": "billing@example.com",
                  "debug": true
                }
                """);

        Set<ValidationMessage> validMessages = eventSchema.validate(validEvent);
        Set<ValidationMessage> invalidMessages = eventSchema.validate(eventWithUnknownProperty);
        Set<String> invalidLocations = invalidMessages.stream()
                .map(message -> message.getInstanceLocation().toString())
                .collect(Collectors.toSet());

        assertTrue(validMessages.isEmpty(), validMessages.toString());
        assertEquals(Set.of("$"), invalidLocations);
    }
}
