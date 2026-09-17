/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_security.micronaut_security_jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.micronaut.security.token.jwt.signature.jwks.DefaultJwkValidator;
import io.micronaut.security.token.jwt.signature.jwks.JWKSetJwksSignature;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JWKSetJwksSignatureTest {
    private static final String KEY_ID = "active-signing-key";

    @Test
    void verifiesTokensWithTheMatchingJsonWebKey() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        RSAKey publicJwk = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .keyID(KEY_ID)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        JWKSetJwksSignature signature = new JWKSetJwksSignature(
                new DefaultJwkValidator(), new JWKSet(publicJwk));

        SignedJWT matchingToken = signedToken(KEY_ID, (RSAPrivateKey) keyPair.getPrivate());
        SignedJWT unknownKeyToken = signedToken("retired-signing-key", (RSAPrivateKey) keyPair.getPrivate());

        assertThat(signature.supports(JWSAlgorithm.RS256)).isTrue();
        assertThat(signature.supports(JWSAlgorithm.ES256)).isFalse();
        assertThat(signature.verify(matchingToken)).isTrue();
        assertThat(matchingToken.getJWTClaimsSet().getSubject()).isEqualTo("jwks-user");
        assertThat(signature.verify(unknownKeyToken)).isFalse();
    }

    private static SignedJWT signedToken(String keyId, RSAPrivateKey privateKey) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("jwks-user")
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .build();
        SignedJWT token = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId).build(), claims);
        token.sign(new RSASSASigner(privateKey));
        return token;
    }
}
