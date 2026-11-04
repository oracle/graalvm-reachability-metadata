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
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.bc.BouncyCastleProviderSingleton;
import com.nimbusds.jose.jwk.ECKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises Nimbus JOSE's Bouncy Castle provider integration for an ECDSA-signed webhook.
public class BouncyCastleEcdsaSigningTest {
    @Test
    @Timeout(60)
    public void signsAndVerifiesWebhookWithBouncyCastleProvider() throws Exception {
        Provider bouncyCastle = BouncyCastleProviderSingleton.getInstance();
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        ECKey signingKey = new ECKey.Builder(
                ECKey.Curve.P_256,
                (ECPublicKey) keyPair.getPublic())
                .privateKey((ECPrivateKey) keyPair.getPrivate())
                .algorithm(JWSAlgorithm.ES256)
                .keyID("webhook-signing-key")
                .build();

        JWSObject webhook = new JWSObject(
                new JWSHeader.Builder(JWSAlgorithm.ES256)
                        .keyID(signingKey.getKeyID())
                        .build(),
                new Payload("order.paid:order-42"));
        ECDSASigner signer = new ECDSASigner(signingKey);
        signer.getJCAContext().setProvider(bouncyCastle);
        webhook.sign(signer);

        JWSObject receivedWebhook = JWSObject.parse(webhook.serialize());
        ECDSAVerifier verifier = new ECDSAVerifier(signingKey.toECPublicKey());
        verifier.getJCAContext().setProvider(bouncyCastle);

        assertTrue(receivedWebhook.verify(verifier));
        assertEquals("webhook-signing-key", receivedWebhook.getHeader().getKeyID());
        assertEquals("order.paid:order-42", receivedWebhook.getPayload().toString());
    }
}
