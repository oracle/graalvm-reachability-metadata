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
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises YAML schema validation for an application's service configuration.
public class ConfigurationYamlValidationTest {
    @Test
    @Timeout(60)
    public void validatesYamlConfigurationAndReportsTheInvalidProperty() {
        String schema = """
                $schema: https://json-schema.org/draft/2020-12/schema
                type: object
                required:
                  - service
                  - retries
                properties:
                  service:
                    type: object
                    required:
                      - name
                      - endpoint
                    properties:
                      name:
                        type: string
                        minLength: 1
                      endpoint:
                        type: string
                        format: uri
                    additionalProperties: false
                  retries:
                    type: integer
                    minimum: 0
                    maximum: 5
                additionalProperties: false
                """;
        String validConfiguration = """
                service:
                  name: inventory-api
                  endpoint: https://inventory.example.test/api
                retries: 3
                """;
        String invalidConfiguration = """
                service:
                  name: inventory-api
                  endpoint: https://inventory.example.test/api
                retries: 8
                """;

        JsonSchema configurationSchema = JsonSchemaFactory
                .getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(schema, InputFormat.YAML);

        Set<ValidationMessage> validMessages = configurationSchema
                .validate(validConfiguration, InputFormat.YAML);
        Set<ValidationMessage> invalidMessages = configurationSchema
                .validate(invalidConfiguration, InputFormat.YAML);

        assertTrue(validMessages.isEmpty());
        assertFalse(invalidMessages.isEmpty());
        assertEquals(1, invalidMessages.size());
        ValidationMessage message = invalidMessages.iterator().next();
        assertEquals("1011", message.getCode());
        assertEquals("$.retries", message.getInstanceLocation().toString());
    }
}
