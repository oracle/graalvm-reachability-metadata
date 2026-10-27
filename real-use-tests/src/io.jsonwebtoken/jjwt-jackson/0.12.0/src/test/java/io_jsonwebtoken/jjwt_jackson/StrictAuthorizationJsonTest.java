/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_jsonwebtoken.jjwt_jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.DeserializationException;
import io.jsonwebtoken.jackson.io.JacksonDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises Jackson's strict duplicate-field detection for authorization response ingestion.
public class StrictAuthorizationJsonTest {
    @Test
    @Timeout(60)
    public void acceptsUnambiguousIdentityAndRejectsConflictingClaims() {
        ObjectMapper mapper = new ObjectMapper()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        JacksonDeserializer<Map<String, ?>> deserializer = new JacksonDeserializer<>(mapper);
        String authorizedResponse = """
                {
                  "sub": "operator-42",
                  "access": {"role": "viewer", "warehouse": "north"},
                  "profile": {"role": "dispatcher"}
                }
                """;

        Map<String, ?> values = deserializer.deserialize(new StringReader(authorizedResponse));
        Claims identity = Jwts.claims().add(values).build();

        assertEquals("operator-42", identity.getSubject());
        assertEquals(Map.of("role", "viewer", "warehouse", "north"),
                identity.get("access", Map.class));
        assertEquals(Map.of("role", "dispatcher"), identity.get("profile", Map.class));

        String conflictingSubject = """
                {
                  "sub": "operator-42",
                  "sub": "administrator",
                  "access": {"role": "viewer", "warehouse": "north"}
                }
                """;
        String conflictingRole = """
                {
                  "sub": "operator-42",
                  "access": {"role": "viewer", "role": "administrator", "warehouse": "north"}
                }
                """;

        assertThrows(DeserializationException.class,
                () -> deserializer.deserialize(new StringReader(conflictingSubject)));
        assertThrows(DeserializationException.class,
                () -> deserializer.deserialize(new StringReader(conflictingRole)));
    }
}
