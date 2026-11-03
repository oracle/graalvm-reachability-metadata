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
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises JWK set selection for a service rotating its JWT signing keys.
public class JwkSetSelectionTest {
    @Test
    @Timeout(60)
    public void selectsCurrentSigningKeyFromPublishedSet() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair retiredPair = keyPairGenerator.generateKeyPair();
        KeyPair currentPair = keyPairGenerator.generateKeyPair();

        RSAKey retiredKey = rsaSigningKey(retiredPair, "retired-signing-key");
        RSAKey currentKey = rsaSigningKey(currentPair, "current-signing-key");
        String publishedSet = new JWKSet(List.of(retiredKey.toPublicJWK(), currentKey.toPublicJWK()))
                .toJSONObject()
                .toString();

        JWKSet receivedSet = JWKSet.parse(publishedSet);
        JWKSelector currentKeySelector = new JWKSelector(new JWKMatcher.Builder()
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyID("current-signing-key")
                .publicOnly(true)
                .build());
        List<JWK> matchingKeys = currentKeySelector.select(receivedSet);

        assertEquals(1, matchingKeys.size());
        RSAKey selectedKey = (RSAKey) matchingKeys.get(0);
        assertEquals("current-signing-key", selectedKey.getKeyID());
        assertEquals(JWSAlgorithm.RS256, selectedKey.getAlgorithm());

        SignedJWT token = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(selectedKey.getKeyID())
                        .build(),
                new JWTClaimsSet.Builder()
                        .issuer("https://accounts.example.invalid")
                        .subject("customer-42")
                        .claim("scope", "orders:read")
                        .build());
        token.sign(new RSASSASigner(currentPair.getPrivate()));

        SignedJWT receivedToken = SignedJWT.parse(token.serialize());
        assertTrue(receivedToken.verify(new RSASSAVerifier(selectedKey)));
        assertEquals("customer-42", receivedToken.getJWTClaimsSet().getSubject());
        assertEquals("orders:read", receivedToken.getJWTClaimsSet().getStringClaim("scope"));
    }

    private static RSAKey rsaSigningKey(KeyPair keyPair, String keyID) {
        return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyID(keyID)
                .build();
    }
}
