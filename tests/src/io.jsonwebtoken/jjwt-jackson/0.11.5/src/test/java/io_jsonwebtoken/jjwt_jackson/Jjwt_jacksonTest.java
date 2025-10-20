/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_jsonwebtoken.jjwt_jackson;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.jackson.io.JacksonDeserializer;
import io.jsonwebtoken.jackson.io.JacksonSerializer;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.security.Key;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class Jjwt_jacksonTest {

    @Test
    void testJacksonSerializer() {
        // Create a JacksonSerializer instance
        JacksonSerializer jacksonSerializer = new JacksonSerializer();

        // Create a sample object to serialize
        String claimValue = "test-claim-value";
        Map<String, Object> claims = Map.of("test-claim", claimValue);

        // Serialize the claims
        byte[] serializedClaims = jacksonSerializer.serialize(claims);

        // Verify the serialized claims
        assertThat(serializedClaims).isNotNull().isNotEmpty();
    }

    @Test
    void testJacksonDeserializer() throws Exception {
        // Create a JacksonDeserializer instance
        ObjectMapper objectMapper = new ObjectMapper();
        JacksonDeserializer jacksonDeserializer = new JacksonDeserializer(objectMapper);

        // Create a sample JSON string to deserialize
        String json = "{\"test-claim\":\"test-claim-value\"}";

        // Deserialize the JSON string
        byte[] jsonBytes = json.getBytes();
        Object deserializedObject = jacksonDeserializer.deserialize(jsonBytes);
        assertThat(deserializedObject).isInstanceOf(Map.class);
        Map<String, Object> deserializedMap = (Map<String, Object>) deserializedObject;
        assertThat(deserializedMap.get("test-claim")).isEqualTo("test-claim-value");

        // Verify the deserialized JSON
    }

    @Test
    void testJwtWithJackson() {
        // Create a JacksonSerializer instance
        JacksonSerializer jacksonSerializer = new JacksonSerializer();

        // Create a JWT with a claim
        String claimValue = "test-claim-value";
        Key secretKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        String jwt = Jwts.builder()
                .claim("test-claim", claimValue)
                .serializeToJsonWith(jacksonSerializer)
                .signWith(secretKey)
                .compact();

        // Verify the JWT is not empty
        assertThat(jwt).isNotNull().isNotEmpty();

        // Create a JacksonDeserializer instance
        ObjectMapper objectMapper = new ObjectMapper();
        JacksonDeserializer jacksonDeserializer = new JacksonDeserializer(objectMapper);

        // Parse the JWT
        Claims claims = Jwts.parserBuilder()
                .deserializeJsonWith(jacksonDeserializer)
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(jwt)
                .getBody();

        // Verify the claim value
        assertThat(claims.get("test-claim")).isNotNull();
        assertThat(claims.get("test-claim")).isEqualTo(claimValue);
    }
}
