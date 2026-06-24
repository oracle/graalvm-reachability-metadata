/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auth.google_auth_library_oauth2_http;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.auth.oauth2.IdToken;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

public class IdTokenTest {
    @Test
    public void serializationRoundTripRestoresParsedJsonWebSignature() throws Exception {
        String tokenValue = createUnsignedJwt();
        IdToken token = IdToken.create(tokenValue);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(token);
        }

        IdToken restored;
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (IdToken) input.readObject();
        }

        assertThat(restored.getTokenValue()).isEqualTo(tokenValue);
        assertThat(restored).isEqualTo(token);
        assertThat(restored.hashCode()).isEqualTo(token.hashCode());
        assertThat(restored.toString()).contains(tokenValue);
    }

    private static String createUnsignedJwt() {
        String header = """
                {"alg":"none","typ":"JWT"}
                """;
        String payload = """
                {"iss":"https://accounts.google.com","sub":"subject","aud":"audience","exp":2524608000}
                """;
        return encode(header) + "." + encode(payload) + ".";
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
