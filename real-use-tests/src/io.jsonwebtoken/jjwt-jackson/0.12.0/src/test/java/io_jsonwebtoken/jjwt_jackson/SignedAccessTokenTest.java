/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_jsonwebtoken.jjwt_jackson;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.jackson.io.JacksonDeserializer;
import io.jsonwebtoken.jackson.io.JacksonSerializer;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Jackson's signed JWT JSON integration for service-to-service access tokens.
public class SignedAccessTokenTest {
    @Test
    @Timeout(60)
    public void verifiesShipmentAccessAndRejectsAlteredPermissions() throws NoSuchAlgorithmException {
        KeyPair signingKey = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Instant issuedAt = Instant.parse("2030-04-02T10:00:00Z");
        Date expiresAt = Date.from(issuedAt.plusSeconds(900));
        Map<String, Object> authorization = Map.of(
                "tenant", "north-warehouse",
                "permissions", List.of("shipment:read"),
                "displayName", "Zo\u00eb's dispatch team");
        JacksonSerializer<Map<String, ?>> serializer = new JacksonSerializer<>();
        String accessToken = Jwts.builder()
                .header().keyId("shipment-key-1").and()
                .issuer("warehouse-identity")
                .subject("dispatch-service")
                .audience().add("shipment-api").and()
                .issuedAt(Date.from(issuedAt))
                .expiration(expiresAt)
                .claim("authorization", authorization)
                .json(serializer)
                .signWith(signingKey.getPrivate(), Jwts.SIG.EdDSA)
                .compact();

        JwtParser shipmentApi = Jwts.parser()
                .verifyWith(signingKey.getPublic())
                .b64Url(Base64.getUrlDecoder()::wrap)
                .requireIssuer("warehouse-identity")
                .requireAudience("shipment-api")
                .clock(() -> Date.from(issuedAt.plusSeconds(60)))
                .json(new JacksonDeserializer<>())
                .build();
        Jws<Claims> verifiedToken = shipmentApi.parseSignedClaims(accessToken);
        Claims identity = verifiedToken.getPayload();
        Map<?, ?> permissions = identity.get("authorization", Map.class);

        assertEquals("shipment-key-1", verifiedToken.getHeader().getKeyId());
        assertEquals("EdDSA", verifiedToken.getHeader().getAlgorithm());
        assertEquals("dispatch-service", identity.getSubject());
        assertEquals(Date.from(issuedAt), identity.getIssuedAt());
        assertEquals(expiresAt, identity.getExpiration());
        assertEquals(authorization, permissions);
        assertTrue(((List<?>) permissions.get("permissions")).contains("shipment:read"));

        String[] segments = accessToken.split("\\.");
        String originalJson = new String(Base64.getUrlDecoder().decode(segments[1]), StandardCharsets.UTF_8);
        String escalatedJson = originalJson.replace("shipment:read", "shipment:write");
        String alteredPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(escalatedJson.getBytes(StandardCharsets.UTF_8));
        String forgedToken = segments[0] + "." + alteredPayload + "." + segments[2];

        assertThrows(SignatureException.class, () -> shipmentApi.parseSignedClaims(forgedToken));
    }
}
