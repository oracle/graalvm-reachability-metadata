/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_nimbusds_nimbus_jose_jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises elliptic-curve JWK conversion and signed message verification for a payment API.
public class EcJwkSigningTest {
    @Test
    @Timeout(60)
    public void signsAndVerifiesMessageWithPublishedJwk() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        ECPrivateKey privateKey = (ECPrivateKey) keyPair.getPrivate();
        ECPublicKey publicKey = (ECPublicKey) keyPair.getPublic();

        ECKey signingKey = new ECKey.Builder(ECKey.Curve.P_256, publicKey)
                .privateKey(privateKey)
                .algorithm(JWSAlgorithm.ES256)
                .keyIDFromThumbprint()
                .build();
        ECKey publishedKey = signingKey.toPublicJWK();
        ECKey parsedPublishedKey = ECKey.parse(publishedKey.toJSONString());

        assertFalse(parsedPublishedKey.isPrivate());
        assertEquals(signingKey.computeThumbprint(), parsedPublishedKey.computeThumbprint());
        assertEquals(signingKey.getKeyID(), parsedPublishedKey.getKeyID());
        assertEquals(publicKey, parsedPublishedKey.toECPublicKey());

        JWSObject signedPayment = new JWSObject(
                new JWSHeader.Builder(JWSAlgorithm.ES256)
                        .keyID(parsedPublishedKey.getKeyID())
                        .build(),
                new Payload("payment=pay-42;amount=1250"));
        signedPayment.sign(new ECDSASigner(signingKey));

        JWSObject receivedPayment = JWSObject.parse(signedPayment.serialize());
        assertTrue(receivedPayment.verify(new ECDSAVerifier(parsedPublishedKey)));
        assertEquals("payment=pay-42;amount=1250", receivedPayment.getPayload().toString());
        assertEquals(parsedPublishedKey.getKeyID(), receivedPayment.getHeader().getKeyID());
    }
}
