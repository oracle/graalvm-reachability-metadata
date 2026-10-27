/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_jsonwebtoken.jjwt_jackson;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.jackson.io.JacksonDeserializer;
import io.jsonwebtoken.jackson.io.JacksonSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/// Exercises Jackson's serialization views for audience-specific identity claim disclosure.
public class PartnerIdentityProjectionTest {
    @Test
    @Timeout(60)
    public void exportsPartnerProfileWithoutInternalContactDetailsOrCredentials() {
        IdentityProfile profile = new IdentityProfile(
                "Morgan Lee", "dispatch", "morgan@warehouse.example.test", "private-refresh-704");
        Claims identity = Jwts.claims()
                .issuer("warehouse-identity")
                .subject("operator-42")
                .add("profile", profile)
                .build();
        ObjectMapper partnerMapper = new ObjectMapper()
                .disable(MapperFeature.DEFAULT_VIEW_INCLUSION);
        partnerMapper.setConfig(partnerMapper.getSerializationConfig().withView(PartnerView.class));
        ObjectMapper internalMapper = partnerMapper.copy();
        internalMapper.setConfig(internalMapper.getSerializationConfig().withView(InternalView.class));
        JacksonSerializer<Map<String, ?>> partnerSerializer = new JacksonSerializer<>(partnerMapper);
        JacksonSerializer<Map<String, ?>> internalSerializer = new JacksonSerializer<>(internalMapper);
        JacksonDeserializer<Map<String, ?>> deserializer = new JacksonDeserializer<>();

        byte[] partnerResponse = partnerSerializer.serialize(identity);
        Claims partnerIdentity = Jwts.claims().add(deserializer.deserialize(partnerResponse)).build();
        Map<?, ?> partnerProfile = partnerIdentity.get("profile", Map.class);

        assertEquals("warehouse-identity", partnerIdentity.getIssuer());
        assertEquals("operator-42", partnerIdentity.getSubject());
        assertEquals(Map.of("displayName", "Morgan Lee", "department", "dispatch"), partnerProfile);
        String partnerJson = new String(partnerResponse, StandardCharsets.UTF_8);
        assertFalse(partnerJson.contains(profile.email));
        assertFalse(partnerJson.contains(profile.refreshToken));

        byte[] internalResponse = internalSerializer.serialize(identity);
        Claims internalIdentity = Jwts.claims().add(deserializer.deserialize(internalResponse)).build();
        assertEquals(Map.of(
                "displayName", "Morgan Lee",
                "department", "dispatch",
                "email", "morgan@warehouse.example.test"), internalIdentity.get("profile", Map.class));
        assertFalse(new String(internalResponse, StandardCharsets.UTF_8).contains(profile.refreshToken));

        // Reusing the partner codec must not inherit the internal audience's broader view.
        Claims repeatedPartnerIdentity = Jwts.claims()
                .add(deserializer.deserialize(partnerSerializer.serialize(identity)))
                .build();
        assertEquals(partnerProfile, repeatedPartnerIdentity.get("profile", Map.class));
    }

    public interface PartnerView {
    }

    public interface InternalView extends PartnerView {
    }

    public static final class IdentityProfile {
        @JsonView(PartnerView.class)
        public final String displayName;

        @JsonView(PartnerView.class)
        public final String department;

        @JsonView(InternalView.class)
        public final String email;

        public final String refreshToken;

        public IdentityProfile(String displayName, String department, String email, String refreshToken) {
            this.displayName = displayName;
            this.department = department;
            this.email = email;
            this.refreshToken = refreshToken;
        }
    }
}
