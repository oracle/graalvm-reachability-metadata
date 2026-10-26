/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_jsonwebtoken.jjwt_jackson;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.jackson.io.JacksonDeserializer;
import io.jsonwebtoken.jackson.io.JacksonSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Jackson's JSON claims codec for persisted tenant authorization templates.
public class TenantClaimTemplateTest {
    @Test
    @Timeout(60)
    public void reloadsTenantAuthorizationTemplate() throws IOException {
        Map<String, Object> authorization = Map.of(
                "tenant", "north-warehouse",
                "roles", List.of("inventory:read", "shipment:create"),
                "limits", Map.of("dailyShipments", 250, "bulkExport", false));
        Date issuedAt = Date.from(Instant.parse("2030-01-01T12:00:00Z"));
        Claims template = Jwts.claims()
                .issuer("warehouse-identity")
                .subject("operator-42")
                .issuedAt(issuedAt)
                .add("authorization", authorization)
                .build();

        JacksonSerializer<Map<String, ?>> serializer = new JacksonSerializer<>();
        JacksonDeserializer<Map<String, ?>> deserializer = new JacksonDeserializer<>();
        Path templateFile = Files.createTempFile("tenant-claims-", ".json");
        try {
            Files.write(templateFile, serializer.serialize(template));
            byte[] storedJson = Files.readAllBytes(templateFile);
            assertTrue(new String(storedJson, StandardCharsets.UTF_8).contains("north-warehouse"));

            Map<String, ?> restoredValues = deserializer.deserialize(storedJson);
            Claims restored = Jwts.claims().add(restoredValues).build();

            assertEquals("warehouse-identity", restored.getIssuer());
            assertEquals("operator-42", restored.getSubject());
            assertEquals(issuedAt, restored.getIssuedAt());
            assertEquals(authorization, restored.get("authorization", Map.class));
        } finally {
            Files.deleteIfExists(templateFile);
        }
    }
}
