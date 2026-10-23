/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_jsonwebtoken.jjwt_jackson;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.DeserializationException;
import io.jsonwebtoken.jackson.io.JacksonDeserializer;
import io.jsonwebtoken.jackson.io.JacksonSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises Jackson's enum-valued claim codec for partner access-tier vocabularies.
public class PartnerAccessTierTest {
    @Test
    @Timeout(60)
    public void restoresPartnerTiersAndRejectsUnrecognizedPrivileges() {
        JacksonDeserializer<Map<String, ?>> partnerDecoder = new JacksonDeserializer<>(
                Map.of("access_tier", AccessTier.class));
        JacksonSerializer<Map<String, ?>> serializer = new JacksonSerializer<>();
        JacksonDeserializer<Map<String, ?>> jsonClient = new JacksonDeserializer<>();

        for (AccessTier tier : AccessTier.values()) {
            String response = """
                    {"sub":"partner-704","access_tier":"%s"}
                    """.formatted(tier.wireValue());
            Claims partner = Jwts.claims()
                    .add(partnerDecoder.deserialize(response.getBytes(StandardCharsets.UTF_8)))
                    .build();
            AccessTier receivedTier = partner.get("access_tier", AccessTier.class);
            List<String> availableActions = switch (receivedTier) {
                case READ_ONLY -> List.of("shipment:read");
                case DISPATCH -> List.of("shipment:read", "shipment:create");
            };

            assertEquals("partner-704", partner.getSubject());
            assertEquals(tier, receivedTier);
            assertEquals(tier == AccessTier.DISPATCH, availableActions.contains("shipment:create"));

            // Forward the identity using the partner's vocabulary, never Java enum constant names.
            byte[] forwarded = serializer.serialize(partner);
            assertEquals(Map.of("sub", "partner-704", "access_tier", tier.wireValue()),
                    jsonClient.deserialize(forwarded));
            assertEquals(tier, partnerDecoder.deserialize(forwarded).get("access_tier"));
        }

        String unknownTier = """
                {"sub":"partner-704","access_tier":"warehouse:administrator"}
                """;
        assertThrows(DeserializationException.class,
                () -> partnerDecoder.deserialize(unknownTier.getBytes(StandardCharsets.UTF_8)));
    }

    public enum AccessTier {
        READ_ONLY("warehouse:read-only"),
        DISPATCH("warehouse:dispatch");

        private final String wireValue;

        AccessTier(String wireValue) {
            this.wireValue = wireValue;
        }

        @JsonValue
        public String wireValue() {
            return wireValue;
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static AccessTier fromWireValue(String value) {
            for (AccessTier tier : values()) {
                if (tier.wireValue.equals(value)) {
                    return tier;
                }
            }
            throw new IllegalArgumentException("Unrecognized partner access tier: " + value);
        }
    }
}
