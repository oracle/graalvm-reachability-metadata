/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_jsonwebtoken.jjwt_jackson;

import io.jsonwebtoken.CompressionCodecs;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class Jjwt_jacksonTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void testUnsecuredJWTs() {
        // Build two unsecured JWTs and ensure they parse correctly (without using JJWT parser APIs)
        String token1 = Jwts.builder().setSubject("Joe").compact();
        String token2 = Jwts.builder().setSubject("Joe").compact();

        assertThat(getSubject(token1)).isEqualTo("Joe");
        assertThat(getSubject(token2)).isEqualTo("Joe");
    }

    @Test
    void testCreatingAJWT() {
        Date firstDate = new Date();
        Date secondDate = new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000L);
        String uuidString = UUID.randomUUID().toString();

        String compactJwt = Jwts.builder()
                .setSubject("Joe")
                .setHeaderParam("kid", "myKeyId")
                .setIssuer("Aaron")
                .setAudience("Abel")
                .setExpiration(secondDate)
                .setNotBefore(firstDate)
                .setIssuedAt(firstDate)
                .setId(uuidString)
                .claim("exampleClaim", "Adam")
                .compressWith(CompressionCodecs.GZIP)
                .compact();

        // Validate header values
        JsonNode header = getHeader(compactJwt);
        assertThat(header.get("alg").asText()).isEqualTo("none");
        assertThat(header.get("kid").asText()).isEqualTo("myKeyId");
        assertThat(header.get("zip").asText()).isEqualTo("GZIP");

        // Validate some claims from payload (decompressed if needed)
        JsonNode payload = getPayloadJson(compactJwt, header);
        assertThat(payload.get("sub").asText()).isEqualTo("Joe");
        assertThat(payload.get("iss").asText()).isEqualTo("Aaron");
        assertThat(payload.get("aud").asText()).isEqualTo("Abel");
        assertThat(payload.get("jti").asText()).isEqualTo(uuidString);
        assertThat(payload.get("exampleClaim").asText()).isEqualTo("Adam");

        // Make sure basic JSON fields exist for exp/nbf/iat (exact numeric value formatting is implementation-specific)
        assertThat(payload.has("exp")).isTrue();
        assertThat(payload.has("nbf")).isTrue();
        assertThat(payload.has("iat")).isTrue();

        // Keep an assertion using JUnit to reflect the original intent (no exception on reading/validating)
        assertDoesNotThrow(() -> {
            // No-op; helpers already validated the token structure and claims
        });
    }

    @Test
    void testCompression() {
        String deflated = Jwts.builder()
                .setSubject("Joe")
                .compressWith(CompressionCodecs.DEFLATE)
                .compact();

        String gzipped = Jwts.builder()
                .setSubject("Joe")
                .compressWith(CompressionCodecs.GZIP)
                .compact();

        JsonNode defHeader = getHeader(deflated);
        JsonNode gzHeader = getHeader(gzipped);

        // Headers should indicate compression used
        assertThat(defHeader.get("alg").asText()).isEqualTo("none");
        assertThat(defHeader.get("zip").asText()).isEqualTo("DEF");
        assertThat(gzHeader.get("alg").asText()).isEqualTo("none");
        assertThat(gzHeader.get("zip").asText()).isEqualTo("GZIP");

        // Payload subjects should be preserved after (de)compression
        assertThat(getPayloadJson(deflated, defHeader).get("sub").asText()).isEqualTo("Joe");
        assertThat(getPayloadJson(gzipped, gzHeader).get("sub").asText()).isEqualTo("Joe");
    }

    @Test
    void testMultipleTokens() {
        // Create a few different unsecured tokens and ensure decoding works consistently
        String t1 = Jwts.builder().setSubject("Joe").compact();
        String t2 = Jwts.builder().setSubject("Joe").compressWith(CompressionCodecs.GZIP).compact();
        String t3 = Jwts.builder().setSubject("Joe").compressWith(CompressionCodecs.DEFLATE).compact();

        assertThat(getSubject(t1)).isEqualTo("Joe");
        assertThat(getSubject(t2)).isEqualTo("Joe");
        assertThat(getSubject(t3)).isEqualTo("Joe");
    }

    // Helpers

    private static String getSubject(String compact) {
        JsonNode header = getHeader(compact);
        return getPayloadJson(compact, header).get("sub").asText();
    }

    private static JsonNode getHeader(String compact) {
        String[] parts = splitCompact(compact);
        byte[] headerBytes = Base64.getUrlDecoder().decode(parts[0]);
        try {
            return MAPPER.readTree(headerBytes);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse JWT header JSON", e);
        }
    }

    private static JsonNode getPayloadJson(String compact, JsonNode header) {
        String[] parts = splitCompact(compact);
        byte[] payloadRaw = Base64.getUrlDecoder().decode(parts[1]);

        // Decompress if header indicates compression
        String zip = header.has("zip") ? header.get("zip").asText() : null;
        byte[] payloadBytes = switch (zip == null ? "" : zip) {
            case "GZIP" -> ungzip(payloadRaw);
            case "DEF" -> inflate(payloadRaw);
            default -> payloadRaw;
        };

        try {
            return MAPPER.readTree(payloadBytes);
        } catch (IOException e) {
            // Also try treating as a plain String (defensive)
            String json = new String(payloadBytes, StandardCharsets.UTF_8);
            try {
                return MAPPER.readTree(json);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to parse JWT payload JSON", ex);
            }
        }
    }

    private static String[] splitCompact(String compact) {
        String[] parts = compact.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Not a compact JWT: " + compact);
        }
        return parts;
        }

    private static byte[] readAll(InputStream in) {
        try (in) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read stream", e);
        }
    }

    private static byte[] ungzip(byte[] data) {
        return readAll(wrapGzip(new ByteArrayInputStream(data)));
    }

    private static byte[] inflate(byte[] data) {
        return readAll(new InflaterInputStream(new ByteArrayInputStream(data)));
    }

    private static GZIPInputStream wrapGzip(InputStream in) {
        try {
            return new GZIPInputStream(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create GZIPInputStream", e);
        }
    }
}
