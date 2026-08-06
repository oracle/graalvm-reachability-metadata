/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_jsonwebtoken.jjwt_jackson;

import io.jsonwebtoken.jackson.io.JacksonDeserializer;
import io.jsonwebtoken.jackson.io.JacksonSerializer;
import io.jsonwebtoken.lang.Supplier;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonDeserializerCoverageTest {
    @Test
    void deserializesConfiguredClaimIntoApplicationType() {
        Map<String, Class<?>> claimTypes = Map.of("user", User.class);
        JacksonDeserializer<Map<String, Object>> deserializer = new JacksonDeserializer<>(claimTypes);

        Map<String, Object> claims = deserializer.deserialize(new StringReader("""
                {"issuer":"https://issuer.example.com","user":{"firstName":"Jill","lastName":"Coder"}}
                """));

        assertThat(claims).containsEntry("issuer", "https://issuer.example.com");
        assertThat(claims.get("user"))
                .isInstanceOfSatisfying(User.class, user -> {
                    assertThat(user.getFirstName()).isEqualTo("Jill");
                    assertThat(user.getLastName()).isEqualTo("Coder");
                });
    }

    @Test
    void serializesSupplierValueThroughJacksonSerializer() {
        Supplier<String> supplier = () -> "resolved-value";
        JacksonSerializer<Supplier<String>> serializer = new JacksonSerializer<>();

        byte[] serialized = serializer.serialize(supplier);

        assertThat(serialized).asString().isEqualTo("\"resolved-value\"");
    }

    public static final class User {
        private String firstName;
        private String lastName;

        public User() {
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }
    }
}
