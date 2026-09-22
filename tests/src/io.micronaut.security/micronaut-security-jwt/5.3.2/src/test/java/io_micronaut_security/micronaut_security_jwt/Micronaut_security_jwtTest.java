/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_security.micronaut_security_jwt;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.micronaut.context.ApplicationContext;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.token.jwt.encryption.secret.SecretEncryption;
import io.micronaut.security.token.jwt.encryption.secret.SecretEncryptionConfiguration;
import io.micronaut.security.token.jwt.generator.JwtTokenGenerator;
import io.micronaut.security.token.jwt.generator.SignedRefreshTokenGenerator;
import io.micronaut.security.token.jwt.signature.ec.ECSignatureGenerator;
import io.micronaut.security.token.jwt.signature.ec.ECSignatureGeneratorConfiguration;
import io.micronaut.security.token.jwt.signature.rsa.RSASignatureGenerator;
import io.micronaut.security.token.jwt.signature.rsa.RSASignatureGeneratorConfiguration;
import io.micronaut.security.token.jwt.validator.JsonWebTokenValidator;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Micronaut_security_jwtTest {
    private static final String SIGNING_SECRET = "0123456789abcdef0123456789abcdef";
    private static final String ISSUER = "https://issuer.example";
    private static final String AUDIENCE = "native-client";

    @Test
    void generatesAndValidatesConfiguredSecretSignedToken() throws Exception {
        Map<String, Object> configuration = new HashMap<>();
        configuration.put("micronaut.security.token.jwt.signatures.secret.generator.secret", SIGNING_SECRET);
        configuration.put("micronaut.security.token.jwt.claims-validators.issuer", ISSUER);
        configuration.put("micronaut.security.token.jwt.claims-validators.audience", AUDIENCE);

        try (ApplicationContext context = ApplicationContext.run(configuration)) {
            JwtTokenGenerator generator = context.getBean(JwtTokenGenerator.class);
            JsonWebTokenValidator<JWT, Object> validator = jwtValidator(context);
            Map<String, Object> claims = validClaims("alice", ISSUER);

            String token = generator.generateToken(claims).orElseThrow();
            Optional<JWT> validated = validator.validate(token, null);

            assertThat(validated).isPresent();
            assertThat(validated.orElseThrow().getJWTClaimsSet().getSubject()).isEqualTo("alice");
            assertThat(validated.orElseThrow().getJWTClaimsSet().getAudience()).containsExactly(AUDIENCE);
            assertThat(validator.validate(tamperSignature(token), null)).isEmpty();

            String wrongIssuerToken = generator.generateToken(validClaims("alice", "https://other.example"))
                    .orElseThrow();
            assertThat(validator.validate(wrongIssuerToken, null)).isEmpty();
        }
    }

    @Test
    void generatesAndValidatesSignedRefreshToken() {
        Map<String, Object> configuration = Map.of(
                "micronaut.security.token.jwt.generator.refresh-token.secret", SIGNING_SECRET);

        try (ApplicationContext context = ApplicationContext.run(configuration)) {
            SignedRefreshTokenGenerator generator = context.getBean(SignedRefreshTokenGenerator.class);
            Authentication authentication = Authentication.build("refresh-user", Map.of("tenant", "reports"));
            String refreshKey = generator.createKey(authentication);
            String token = generator.generate(authentication, refreshKey).orElseThrow();

            assertThat(refreshKey).isNotBlank();
            assertThat(generator.validate(token)).contains(refreshKey);
            assertThat(generator.validate(tamperSignature(token))).isEmpty();
        }
    }

    @Test
    void encryptsAndDecryptsClaimsWithDirectSecretEncryption() throws Exception {
        SecretEncryptionConfiguration configuration = new SecretEncryptionConfiguration("generator");
        configuration.setSecret(SIGNING_SECRET);
        configuration.setJweAlgorithm(JWEAlgorithm.DIR);
        configuration.setEncryptionMethod(EncryptionMethod.A256GCM);
        SecretEncryption encryption = new SecretEncryption(configuration);
        JwtTokenGenerator generator = new JwtTokenGenerator(null, encryption, null);

        String token = generator.generateToken(Map.of("sub", "encrypted-user", "scope", "reports.read"))
                .orElseThrow();
        EncryptedJWT encryptedJWT = EncryptedJWT.parse(token);
        encryption.decrypt(encryptedJWT);

        assertThat(encryptedJWT.getHeader().getAlgorithm()).isEqualTo(JWEAlgorithm.DIR);
        assertThat(encryptedJWT.getHeader().getEncryptionMethod()).isEqualTo(EncryptionMethod.A256GCM);
        assertThat(encryptedJWT.getJWTClaimsSet().getSubject()).isEqualTo("encrypted-user");
        assertThat(encryptedJWT.getJWTClaimsSet().getStringClaim("scope")).isEqualTo("reports.read");
    }

    @Test
    void signsAndVerifiesWithRsaKeys() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        RsaConfiguration configuration = new RsaConfiguration(keyPair);
        RSASignatureGenerator signature = new RSASignatureGenerator(configuration);

        SignedJWT token = signature.sign(claimsSet("rsa-user"));

        assertThat(token.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(signature.verify(token)).isTrue();
        assertThat(token.getJWTClaimsSet().getSubject()).isEqualTo("rsa-user");
    }

    @Test
    void signsAndVerifiesWithEllipticCurveKeys() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        EcConfiguration configuration = new EcConfiguration(keyPair);
        ECSignatureGenerator signature = new ECSignatureGenerator(configuration);

        SignedJWT token = signature.sign(claimsSet("ec-user"));

        assertThat(token.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
        assertThat(token.getHeader().getKeyID()).isEqualTo("primary-ec-key");
        assertThat(signature.verify(token)).isTrue();
        assertThat(token.getJWTClaimsSet().getSubject()).isEqualTo("ec-user");
    }

    @SuppressWarnings("unchecked")
    private static JsonWebTokenValidator<JWT, Object> jwtValidator(ApplicationContext context) {
        return (JsonWebTokenValidator<JWT, Object>) context.getBean(JsonWebTokenValidator.class);
    }

    private static Map<String, Object> validClaims(String subject, String issuer) {
        Instant now = Instant.now();
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", subject);
        claims.put("iss", issuer);
        claims.put("aud", AUDIENCE);
        claims.put("iat", Date.from(now));
        claims.put("exp", Date.from(now.plusSeconds(300)));
        return claims;
    }

    private static JWTClaimsSet claimsSet(String subject) {
        return new JWTClaimsSet.Builder()
                .subject(subject)
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .build();
    }

    private static String tamperSignature(String token) {
        int signatureStart = token.lastIndexOf('.') + 1;
        char firstSignatureCharacter = token.charAt(signatureStart);
        char replacement = firstSignatureCharacter == 'A' ? 'B' : 'A';
        return token.substring(0, signatureStart) + replacement + token.substring(signatureStart + 1);
    }

    private static final class RsaConfiguration implements RSASignatureGeneratorConfiguration {
        private final RSAPublicKey publicKey;
        private final RSAPrivateKey privateKey;

        private RsaConfiguration(KeyPair keyPair) {
            this.publicKey = (RSAPublicKey) keyPair.getPublic();
            this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
        }

        @Override
        public RSAPublicKey getPublicKey() {
            return publicKey;
        }

        @Override
        public RSAPrivateKey getPrivateKey() {
            return privateKey;
        }

        @Override
        public JWSAlgorithm getJwsAlgorithm() {
            return JWSAlgorithm.RS256;
        }
    }

    private static final class EcConfiguration implements ECSignatureGeneratorConfiguration {
        private final ECPublicKey publicKey;
        private final ECPrivateKey privateKey;

        private EcConfiguration(KeyPair keyPair) {
            this.publicKey = (ECPublicKey) keyPair.getPublic();
            this.privateKey = (ECPrivateKey) keyPair.getPrivate();
        }

        @Override
        public ECPublicKey getPublicKey() {
            return publicKey;
        }

        @Override
        public ECPrivateKey getPrivateKey() {
            return privateKey;
        }

        @Override
        public JWSAlgorithm getJwsAlgorithm() {
            return JWSAlgorithm.ES256;
        }

        @Override
        public Optional<String> getKid() {
            return Optional.of("primary-ec-key");
        }
    }
}
