/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auth.google_auth_library_oauth2_http;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.auth.http.AuthHttpConstants;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.UserCredentials;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class OAuth2CredentialsTest {
    private static final URI AUDIENCE_URI = URI.create("https://example.test/resource");

    @Test
    public void userCredentialsDeserializationRestoresDefaultTransportFactory() throws Exception {
        AccessToken accessToken = new AccessToken(
                "access-token",
                new Date(System.currentTimeMillis() + 3_600_000));
        UserCredentials credentials = UserCredentials.newBuilder()
                .setClientId("client-id")
                .setClientSecret("client-secret")
                .setAccessToken(accessToken)
                .setQuotaProjectId("quota-project")
                .build();

        UserCredentials restored;
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(serialize(credentials)))) {
            restored = (UserCredentials) input.readObject();
        }

        Map<String, List<String>> requestMetadata = restored.getRequestMetadata(AUDIENCE_URI);
        assertThat(requestMetadata)
                .containsEntry(AuthHttpConstants.AUTHORIZATION, List.of("Bearer access-token"));
        assertThat(restored).isEqualTo(credentials);
        assertThat(restored.toString()).contains("client-id", "quota-project");
    }

    private static byte[] serialize(UserCredentials credentials) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(credentials);
        }
        return bytes.toByteArray();
    }
}
