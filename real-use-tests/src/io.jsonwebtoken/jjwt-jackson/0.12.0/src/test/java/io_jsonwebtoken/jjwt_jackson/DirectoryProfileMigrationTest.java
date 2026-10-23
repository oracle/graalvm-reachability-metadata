/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_jsonwebtoken.jjwt_jackson;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.jackson.io.JacksonDeserializer;
import io.jsonwebtoken.jackson.io.JacksonSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Jackson's property naming and aliases for a directory profile schema migration.
public class DirectoryProfileMigrationTest {
    @Test
    @Timeout(60)
    public void acceptsLegacyProfileFieldsAndExportsOnlyCanonicalNames() {
        String legacyResponse = """
                {
                  "sub": "operator-42",
                  "directory_profile": {
                    "name": "Morgan Lee",
                    "department": "dispatch",
                    "employee_number": "000704"
                  }
                }
                """;
        String currentResponse = """
                {
                  "sub": "operator-42",
                  "directory_profile": {
                    "display_name": "Morgan Lee",
                    "department_code": "dispatch",
                    "employee_number": "000704"
                  }
                }
                """;
        JacksonDeserializer<Map<String, ?>> profileDecoder = new JacksonDeserializer<>(
                Map.of("directory_profile", DirectoryProfile.class));
        JacksonSerializer<Map<String, ?>> serializer = new JacksonSerializer<>();
        JacksonDeserializer<Map<String, ?>> clientDecoder = new JacksonDeserializer<>();
        Map<String, String> canonicalProfile = Map.of(
                "display_name", "Morgan Lee",
                "department_code", "dispatch",
                "employee_number", "000704");

        // Both provider schemas produce the same application model and public response.
        for (String providerResponse : List.of(legacyResponse, currentResponse)) {
            Claims identity = Jwts.claims()
                    .add(profileDecoder.deserialize(providerResponse.getBytes(StandardCharsets.UTF_8)))
                    .build();
            DirectoryProfile profile = identity.get("directory_profile", DirectoryProfile.class);

            assertEquals("operator-42", identity.getSubject());
            assertEquals("Morgan Lee", profile.displayName);
            assertEquals("dispatch", profile.departmentCode);
            assertEquals("000704", profile.employeeNumber);

            byte[] exported = serializer.serialize(identity);
            Map<String, ?> publicResponse = clientDecoder.deserialize(exported);
            assertEquals(Map.of("sub", "operator-42", "directory_profile", canonicalProfile), publicResponse);

            Claims restored = Jwts.claims().add(profileDecoder.deserialize(exported)).build();
            DirectoryProfile restoredProfile = restored.get("directory_profile", DirectoryProfile.class);
            assertEquals(profile.displayName, restoredProfile.displayName);
            assertEquals(profile.departmentCode, restoredProfile.departmentCode);
            assertEquals(profile.employeeNumber, restoredProfile.employeeNumber);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class DirectoryProfile {
        @JsonAlias("name")
        public String displayName;

        @JsonAlias("department")
        public String departmentCode;

        public String employeeNumber;
    }
}
