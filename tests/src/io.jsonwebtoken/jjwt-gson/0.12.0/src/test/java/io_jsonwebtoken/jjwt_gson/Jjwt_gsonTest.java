/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_jsonwebtoken.jjwt_gson;

import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.CompressionCodecs;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class Jjwt_gsonTest {
    @Test
    void testSignedJWTs() {
        SecretKey firstKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        String secretString = Encoders.BASE64.encode(firstKey.getEncoded());
        SecretKey secondKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretString));
        assertThat(Jwts.parser().verifyWith(firstKey).build().parseSignedClaims(
                Jwts.builder().setSubject("Joe").signWith(firstKey).compact()
        ).getPayload().getSubject()).isEqualTo("Joe");
        assertThat(Jwts.parser().verifyWith(secondKey).build().parseSignedClaims(
                Jwts.builder().setSubject("Joe").signWith(secondKey).compact()
        ).getPayload().getSubject()).isEqualTo("Joe");
    }

    @Test
    void testCreatingAJWS() {
        Date firstDate = new Date();
        Date secondDate = new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000L);
        String uuidString = UUID.randomUUID().toString();
        SecretKey firstKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        String firstCompactJws = Jwts.builder()
                .setSubject("Joe")
                .setHeaderParam("kid", "myKeyId")
                .setIssuer("Aaron")
                .setAudience("Abel")
                .setExpiration(secondDate)
                .setNotBefore(firstDate)
                .setIssuedAt(firstDate)
                .setId(uuidString)
                .claim("exampleClaim", "Adam")
                .signWith(firstKey)
                .compressWith(CompressionCodecs.GZIP)
                .compact();
        JwtParserBuilder jwtParserBuilder = Jwts.parser().clockSkewSeconds(3 * 60).verifyWith(firstKey);
        assertThat(jwtParserBuilder.build().parseSignedClaims(firstCompactJws).getPayload().getSubject()).isEqualTo("Joe");
        assertDoesNotThrow(() -> jwtParserBuilder.requireSubject("Joe").build().parseSignedClaims(firstCompactJws));
        assertDoesNotThrow(() -> jwtParserBuilder.requireIssuer("Aaron").build().parseSignedClaims(firstCompactJws));
        assertDoesNotThrow(() -> jwtParserBuilder.requireAudience("Abel").build().parseSignedClaims(firstCompactJws));

        // In JJWT 0.12.0 the Gson extension validates these claims correctly
        assertDoesNotThrow(() -> jwtParserBuilder.requireExpiration(secondDate).build().parseSignedClaims(firstCompactJws));
        assertDoesNotThrow(() -> jwtParserBuilder.requireNotBefore(firstDate).build().parseSignedClaims(firstCompactJws));
        assertDoesNotThrow(() -> jwtParserBuilder.requireIssuedAt(firstDate).build().parseSignedClaims(firstCompactJws));
        assertDoesNotThrow(() -> jwtParserBuilder.requireId(uuidString).build().parseSignedClaims(firstCompactJws));
        assertDoesNotThrow(() -> jwtParserBuilder.require("exampleClaim", "Adam").build().parseSignedClaims(firstCompactJws));
    }

    @Test
    void testCompression() {
        SecretKey firstKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        assertThat(Jwts.parser().verifyWith(firstKey).build().parseSignedClaims(
                Jwts.builder().setSubject("Joe").signWith(firstKey).compressWith(CompressionCodecs.DEFLATE).compact()
        ).getPayload().getSubject()).isEqualTo("Joe");
        assertThat(Jwts.parser().verifyWith(firstKey).build().parseSignedClaims(
                Jwts.builder().setSubject("Joe").signWith(firstKey).compressWith(CompressionCodecs.GZIP).compact()
        ).getPayload().getSubject()).isEqualTo("Joe");
    }

    @Test
    void testSignatureAlgorithms() {
        Stream.of(SignatureAlgorithm.HS256, SignatureAlgorithm.HS384, SignatureAlgorithm.HS512)
                .map(Keys::secretKeyFor)
                .forEach(secretKey -> assertThat(Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(
                        Jwts.builder().setSubject("Joe").signWith(secretKey).compact()
                ).getPayload().getSubject()).isEqualTo("Joe"));
        Stream.of(SignatureAlgorithm.ES256, SignatureAlgorithm.ES384, SignatureAlgorithm.ES512,
                        SignatureAlgorithm.RS256, SignatureAlgorithm.RS384, SignatureAlgorithm.RS512,
                        SignatureAlgorithm.PS256, SignatureAlgorithm.PS384, SignatureAlgorithm.PS512)
                .map(Keys::keyPairFor)
                .forEach(keyPair -> assertThat(Jwts.parser().verifyWith(keyPair.getPublic()).build().parseSignedClaims(
                        Jwts.builder().setSubject("Joe").signWith(keyPair.getPrivate()).compact()
                ).getPayload().getSubject()).isEqualTo("Joe"));
    }
}
