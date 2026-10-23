/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_jsonwebtoken.jjwt_jackson;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.DeserializationException;
import io.jsonwebtoken.jackson.io.JacksonDeserializer;
import io.jsonwebtoken.jackson.io.JacksonSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Jackson's allowlisted polymorphic claim mapping for step-up authentication proofs.
public class PolymorphicAuthenticationProofTest {
    @Test
    @Timeout(60)
    public void restoresKnownProofKindsAndRejectsUnknownKinds() {
        JacksonSerializer<Map<String, ?>> serializer = new JacksonSerializer<>();
        JacksonDeserializer<Map<String, ?>> deserializer = new JacksonDeserializer<>(
                Map.of("proof", AuthenticationProof.class));
        List<AuthenticationProof> proofs = List.of(
                new EmailProof("morgan@example.test"),
                new HardwareKeyProof("security-key-704", true));

        for (AuthenticationProof proof : proofs) {
            Claims identity = Jwts.claims()
                    .issuer("account-security")
                    .subject("customer-704")
                    .add("proof", proof)
                    .build();
            byte[] response = serializer.serialize(identity);
            Claims restored = Jwts.claims().add(deserializer.deserialize(response)).build();
            AuthenticationProof received = restored.get("proof", AuthenticationProof.class);

            assertEquals("account-security", restored.getIssuer());
            assertEquals("customer-704", restored.getSubject());
            if (proof instanceof HardwareKeyProof) {
                HardwareKeyProof key = assertInstanceOf(HardwareKeyProof.class, received);
                assertEquals("security-key-704", key.credentialId);
                assertTrue(key.userVerified);
                assertTrue(received.permitsPayoutChange());
            } else {
                EmailProof email = assertInstanceOf(EmailProof.class, received);
                assertEquals("morgan@example.test", email.address);
                assertFalse(received.permitsPayoutChange());
            }
        }

        String unsupportedProof = """
                {
                  "iss": "account-security",
                  "sub": "customer-704",
                  "proof": {"kind": "administrator-override", "userVerified": true}
                }
                """;
        assertThrows(DeserializationException.class,
                () -> deserializer.deserialize(unsupportedProof.getBytes(StandardCharsets.UTF_8)));
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "kind")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = EmailProof.class, name = "email"),
            @JsonSubTypes.Type(value = HardwareKeyProof.class, name = "hardware-key")
    })
    public abstract static class AuthenticationProof {
        public abstract String getKind();

        public abstract boolean permitsPayoutChange();
    }

    public static final class EmailProof extends AuthenticationProof {
        public final String address;

        @JsonCreator
        public EmailProof(@JsonProperty("address") String address) {
            this.address = address;
        }

        @Override
        public String getKind() {
            return "email";
        }

        @Override
        public boolean permitsPayoutChange() {
            return false;
        }
    }

    public static final class HardwareKeyProof extends AuthenticationProof {
        public final String credentialId;
        public final boolean userVerified;

        @JsonCreator
        public HardwareKeyProof(
                @JsonProperty("credentialId") String credentialId,
                @JsonProperty("userVerified") boolean userVerified) {
            this.credentialId = credentialId;
            this.userVerified = userVerified;
        }

        @Override
        public String getKind() {
            return "hardware-key";
        }

        @Override
        public boolean permitsPayoutChange() {
            return userVerified;
        }
    }
}
