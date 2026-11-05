/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_networknt_json_schema_validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.ValidationMessage;
import com.networknt.schema.format.AbstractFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises custom format registration for an application's partner-order validation.
public class CustomFormatOrderValidationTest {
    @Test
    @Timeout(60)
    public void validatesPartnerOrderCodesWithAnApplicationFormat() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonMetaSchema metaSchema = JsonMetaSchema.builder(JsonMetaSchema.getV202012())
                .format(new PartnerOrderCodeFormat())
                .build();
        JsonSchemaFactory factory = JsonSchemaFactory.builder()
                .defaultMetaSchemaIri("https://json-schema.org/draft/2020-12/schema")
                .metaSchema(metaSchema)
                .build();
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder()
                .formatAssertionsEnabled(true)
                .build();
        JsonSchema orderSchema = factory.getSchema("""
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
                  "required": ["partnerOrderId", "amount"],
                  "properties": {
                    "partnerOrderId": {
                      "type": "string",
                      "format": "partner-order-code"
                    },
                    "amount": {
                      "type": "number",
                      "exclusiveMinimum": 0
                    }
                  },
                  "additionalProperties": false
                }
                """, config);
        JsonNode validOrder = objectMapper.readTree("""
                {
                  "partnerOrderId": "ORD-ABC-2048",
                  "amount": 125.50
                }
                """);
        JsonNode invalidOrder = objectMapper.readTree("""
                {
                  "partnerOrderId": "ORD-abc-2048",
                  "amount": 125.50
                }
                """);

        Set<ValidationMessage> validMessages = orderSchema.validate(validOrder);
        Set<ValidationMessage> invalidMessages = orderSchema.validate(invalidOrder);
        Set<String> invalidLocations = invalidMessages.stream()
                .map(message -> message.getInstanceLocation().toString())
                .collect(Collectors.toSet());

        assertTrue(validMessages.isEmpty(), validMessages.toString());
        assertEquals(Set.of("/partnerOrderId"), invalidLocations);
    }

    private static final class PartnerOrderCodeFormat extends AbstractFormat {
        private PartnerOrderCodeFormat() {
            super("partner-order-code", "an uppercase partner order code");
        }

        @Override
        public boolean matches(String value) {
            return value.matches("ORD-[A-Z]{3}-[0-9]{4}");
        }
    }
}
