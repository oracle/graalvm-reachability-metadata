/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_jsonwebtoken.jjwt_impl;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.CompressionCodec;
import io.jsonwebtoken.CompressionCodecs;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.JwtHandlerAdapter;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SigningKeyResolverAdapter;
import io.jsonwebtoken.io.DeserializationException;
import io.jsonwebtoken.io.Deserializer;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.io.SerializationException;
import io.jsonwebtoken.io.Serializer;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyPair;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Jjwt_implTest {
    private static final SimpleJsonCodec JSON = new SimpleJsonCodec();
    private static final Date ISSUED_AT = Date.from(Instant.parse("2026-01-01T00:00:00Z"));
    private static final Date NOT_BEFORE = Date.from(Instant.parse("2026-01-01T00:01:00Z"));
    private static final Date EXPIRATION = Date.from(Instant.parse("2036-01-01T00:00:00Z"));

    @Test
    void signedClaimsJwtRoundTripsHeadersRegisteredClaimsAndCustomClaims() {
        SecretKey key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        String jws = Jwts.builder()
                .serializeToJsonWith(JSON)
                .setHeaderParam(JwsHeader.KEY_ID, "primary-hmac-key")
                .setHeaderParam(Header.TYPE, Header.JWT_TYPE)
                .setIssuer("https://issuer.example")
                .setSubject("integration-test-subject")
                .setAudience("native-image-tests")
                .setIssuedAt(ISSUED_AT)
                .setNotBefore(NOT_BEFORE)
                .setExpiration(EXPIRATION)
                .setId("jwt-id-123")
                .claim("scope", "read:messages")
                .claim("admin", true)
                .claim("loginCount", 7)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        JwtParser parser = parserBuilder()
                .requireIssuer("https://issuer.example")
                .requireSubject("integration-test-subject")
                .requireAudience("native-image-tests")
                .requireIssuedAt(ISSUED_AT)
                .requireNotBefore(NOT_BEFORE)
                .requireExpiration(EXPIRATION)
                .requireId("jwt-id-123")
                .require("scope", "read:messages")
                .require("admin", true)
                .require("loginCount", 7)
                .setSigningKey(key)
                .build();

        Jws<Claims> parsed = parser.parseClaimsJws(jws);

        assertThat(parser.isSigned(jws)).isTrue();
        assertThat(parsed.getHeader().getAlgorithm()).isEqualTo(SignatureAlgorithm.HS256.getValue());
        assertThat(parsed.getHeader().getKeyId()).isEqualTo("primary-hmac-key");
        assertThat(parsed.getHeader().getType()).isEqualTo(Header.JWT_TYPE);
        assertThat(parsed.getBody().getIssuer()).isEqualTo("https://issuer.example");
        assertThat(parsed.getBody().getSubject()).isEqualTo("integration-test-subject");
        assertThat(parsed.getBody().getAudience()).isEqualTo("native-image-tests");
        assertThat(parsed.getBody().getIssuedAt()).isEqualTo(ISSUED_AT);
        assertThat(parsed.getBody().getNotBefore()).isEqualTo(NOT_BEFORE);
        assertThat(parsed.getBody().getExpiration()).isEqualTo(EXPIRATION);
        assertThat(parsed.getBody().getId()).isEqualTo("jwt-id-123");
        assertThat(parsed.getBody().get("scope", String.class)).isEqualTo("read:messages");
        assertThat(parsed.getBody().get("admin", Boolean.class)).isTrue();
        assertThat(parsed.getBody().get("loginCount", Integer.class)).isEqualTo(7);
        assertThat(parsed.getSignature()).isNotBlank();
    }

    @Test
    void compressionCodecsRoundTripDirectlyAndInsideSignedClaimsJwt() {
        byte[] payload = "compressible payload compressible payload compressible payload"
                .getBytes(StandardCharsets.UTF_8);
        assertCompressionRoundTrip(CompressionCodecs.GZIP, payload);
        assertCompressionRoundTrip(CompressionCodecs.DEFLATE, payload);

        SecretKey key = Keys.secretKeyFor(SignatureAlgorithm.HS384);
        Stream.of(CompressionCodecs.GZIP, CompressionCodecs.DEFLATE).forEach(codec -> {
            String jws = Jwts.builder()
                    .serializeToJsonWith(JSON)
                    .setSubject("compressed-claims")
                    .claim("codec", codec.getAlgorithmName())
                    .compressWith(codec)
                    .signWith(key, SignatureAlgorithm.HS384)
                    .compact();

            Jws<Claims> parsed = parserBuilder().setSigningKey(key).build().parseClaimsJws(jws);

            assertThat(parsed.getHeader().getCompressionAlgorithm()).isEqualTo(codec.getAlgorithmName());
            assertThat(parsed.getBody().getSubject()).isEqualTo("compressed-claims");
            assertThat(parsed.getBody().get("codec", String.class)).isEqualTo(codec.getAlgorithmName());
        });
    }

    @Test
    void customCompressionCodecResolverInflatesSignedClaimsJwt() {
        CompressionCodec codec = new ReversingCompressionCodec();
        SecretKey key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        String jws = Jwts.builder()
                .serializeToJsonWith(JSON)
                .setSubject("custom-compression")
                .claim("departments", List.of("engineering", "support"))
                .compressWith(codec)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        Jws<Claims> parsed = parserBuilder()
                .setCompressionCodecResolver(header -> {
                    assertThat(header.getCompressionAlgorithm()).isEqualTo(codec.getAlgorithmName());
                    return codec;
                })
                .setSigningKey(key)
                .build()
                .parseClaimsJws(jws);

        Object departments = parsed.getBody().get("departments");
        assertThat(parsed.getHeader().getCompressionAlgorithm()).isEqualTo(codec.getAlgorithmName());
        assertThat(parsed.getBody().getSubject()).isEqualTo("custom-compression");
        assertThat(departments).isInstanceOf(List.class);
        assertThat((List<?>) departments).hasSize(2);
        assertThat(((List<?>) departments).get(0)).isEqualTo("engineering");
        assertThat(((List<?>) departments).get(1)).isEqualTo("support");
    }

    @Test
    void hmacRsaAndEllipticCurveSignaturesAreVerifiedWithMatchingKeys() {
        Stream.of(SignatureAlgorithm.HS256, SignatureAlgorithm.HS384, SignatureAlgorithm.HS512).forEach(algorithm -> {
            SecretKey key = Keys.secretKeyFor(algorithm);
            String jws = signedSubject("hmac-" + algorithm.name(), key, algorithm);

            assertThat(parserBuilder().setSigningKey(key).build().parseClaimsJws(jws).getBody().getSubject())
                    .isEqualTo("hmac-" + algorithm.name());
        });

        Stream.of(SignatureAlgorithm.RS256, SignatureAlgorithm.RS384, SignatureAlgorithm.RS512,
                SignatureAlgorithm.PS256, SignatureAlgorithm.PS384, SignatureAlgorithm.PS512,
                SignatureAlgorithm.ES256, SignatureAlgorithm.ES384, SignatureAlgorithm.ES512).forEach(algorithm -> {
                    KeyPair keyPair = Keys.keyPairFor(algorithm);
                    String jws = signedSubject("asymmetric-" + algorithm.name(), keyPair.getPrivate(), algorithm);

                    assertThat(parserBuilder().setSigningKey(keyPair.getPublic()).build().parseClaimsJws(jws)
                            .getBody().getSubject()).isEqualTo("asymmetric-" + algorithm.name());
                });
    }

    @Test
    void signingKeyResolverSelectsVerificationKeyFromJwsHeader() {
        SecretKey firstKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        SecretKey secondKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        Map<String, Key> keys = new LinkedHashMap<>();
        keys.put("first", firstKey);
        keys.put("second", secondKey);
        String jws = Jwts.builder()
                .serializeToJsonWith(JSON)
                .setHeaderParam(JwsHeader.KEY_ID, "second")
                .setSubject("resolved-by-kid")
                .signWith(secondKey, SignatureAlgorithm.HS256)
                .compact();

        Jws<Claims> parsed = parserBuilder().setSigningKeyResolver(new SigningKeyResolverAdapter() {
            @Override
            public Key resolveSigningKey(JwsHeader header, Claims claims) {
                return keys.get(header.getKeyId());
            }
        }).build().parseClaimsJws(jws);

        assertThat(parsed.getHeader().getKeyId()).isEqualTo("second");
        assertThat(parsed.getBody().getSubject()).isEqualTo("resolved-by-kid");
    }

    @Test
    void unsignedClaimsJwtParsesWithoutVerificationKeyAndDispatchesToClaimsJwtHandler() {
        String jwt = Jwts.builder()
                .serializeToJsonWith(JSON)
                .setHeaderParam(Header.TYPE, Header.JWT_TYPE)
                .setSubject("unsigned-claims")
                .claim("roles", List.of("reader", "writer"))
                .compact();

        JwtParser parser = parserBuilder().build();
        Jwt<Header, Claims> parsed = parser.parseClaimsJwt(jwt);
        String handledSubject = parser.parse(jwt, new JwtHandlerAdapter<String>() {
            @Override
            public String onClaimsJwt(Jwt<Header, Claims> jwt) {
                assertThat(jwt.getHeader().getType()).isEqualTo(Header.JWT_TYPE);
                return jwt.getBody().getSubject();
            }
        });

        Object roles = parsed.getBody().get("roles");
        assertThat(parser.isSigned(jwt)).isFalse();
        assertThat(parsed.getHeader().getType()).isEqualTo(Header.JWT_TYPE);
        assertThat(parsed.getBody().getSubject()).isEqualTo("unsigned-claims");
        assertThat(roles).isInstanceOf(List.class);
        assertThat((List<?>) roles).hasSize(2);
        assertThat(((List<?>) roles).get(0)).isEqualTo("reader");
        assertThat(((List<?>) roles).get(1)).isEqualTo("writer");
        assertThat(handledSubject).isEqualTo("unsigned-claims");
    }

    @Test
    void plaintextJwsUsesCustomBase64UrlEncoderAndHandlerDispatch() {
        SecretKey key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        String jws = Jwts.builder()
                .serializeToJsonWith(JSON)
                .base64UrlEncodeWith(Encoders.BASE64URL)
                .setHeaderParam(Header.CONTENT_TYPE, "text/plain")
                .setPayload("plain-text-payload")
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        JwtParser parser = parserBuilder().setSigningKey(key).build();
        Jws<String> parsed = parser.parsePlaintextJws(jws);
        String handledPayload = parser.parse(jws, new JwtHandlerAdapter<String>() {
            @Override
            public String onPlaintextJws(Jws<String> jws) {
                return jws.getBody();
            }
        });

        assertThat(parsed.getHeader().getContentType()).isEqualTo("text/plain");
        assertThat(parsed.getBody()).isEqualTo("plain-text-payload");
        assertThat(handledPayload).isEqualTo("plain-text-payload");
    }

    @Test
    void parserRejectsTamperedSignaturesWrongClaimsAndExpiredTokens() {
        SecretKey key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        String jws = signedSubject("protected-subject", key, SignatureAlgorithm.HS256);
        int lastDot = jws.lastIndexOf('.');
        String tampered = jws.substring(0, lastDot + 1) + "tamperedSignature";

        assertThrows(SignatureException.class, () -> parserBuilder().setSigningKey(key).build().parseClaimsJws(tampered));
        assertThrows(IncorrectClaimException.class, () -> parserBuilder()
                .requireSubject("somebody-else")
                .setSigningKey(key)
                .build()
                .parseClaimsJws(jws));

        String expired = Jwts.builder()
                .serializeToJsonWith(JSON)
                .setSubject("expired")
                .setExpiration(Date.from(Instant.parse("2026-01-01T00:00:00Z")))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        assertThrows(ExpiredJwtException.class, () -> parserBuilder()
                .setClock(() -> Date.from(Instant.parse("2026-01-01T00:00:01Z")))
                .setSigningKey(key)
                .build()
                .parseClaimsJws(expired));
        assertThat(parserBuilder()
                .setClock(() -> Date.from(Instant.parse("2026-01-01T00:00:01Z")))
                .setAllowedClockSkewSeconds(2)
                .setSigningKey(key)
                .build()
                .parseClaimsJws(expired)
                .getBody()
                .getSubject()).isEqualTo("expired");
    }

    private static void assertCompressionRoundTrip(CompressionCodec codec, byte[] payload) {
        byte[] compressed = codec.compress(payload);
        byte[] decompressed = codec.decompress(compressed);

        assertThat(codec.getAlgorithmName()).isNotBlank();
        assertThat(compressed).isNotEqualTo(payload);
        assertThat(decompressed).isEqualTo(payload);
    }

    private static String signedSubject(String subject, Key key, SignatureAlgorithm algorithm) {
        return Jwts.builder()
                .serializeToJsonWith(JSON)
                .setSubject(subject)
                .signWith(key, algorithm)
                .compact();
    }

    private static JwtParserBuilder parserBuilder() {
        return Jwts.parserBuilder().deserializeJsonWith(JSON);
    }

    private static final class ReversingCompressionCodec implements CompressionCodec {
        private static final String ALGORITHM_NAME = "REV";

        @Override
        public String getAlgorithmName() {
            return ALGORITHM_NAME;
        }

        @Override
        public byte[] compress(byte[] bytes) {
            return reverse(bytes);
        }

        @Override
        public byte[] decompress(byte[] bytes) {
            return reverse(bytes);
        }

        private static byte[] reverse(byte[] bytes) {
            byte[] reversed = bytes.clone();
            for (int left = 0, right = reversed.length - 1; left < right; left++, right--) {
                byte value = reversed[left];
                reversed[left] = reversed[right];
                reversed[right] = value;
            }
            return reversed;
        }
    }

    private static final class SimpleJsonCodec implements Serializer<Map<String, ?>>, Deserializer<Map<String, ?>> {
        @Override
        public byte[] serialize(Map<String, ?> map) throws SerializationException {
            return toJsonObject(map).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public Map<String, ?> deserialize(byte[] bytes) throws DeserializationException {
            return new JsonParser(new String(bytes, StandardCharsets.UTF_8)).parseObject();
        }

        private static String toJsonObject(Map<String, ?> map) {
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, ?> entry : map.entrySet()) {
                Object value = entry.getValue();
                if (value == null) {
                    continue;
                }
                if (!first) {
                    json.append(',');
                }
                json.append(toJsonString(entry.getKey())).append(':').append(toJsonValue(value));
                first = false;
            }
            return json.append('}').toString();
        }

        private static String toJsonValue(Object value) {
            if (value instanceof Date) {
                return Long.toString(((Date) value).getTime() / 1000L);
            }
            if (value instanceof Number || value instanceof Boolean) {
                return value.toString();
            }
            if (value instanceof Iterable<?>) {
                return toJsonArray((Iterable<?>) value);
            }
            return toJsonString(value.toString());
        }

        private static String toJsonArray(Iterable<?> values) {
            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            for (Object value : values) {
                if (!first) {
                    json.append(',');
                }
                json.append(toJsonValue(value));
                first = false;
            }
            return json.append(']').toString();
        }

        private static String toJsonString(String value) {
            StringBuilder json = new StringBuilder("\"");
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                if (character == '\\' || character == '\"') {
                    json.append('\\').append(character);
                } else if (character == '\n') {
                    json.append("\\n");
                } else if (character == '\r') {
                    json.append("\\r");
                } else if (character == '\t') {
                    json.append("\\t");
                } else {
                    json.append(character);
                }
            }
            return json.append('"').toString();
        }
    }

    private static final class JsonParser {
        private final String json;
        private int position;

        private JsonParser(String json) {
            this.json = json;
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> values = new LinkedHashMap<>();
            skipWhitespace();
            if (consume('}')) {
                return values;
            }
            do {
                String key = parseString();
                expect(':');
                values.put(key, parseValue());
            } while (consume(','));
            expect('}');
            return values;
        }

        private Object parseValue() {
            skipWhitespace();
            char current = peek();
            if (current == '"') {
                return parseString();
            }
            if (current == '[') {
                return parseArray();
            }
            if (json.startsWith("true", position)) {
                position += 4;
                return Boolean.TRUE;
            }
            if (json.startsWith("false", position)) {
                position += 5;
                return Boolean.FALSE;
            }
            return parseInteger();
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> values = new ArrayList<>();
            skipWhitespace();
            if (consume(']')) {
                return values;
            }
            do {
                values.add(parseValue());
            } while (consume(','));
            expect(']');
            return values;
        }

        private String parseString() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (position < json.length()) {
                char character = json.charAt(position++);
                if (character == '"') {
                    return value.toString();
                }
                if (character == '\\') {
                    value.append(parseEscapedCharacter());
                } else {
                    value.append(character);
                }
            }
            throw new DeserializationException("Unterminated JSON string");
        }

        private char parseEscapedCharacter() {
            char escaped = json.charAt(position++);
            if (escaped == 'n') {
                return '\n';
            }
            if (escaped == 'r') {
                return '\r';
            }
            if (escaped == 't') {
                return '\t';
            }
            return escaped;
        }

        private Number parseInteger() {
            int start = position;
            if (peek() == '-') {
                position++;
            }
            while (position < json.length() && Character.isDigit(json.charAt(position))) {
                position++;
            }
            long value = Long.parseLong(json.substring(start, position));
            if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                return (int) value;
            }
            return value;
        }

        private boolean consume(char expected) {
            skipWhitespace();
            if (position < json.length() && json.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            skipWhitespace();
            if (position >= json.length() || json.charAt(position) != expected) {
                throw new DeserializationException("Expected '" + expected + "' at position " + position);
            }
            position++;
        }

        private char peek() {
            if (position >= json.length()) {
                throw new DeserializationException("Unexpected end of JSON input");
            }
            return json.charAt(position);
        }

        private void skipWhitespace() {
            while (position < json.length() && Character.isWhitespace(json.charAt(position))) {
                position++;
            }
        }
    }
}
