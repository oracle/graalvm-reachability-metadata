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

/// Exercises one-of polymorphic command validation for an application's API.
public class PolymorphicCommandValidationTest {
    @Test
    @Timeout(60)
    public void validatesCommandsByTheirVariant() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonSchema commandSchema = JsonSchemaFactory
                .getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema("""
                        {
                          "type": "object",
                          "required": ["kind"],
                          "properties": {
                            "kind": {"type": "string"}
                          },
                          "oneOf": [
                            {"$ref": "#/$defs/email"},
                            {"$ref": "#/$defs/sms"}
                          ],
                          "$defs": {
                            "email": {
                              "type": "object",
                              "required": ["kind", "address", "subject"],
                              "properties": {
                                "kind": {"const": "email"},
                                "address": {"type": "string", "format": "email"},
                                "subject": {"type": "string", "minLength": 1}
                              },
                              "additionalProperties": false
                            },
                            "sms": {
                              "type": "object",
                              "required": ["kind", "phone", "message"],
                              "properties": {
                                "kind": {"const": "sms"},
                                "phone": {"type": "string", "pattern": "^\\\\+[0-9]{11,15}$"},
                                "message": {"type": "string", "minLength": 1}
                              },
                              "additionalProperties": false
                            }
                          }
                        }
                        """);
        JsonNode emailCommand = objectMapper.readTree("""
                {
                  "kind": "email",
                  "address": "ops@example.com",
                  "subject": "Build completed"
                }
                """);
        JsonNode smsCommand = objectMapper.readTree("""
                {
                  "kind": "sms",
                  "phone": "+14155550123",
                  "message": "Build completed"
                }
                """);
        JsonNode invalidEmailCommand = objectMapper.readTree("""
                {
                  "kind": "push",
                  "address": "ops@example.com"
                }
                """);

        assertTrue(commandSchema.validate(emailCommand).isEmpty());
        assertTrue(commandSchema.validate(smsCommand).isEmpty());
        Set<ValidationMessage> invalidMessages = commandSchema.validate(invalidEmailCommand);
        Set<String> invalidLocations = invalidMessages.stream()
                .map(message -> message.getInstanceLocation().toString())
                .collect(Collectors.toSet());

        assertEquals(Set.of("$", "$.kind"), invalidLocations);
    }
}
