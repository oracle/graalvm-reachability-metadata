/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_jsonwebtoken.jjwt_gson;

import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.CompressionAlgorithm;
import org.junit.jupiter.api.Test;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class Jjwt_gsonTest {
    private static final CompressionAlgorithm DEFLATE = new DeflateCompressionAlgorithm();
    private static final CompressionAlgorithm GZIP = new GzipCompressionAlgorithm();

    @Test
    void testSignedJWTs() throws GeneralSecurityException {
        SecretKey firstKey = generateSecretKey("HmacSHA256");
        String secretString = Base64.getEncoder().encodeToString(firstKey.getEncoded());
        SecretKey secondKey = new SecretKeySpec(Base64.getDecoder().decode(secretString), "HmacSHA256");
        assertThat(parseSubject(Jwts.builder().subject("Joe").signWith(firstKey).compact(), firstKey)).isEqualTo("Joe");
        assertThat(parseSubject(Jwts.builder().subject("Joe").signWith(secondKey).compact(), secondKey)).isEqualTo("Joe");
    }

    @Test
    void testCreatingAJWS() throws GeneralSecurityException {
        Date firstDate = new Date();
        Date secondDate = new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000L);
        String uuidString = UUID.randomUUID().toString();
        SecretKey firstKey = generateSecretKey("HmacSHA256");
        String firstCompactJws = Jwts.builder()
                .subject("Joe")
                .header().add("kid", "myKeyId").and()
                .issuer("Aaron")
                .audience().add("Abel").and()
                .expiration(secondDate)
                .notBefore(firstDate)
                .issuedAt(firstDate)
                .id(uuidString)
                .claim("exampleClaim", "Adam")
                .signWith(firstKey)
                .compressWith(GZIP)
                .compact();
        JwtParserBuilder jwtParserBuilder = Jwts.parser().clockSkewSeconds(3 * 60).verifyWith(firstKey);
        assertThat(jwtParserBuilder.build().parseSignedClaims(firstCompactJws).getPayload().getSubject()).isEqualTo("Joe");
        assertDoesNotThrow(() -> jwtParserBuilder.requireSubject("Joe").build().parseSignedClaims(firstCompactJws));
        assertDoesNotThrow(() -> jwtParserBuilder.requireIssuer("Aaron").build().parseSignedClaims(firstCompactJws));
        assertDoesNotThrow(() -> jwtParserBuilder.requireAudience("Abel").build().parseSignedClaims(firstCompactJws));
        assertDoesNotThrow(() -> jwtParserBuilder.requireExpiration(secondDate).build().parseSignedClaims(firstCompactJws));
        assertDoesNotThrow(() -> jwtParserBuilder.requireNotBefore(firstDate).build().parseSignedClaims(firstCompactJws));
        assertDoesNotThrow(() -> jwtParserBuilder.requireIssuedAt(firstDate).build().parseSignedClaims(firstCompactJws));
        assertDoesNotThrow(() -> jwtParserBuilder.requireId(uuidString).build().parseSignedClaims(firstCompactJws));
        assertDoesNotThrow(() -> jwtParserBuilder.require("exampleClaim", "Adam").build().parseSignedClaims(firstCompactJws));
    }

    @Test
    void testCompression() throws GeneralSecurityException {
        SecretKey firstKey = generateSecretKey("HmacSHA256");
        assertThat(parseSubject(Jwts.builder().subject("Joe").signWith(firstKey).compressWith(DEFLATE).compact(), firstKey))
                .isEqualTo("Joe");
        assertThat(parseSubject(Jwts.builder().subject("Joe").signWith(firstKey).compressWith(GZIP).compact(), firstKey))
                .isEqualTo("Joe");
    }

    @Test
    void testSignatureAlgorithms() throws GeneralSecurityException {
        assertSignedSubject("HS256", generateSecretKey("HmacSHA256"));
        assertSignedSubject("HS384", generateSecretKey("HmacSHA384"));
        assertSignedSubject("HS512", generateSecretKey("HmacSHA512"));

        assertSignedSubject("ES256", generateEcKeyPair("secp256r1"));
        assertSignedSubject("ES384", generateEcKeyPair("secp384r1"));
        assertSignedSubject("ES512", generateEcKeyPair("secp521r1"));

        assertSignedSubject("RS256", generateKeyPair("RSA", 2048));
        assertSignedSubject("RS384", generateKeyPair("RSA", 3072));
        assertSignedSubject("RS512", generateKeyPair("RSA", 4096));

        assertSignedSubject("PS256", generateKeyPair("RSASSA-PSS", 2048));
        assertSignedSubject("PS384", generateKeyPair("RSASSA-PSS", 3072));
        assertSignedSubject("PS512", generateKeyPair("RSASSA-PSS", 4096));
    }

    private static SecretKey generateSecretKey(String algorithm) throws GeneralSecurityException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(algorithm);
        return keyGenerator.generateKey();
    }

    private static KeyPair generateKeyPair(String algorithm, int keySize) throws GeneralSecurityException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(algorithm);
        keyPairGenerator.initialize(keySize);
        return keyPairGenerator.generateKeyPair();
    }

    private static KeyPair generateEcKeyPair(String curve) throws GeneralSecurityException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(new ECGenParameterSpec(curve));
        return keyPairGenerator.generateKeyPair();
    }

    private static void assertSignedSubject(String expectedAlgorithm, SecretKey secretKey) {
        String compactJws = Jwts.builder().subject("Joe").signWith(secretKey).compact();
        assertThat(readHeader(compactJws)).contains("\"alg\":\"" + expectedAlgorithm + "\"");
        assertThat(parseSubject(compactJws, secretKey)).isEqualTo("Joe");
    }

    private static void assertSignedSubject(String expectedAlgorithm, KeyPair keyPair) {
        String compactJws = Jwts.builder().subject("Joe").signWith(keyPair.getPrivate()).compact();
        assertThat(readHeader(compactJws)).contains("\"alg\":\"" + expectedAlgorithm + "\"");
        assertThat(parseSubject(compactJws, keyPair.getPublic())).isEqualTo("Joe");
    }

    private static String readHeader(String compactJws) {
        String encodedHeader = compactJws.substring(0, compactJws.indexOf('.'));
        return new String(Base64.getUrlDecoder().decode(encodedHeader), StandardCharsets.UTF_8);
    }

    private static String parseSubject(String compactJws, SecretKey secretKey) {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(compactJws).getPayload().getSubject();
    }

    private static String parseSubject(String compactJws, PublicKey publicKey) {
        return Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(compactJws).getPayload().getSubject();
    }

    private static final class DeflateCompressionAlgorithm implements CompressionAlgorithm {
        @Override
        public String getId() {
            return "DEF";
        }

        @Override
        public OutputStream compress(OutputStream outputStream) {
            return new DeflaterOutputStream(outputStream);
        }

        @Override
        public InputStream decompress(InputStream inputStream) {
            return new InflaterInputStream(inputStream);
        }
    }

    private static final class GzipCompressionAlgorithm implements CompressionAlgorithm {
        @Override
        public String getId() {
            return "GZIP";
        }

        @Override
        public OutputStream compress(OutputStream outputStream) {
            try {
                return new GZIPOutputStream(outputStream);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public InputStream decompress(InputStream inputStream) {
            try {
                return new GZIPInputStream(inputStream);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
