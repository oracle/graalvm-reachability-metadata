/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_oauth2_jose;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.DPoPProofContext;
import org.springframework.security.oauth2.jwt.DPoPProofJwtDecoderFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

public class Spring_security_oauth2_joseTest {
    private static final String ISSUER = "https://issuer.example.test";

    @Test
    void hmacEncoderAndDecoderRoundTripJwtHeadersAndClaims() {
        SecretKey secretKey = hmacSecretKey();
        JwtEncoder encoder = NimbusJwtEncoder.withSecretKey(secretKey).algorithm(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = claimsFor("customer-42");
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();

        Jwt encoded = encoder.encode(JwtEncoderParameters.from(header, claims));

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        Jwt decoded = decoder.decode(encoded.getTokenValue());

        assertThat(encoded.getTokenValue()).contains(".");
        assertThat(decoded.getHeaders()).containsEntry("alg", "HS256").containsEntry("typ", "JWT");
        assertThat((String) decoded.getClaim("iss")).isEqualTo(ISSUER);
        assertThat(decoded.getSubject()).isEqualTo("customer-42");
        assertThat(decoded.getAudience()).containsExactly("orders-api", "billing-api");
        assertThat(decoded.getClaims()).containsEntry("roles", List.of("ORDER_READ", "BILLING_READ"));
        assertThat((Boolean) decoded.getClaim("active")).isTrue();
        assertThat(decoded.getExpiresAt()).isAfter(decoded.getIssuedAt());
    }

    @Test
    void issuerValidatorRejectsOtherwiseValidSignedTokenFromAnotherIssuer() {
        SecretKey secretKey = hmacSecretKey();
        JwtEncoder encoder = NimbusJwtEncoder.withSecretKey(secretKey).algorithm(MacAlgorithm.HS256).build();
        Jwt encoded = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                claimsFor("customer-17")));
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("https://other-issuer.example.test"));

        assertThatThrownBy(() -> decoder.decode(encoded.getTokenValue()))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void mappedClaimSetConverterCustomizesAndRemovesClaims() {
        Converter<Object, Object> rolesConverter = (roles) -> ((List<?>) roles).stream().map(Object::toString)
                .map(String::toLowerCase).toList();
        Converter<Object, Object> claimRemovalConverter = (claim) -> null;
        MappedJwtClaimSetConverter converter = MappedJwtClaimSetConverter.withDefaults(Map.of("roles", rolesConverter,
                "temporary", claimRemovalConverter));

        Map<String, Object> convertedClaims = converter.convert(Map.of("sub", "customer-42", "aud", "orders-api",
                "roles", List.of("ORDER_READ", "BILLING_READ"), "temporary", "one-time-value"));

        assertThat(convertedClaims).containsEntry("sub", "customer-42").containsEntry("aud", List.of("orders-api"))
                .containsEntry("roles", List.of("order_read", "billing_read")).doesNotContainKey("temporary");
    }

    @Test
    void dpopProofDecoderFactoryValidatesSignedProofBoundToRequest() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        JwtEncoder encoder = NimbusJwtEncoder.withKeyPair(publicKey, (RSAPrivateKey) keyPair.getPrivate())
                .algorithm(SignatureAlgorithm.RS256).build();
        Instant issuedAt = Instant.now();
        Jwt proof = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).type("dpop+jwt").jwk(publicRsaJwk(publicKey)).build(),
                JwtClaimsSet.builder().issuedAt(issuedAt).id(UUID.randomUUID().toString()).claim("htm", "POST")
                        .claim("htu", "https://resource.example.test/orders").build()));
        DPoPProofContext context = DPoPProofContext.withDPoPProof(proof.getTokenValue()).method("POST")
                .targetUri("https://resource.example.test/orders").build();

        Jwt decoded = new DPoPProofJwtDecoderFactory().createDecoder(context).decode(context.getDPoPProof());

        assertThat(decoded.getHeaders()).containsEntry("typ", "dpop+jwt").containsKey("jwk");
        assertThat(decoded.getId()).isEqualTo(proof.getId());
        assertThat(decoded.getClaimAsString("htm")).isEqualTo("POST");
        assertThat(decoded.getClaimAsString("htu")).isEqualTo("https://resource.example.test/orders");
    }

    @Test
    void rsaKeyPairEncoderAndPublicKeyDecoderRoundTripJwt() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        JwtEncoder encoder = NimbusJwtEncoder.withKeyPair((RSAPublicKey) keyPair.getPublic(),
                (RSAPrivateKey) keyPair.getPrivate()).algorithm(SignatureAlgorithm.RS256).build();
        JwtClaimsSet claims = claimsFor("service-account");

        Jwt encoded = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims));

        JwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic())
                .signatureAlgorithm(SignatureAlgorithm.RS256).build();
        Jwt decoded = decoder.decode(encoded.getTokenValue());

        assertThat(decoded.getHeaders()).containsEntry("alg", "RS256");
        assertThat(decoded.getSubject()).isEqualTo("service-account");
        assertThat(decoded.getClaims()).containsEntry("roles", List.of("ORDER_READ", "BILLING_READ"));
    }

    private JwtClaimsSet claimsFor(String subject) {
        Instant issuedAt = Instant.now().minusSeconds(5);
        return JwtClaimsSet.builder().issuer(ISSUER).subject(subject).audience(List.of("orders-api", "billing-api"))
                .issuedAt(issuedAt).expiresAt(issuedAt.plusSeconds(300)).id("jwt-" + subject)
                .claim("roles", List.of("ORDER_READ", "BILLING_READ")).claim("active", true).build();
    }

    private Map<String, Object> publicRsaJwk(RSAPublicKey publicKey) {
        return Map.of("kty", "RSA", "n", base64UrlUnsigned(publicKey.getModulus()), "e",
                base64UrlUnsigned(publicKey.getPublicExponent()));
    }

    private String base64UrlUnsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        int offset = bytes.length > 1 && bytes[0] == 0 ? 1 : 0;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOfRange(bytes, offset, bytes.length));
    }

    private SecretKey hmacSecretKey() {
        byte[] keyMaterial = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
        return new SecretKeySpec(keyMaterial, "HmacSHA256");
    }
}
