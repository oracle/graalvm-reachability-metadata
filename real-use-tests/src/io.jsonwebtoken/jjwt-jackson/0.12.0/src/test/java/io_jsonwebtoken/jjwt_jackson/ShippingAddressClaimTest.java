/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_jsonwebtoken.jjwt_jackson;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.jackson.io.JacksonDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Jackson's custom-claim type mapping for an identity provider's shipping address response.
public class ShippingAddressClaimTest {
    @Test
    @Timeout(60)
    public void mapsAddressClaimForShippingLabel() {
        String identityResponse = """
                {
                  "sub": "customer-704",
                  "email_verified": true,
                  "address": {
                    "recipient": "Morgan Lee",
                    "lines": ["14 Market Street", "Unit 6"],
                    "postal_code": "00123",
                    "country": "US"
                  },
                  "preferences": {"delivery": "front-desk"}
                }
                """;
        JacksonDeserializer<Map<String, ?>> deserializer = new JacksonDeserializer<>(
                Map.of("address", ShippingAddress.class));

        Map<String, ?> decoded = deserializer.deserialize(identityResponse.getBytes(StandardCharsets.UTF_8));
        assertInstanceOf(ShippingAddress.class, decoded.get("address"));
        Claims customer = Jwts.claims().add(decoded).build();
        ShippingAddress address = customer.get("address", ShippingAddress.class);
        String shippingLabel = address.recipient + "\n"
                + String.join("\n", address.lines) + "\n"
                + address.postalCode + " " + address.country;

        assertEquals("customer-704", customer.getSubject());
        assertTrue(customer.get("email_verified", Boolean.class));
        assertEquals(Map.of("delivery", "front-desk"), customer.get("preferences", Map.class));
        assertEquals("""
                Morgan Lee
                14 Market Street
                Unit 6
                00123 US""", shippingLabel);
    }

    public static final class ShippingAddress {
        private final String recipient;
        private final List<String> lines;
        private final String postalCode;
        private final String country;

        @JsonCreator
        public ShippingAddress(
                @JsonProperty("recipient") String recipient,
                @JsonProperty("lines") List<String> lines,
                @JsonProperty("postal_code") String postalCode,
                @JsonProperty("country") String country) {
            this.recipient = recipient;
            this.lines = List.copyOf(lines);
            this.postalCode = postalCode;
            this.country = country;
        }
    }
}
