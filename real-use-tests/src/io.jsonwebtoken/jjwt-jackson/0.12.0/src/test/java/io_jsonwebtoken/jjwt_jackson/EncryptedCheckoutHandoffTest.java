/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_jsonwebtoken.jjwt_jackson;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwe;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.jackson.io.JacksonDeserializer;
import io.jsonwebtoken.jackson.io.JacksonSerializer;
import io.jsonwebtoken.security.SecurityException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises Jackson's encrypted JWT JSON integration for confidential checkout handoffs.
public class EncryptedCheckoutHandoffTest {
    @Test
    @Timeout(60)
    public void restoresPrivateCheckoutDetailsOnlyWithTheRecipientKey() {
        SecretKey recipientKey = Jwts.ENC.A256GCM.key().build();
        SecretKey unrelatedKey = Jwts.ENC.A256GCM.key().build();
        Instant issuedAt = Instant.parse("2030-06-15T12:00:00Z");
        Date expiration = Date.from(issuedAt.plusSeconds(300));
        Map<String, Object> checkout = Map.of(
                "order_id", "order-704",
                "contact", Map.of("name", "Zo\u00eb Lee", "email", "zoe@example.test"),
                "items", List.of(Map.of("sku", "NOTEBOOK-8", "quantity", 2)),
                "delivery_note", "Ring bell\nLeave with reception");
        String handoff = Jwts.builder()
                .header().keyId("checkout-recipient-1").and()
                .issuer("storefront")
                .subject("customer-42")
                .audience().add("fulfillment").and()
                .issuedAt(Date.from(issuedAt))
                .expiration(expiration)
                .claim("checkout", checkout)
                .json(new JacksonSerializer<>())
                .encryptWith(recipientKey, Jwts.ENC.A256GCM)
                .compact();

        JwtParser recipient = Jwts.parser()
                .decryptWith(recipientKey)
                .b64Url(Base64.getUrlDecoder()::wrap)
                .requireIssuer("storefront")
                .requireAudience("fulfillment")
                .clock(() -> Date.from(issuedAt.plusSeconds(30)))
                .json(new JacksonDeserializer<>())
                .build();
        Jwe<Claims> received = recipient.parseEncryptedClaims(handoff);
        Claims identity = received.getPayload();
        Map<?, ?> restoredCheckout = identity.get("checkout", Map.class);

        assertEquals("checkout-recipient-1", received.getHeader().getKeyId());
        assertEquals("dir", received.getHeader().getAlgorithm());
        assertEquals("A256GCM", received.getHeader().getEncryptionAlgorithm());
        assertEquals("customer-42", identity.getSubject());
        assertEquals(Date.from(issuedAt), identity.getIssuedAt());
        assertEquals(expiration, identity.getExpiration());
        assertEquals(checkout, restoredCheckout);
        assertEquals("zoe@example.test", ((Map<?, ?>) restoredCheckout.get("contact")).get("email"));
        assertEquals("Ring bell\nLeave with reception", restoredCheckout.get("delivery_note"));

        JwtParser unauthorizedRecipient = Jwts.parser()
                .decryptWith(unrelatedKey)
                .b64Url(Base64.getUrlDecoder()::wrap)
                .clock(() -> Date.from(issuedAt.plusSeconds(30)))
                .json(new JacksonDeserializer<>())
                .build();
        assertThrows(SecurityException.class, () -> unauthorizedRecipient.parseEncryptedClaims(handoff));
    }
}
