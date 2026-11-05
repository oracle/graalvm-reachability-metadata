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
import com.networknt.schema.resource.ClasspathSchemaLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises classpath-backed schema loading for an application's reusable checkout contract.
public class ClasspathSchemaLoadingTest {
    @Test
    @Timeout(60)
    public void validatesCheckoutWithClasspathReferencedAddressSchema() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(
                SpecVersion.VersionFlag.V202012,
                builder -> builder.schemaLoaders(loaders -> loaders.add(new ClasspathSchemaLoader(
                        ScenarioSchemaClassLoader::new))));
        JsonSchema checkoutSchema = factory.getSchema(URI.create("classpath:/schemas/checkout.json"));
        JsonNode validCheckout = objectMapper.readTree("""
                {
                  "orderId": "ORD-2048",
                  "shippingAddress": {
                    "street": "1 Main Street",
                    "postalCode": "02110"
                  }
                }
                """);
        JsonNode invalidCheckout = objectMapper.readTree("""
                {
                  "orderId": "2048",
                  "shippingAddress": {
                    "street": "1 Main Street",
                    "postalCode": "0211"
                  }
                }
                """);

        Set<ValidationMessage> validMessages = checkoutSchema.validate(validCheckout);
        Set<ValidationMessage> invalidMessages = checkoutSchema.validate(invalidCheckout);
        Set<String> invalidLocations = invalidMessages.stream()
                .map(message -> message.getInstanceLocation().toString())
                .collect(Collectors.toSet());

        assertTrue(validMessages.isEmpty());
        assertEquals(2, invalidMessages.size());
        assertEquals(Set.of("$.orderId", "$.shippingAddress.postalCode"), invalidLocations);
    }

    private static final class ScenarioSchemaClassLoader extends ClassLoader {
        private static final String CHECKOUT_SCHEMA = """
                {
                  \"$schema\": \"https://json-schema.org/draft/2020-12/schema\",
                  \"$id\": \"classpath:/schemas/checkout.json\",
                  \"type\": \"object\",
                  \"required\": [\"orderId\", \"shippingAddress\"],
                  \"properties\": {
                    \"orderId\": {\"type\": \"string\", \"pattern\": \"^ORD-[0-9]{4}$\"},
                    \"shippingAddress\": {\"$ref\": \"address.json\"}
                  },
                  \"additionalProperties\": false
                }
                """;
        private static final String ADDRESS_SCHEMA = """
                {
                  \"$schema\": \"https://json-schema.org/draft/2020-12/schema\",
                  \"$id\": \"classpath:/schemas/address.json\",
                  \"type\": \"object\",
                  \"required\": [\"street\", \"postalCode\"],
                  \"properties\": {
                    \"street\": {\"type\": \"string\", \"minLength\": 1},
                    \"postalCode\": {\"type\": \"string\", \"pattern\": \"^[0-9]{5}$\"}
                  },
                  \"additionalProperties\": false
                }
                """;

        @Override
        public InputStream getResourceAsStream(String name) {
            String schema = switch (name) {
                case "schemas/checkout.json" -> CHECKOUT_SCHEMA;
                case "schemas/address.json" -> ADDRESS_SCHEMA;
                default -> null;
            };
            return schema == null ? null
                    : new ByteArrayInputStream(schema.getBytes(StandardCharsets.UTF_8));
        }
    }
}
