/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_networknt_json_schema_validator;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises OpenAPI discriminator dispatch for an application's event-ingestion API.
public class OpenApiDiscriminatorValidationTest {
    @Test
    @Timeout(60)
    public void dispatchesEventsToTheMappedPayloadSchema() {
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder()
                .discriminatorKeywordEnabled(true)
                .formatAssertionsEnabled(true)
                .build();
        JsonSchema eventSchema = JsonSchemaFactory
                .getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema("""
                        {
                          "$schema": "https://json-schema.org/draft/2020-12/schema",
                          "type": "object",
                          "required": ["eventType"],
                          "discriminator": {
                            "propertyName": "eventType",
                            "mapping": {
                              "email.delivered": "#/$defs/emailDelivered",
                              "sms.delivered": "#/$defs/smsDelivered"
                            }
                          },
                          "oneOf": [
                            {"$ref": "#/$defs/emailDelivered"},
                            {"$ref": "#/$defs/smsDelivered"}
                          ],
                          "$defs": {
                            "emailDelivered": {
                              "type": "object",
                              "required": ["eventType", "messageId", "recipient"],
                              "properties": {
                                "eventType": {"const": "email.delivered"},
                                "messageId": {"type": "string", "minLength": 1},
                                "recipient": {"type": "string", "format": "email"}
                              },
                              "additionalProperties": false
                            },
                            "smsDelivered": {
                              "type": "object",
                              "required": ["eventType", "messageId", "phone"],
                              "properties": {
                                "eventType": {"const": "sms.delivered"},
                                "messageId": {"type": "string", "minLength": 1},
                                "phone": {"type": "string", "pattern": "^\\\\+[0-9]{11,15}$"}
                              },
                              "additionalProperties": false
                            }
                          }
                        }
                        """, config);

        Set<ValidationMessage> emailMessages = eventSchema.validate("""
                {
                  "eventType": "email.delivered",
                  "messageId": "msg-100",
                  "recipient": "customer@example.com"
                }
                """, InputFormat.JSON);
        Set<ValidationMessage> smsMessages = eventSchema.validate("""
                {
                  "eventType": "sms.delivered",
                  "messageId": "msg-101",
                  "phone": "+14155550123"
                }
                """, InputFormat.JSON);
        Set<ValidationMessage> invalidMessages = eventSchema.validate("""
                {
                  "eventType": "email.delivered",
                  "messageId": "msg-102",
                  "recipient": "not-an-email"
                }
                """, InputFormat.JSON);

        assertTrue(emailMessages.isEmpty(), emailMessages::toString);
        assertTrue(smsMessages.isEmpty(), smsMessages::toString);
        assertFalse(invalidMessages.isEmpty());
        assertTrue(invalidMessages.stream()
                .anyMatch(message -> message.getInstanceLocation().toString().endsWith("recipient")),
                invalidMessages::toString);
    }
}
