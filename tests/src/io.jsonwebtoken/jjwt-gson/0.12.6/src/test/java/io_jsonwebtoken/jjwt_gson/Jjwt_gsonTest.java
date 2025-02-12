package io_jsonwebtoken.jjwt_gson;

import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * @author Oguz Kahraman <oguz.kahraman@payten.com> 12/02/2025
 */
class Jjwt_gsonTest {

    @Test
    void testSignedJWTs() {
        SecretKey firstKey = Jwts.SIG.HS256.key().build();
        String secretString = Encoders.BASE64.encode(firstKey.getEncoded());
        SecretKey secondKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretString));
        assertThat(Jwts.parser().verifyWith(firstKey).build().parseSignedClaims(
                Jwts.builder().subject("Joe").signWith(firstKey).compact()
        ).getPayload().getSubject()).isEqualTo("Joe");
        assertThat(Jwts.parser().verifyWith(secondKey).build().parseSignedClaims(
                Jwts.builder().subject("Joe").signWith(secondKey).compact()
        ).getPayload().getSubject()).isEqualTo("Joe");
    }

    @Test
    void testCreatingAJWS() {
        Date firstDate = new Date();
        Date secondDate = new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000L);
        String uuidString = UUID.randomUUID().toString();
        SecretKey firstKey = Jwts.SIG.HS256.key().build();
        String firstCompactJws = Jwts.builder()
                .subject("Joe")
                .header().add("kid", "myKeyId")
                .and()
                .issuer("Aaron")
                .audience().add("Abel").and()
                .expiration(secondDate)
                .notBefore(firstDate)
                .issuedAt(firstDate)
                .id(uuidString)
                .claim("exampleClaim", "Adam")
                .signWith(firstKey, Jwts.SIG.HS256)
                .compact();
        JwtParserBuilder jwtParserBuilder = Jwts.parser().clockSkewSeconds(180).verifyWith(firstKey);
        assertThat(jwtParserBuilder.build().parseSignedClaims(firstCompactJws).getPayload().getSubject()).isEqualTo("Joe");
        assertDoesNotThrow(() -> jwtParserBuilder.requireSubject("Joe").build().parseSignedClaims(firstCompactJws));
        assertDoesNotThrow(() -> jwtParserBuilder.requireIssuer("Aaron").build().parseSignedClaims(firstCompactJws));
        assertDoesNotThrow(() -> jwtParserBuilder.requireAudience("Abel").build().parseSignedClaims(firstCompactJws));
    }

    @Test
    void testCompression() {
        SecretKey firstKey = Jwts.SIG.HS256.key().build();
        assertThat(Jwts.parser().verifyWith(firstKey).build().parseSignedClaims(
                Jwts.builder().subject("Joe").signWith(firstKey).compact()
        ).getPayload().getSubject()).isEqualTo("Joe");
        assertThat(Jwts.parser().verifyWith(firstKey).build().parseSignedClaims(
                Jwts.builder().subject("Joe").signWith(firstKey).compact()
        ).getPayload().getSubject()).isEqualTo("Joe");
    }

}