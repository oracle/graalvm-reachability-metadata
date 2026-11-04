/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_nimbusds_nimbus_jose_jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises symmetric MAC signing and verification for a webhook consumer.
public class MacWebhookVerificationTest {
    @Test
    @Timeout(60)
    public void verifiesWebhookUsingSharedSecretJwk() throws Exception {
        byte[] sharedSecret = "webhook-secret-for-orders-42-with-256-bits".getBytes(StandardCharsets.UTF_8);
        OctetSequenceKey verificationKey = new OctetSequenceKey.Builder(sharedSecret)
                .algorithm(JWSAlgorithm.HS256)
                .keyID("orders-webhook-secret")
                .build();

        JWTClaimsSet webhookClaims = new JWTClaimsSet.Builder()
                .issuer("https://orders.example.invalid")
                .subject("order-42")
                .claim("event", "order.paid")
                .claim("amount", 1250)
                .build();
        SignedJWT webhook = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256)
                        .keyID(verificationKey.getKeyID())
                        .build(),
                webhookClaims);
        webhook.sign(new MACSigner(verificationKey));

        SignedJWT receivedWebhook = SignedJWT.parse(webhook.serialize());

        assertTrue(receivedWebhook.verify(new MACVerifier(verificationKey)));
        assertEquals("orders-webhook-secret", receivedWebhook.getHeader().getKeyID());
        assertEquals("order-42", receivedWebhook.getJWTClaimsSet().getSubject());
        assertEquals("order.paid", receivedWebhook.getJWTClaimsSet().getStringClaim("event"));
        assertEquals(1250, receivedWebhook.getJWTClaimsSet().getIntegerClaim("amount"));
    }
}
