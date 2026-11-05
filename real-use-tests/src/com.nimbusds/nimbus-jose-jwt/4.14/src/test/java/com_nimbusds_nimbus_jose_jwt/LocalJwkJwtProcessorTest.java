/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_nimbusds_nimbus_jose_jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.JWTClaimsVerifier;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises local JWK source resolution and JWT processing for an API authentication boundary.
public class LocalJwkJwtProcessorTest {
    @Test
    @Timeout(60)
    public void processesSignedTokenWithLocallyConfiguredVerificationKey() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        RSAKey signingKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyID("orders-signing-key")
                .build();

        JWKSet configuredKeys = JWKSet.parse(
                new JWKSet(signingKey.toPublicJWK()).toString());
        ImmutableJWKSet<SecurityContext> keySource = new ImmutableJWKSet<>(configuredKeys);
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(
                JWSAlgorithm.RS256, keySource));
        processor.setJWTClaimsVerifier(new TenantClaimsVerifier(
                "https://accounts.example.invalid", "orders-api"));

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("https://accounts.example.invalid")
                .subject("customer-42")
                .audience(Collections.singletonList("orders-api"))
                .claim("scope", "orders:read")
                .build();
        SignedJWT token = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID("orders-signing-key")
                        .build(),
                claims);
        token.sign(new RSASSASigner(signingKey));

        JWTClaimsSet result = processor.process(token.serialize(), null);

        assertEquals("customer-42", result.getSubject());
        assertEquals("orders:read", result.getStringClaim("scope"));
    }

    private static final class TenantClaimsVerifier implements JWTClaimsVerifier {
        private final String expectedIssuer;
        private final String expectedAudience;

        private TenantClaimsVerifier(String expectedIssuer, String expectedAudience) {
            this.expectedIssuer = expectedIssuer;
            this.expectedAudience = expectedAudience;
        }

        @Override
        public void verify(JWTClaimsSet claims) throws BadJWTException {
            if (!expectedIssuer.equals(claims.getIssuer())
                    || !claims.getAudience().contains(expectedAudience)) {
                throw new BadJWTException("Token is not intended for this API");
            }
        }
    }
}
