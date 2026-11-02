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

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises modular JSON Schema reference loading for an application's API contract.
public class ModularApiContractValidationTest {
    private static final String ADDRESS_SCHEMA_URI = "https://schemas.example.test/address.json";

    @Test
    @Timeout(60)
    public void validatesOrderPayloadWithReferencedAddressSchema() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        String addressSchema = """
                {
                  "$id": "https://schemas.example.test/address.json",
                  "type": "object",
                  "required": ["street", "postalCode"],
                  "properties": {
                    "street": {"type": "string", "minLength": 1},
                    "postalCode": {"type": "string", "pattern": "^[0-9]{5}$"}
                  },
                  "additionalProperties": false
                }
                """;
        String orderSchema = """
                {
                  "$id": "https://schemas.example.test/order.json",
                  "type": "object",
                  "required": ["orderId", "shippingAddress"],
                  "properties": {
                    "orderId": {"type": "string", "pattern": "^ORD-[0-9]{4}$"},
                    "shippingAddress": {"$ref": "https://schemas.example.test/address.json"}
                  },
                  "additionalProperties": false
                }
                """;

        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(
                SpecVersion.VersionFlag.V202012,
                builder -> builder.schemaLoaders(loaders -> loaders.schemas(Map.of(
                        ADDRESS_SCHEMA_URI, addressSchema))));
        JsonSchema orderContract = factory.getSchema(orderSchema);
        JsonNode validOrder = objectMapper.readTree("""
                {
                  "orderId": "ORD-2048",
                  "shippingAddress": {
                    "street": "1 Main Street",
                    "postalCode": "02110"
                  }
                }
                """);
        JsonNode invalidOrder = objectMapper.readTree("""
                {
                  "orderId": "2048",
                  "shippingAddress": {
                    "street": "1 Main Street",
                    "postalCode": "0211"
                  }
                }
                """);

        Set<ValidationMessage> validMessages = orderContract.validate(validOrder);
        Set<ValidationMessage> invalidMessages = orderContract.validate(invalidOrder);
        Set<String> invalidLocations = invalidMessages.stream()
                .map(message -> message.getInstanceLocation().toString())
                .collect(Collectors.toSet());

        assertTrue(validMessages.isEmpty());
        assertEquals(2, invalidMessages.size());
        assertEquals(Set.of("$.orderId", "$.shippingAddress.postalCode"), invalidLocations);
    }
}
