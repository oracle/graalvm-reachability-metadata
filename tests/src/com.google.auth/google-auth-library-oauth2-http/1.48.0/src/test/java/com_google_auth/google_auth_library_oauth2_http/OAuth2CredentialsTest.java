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

        UserCredentials restored = deserialize(serialize(credentials));

        Map<String, List<String>> requestMetadata = restored.getRequestMetadata(AUDIENCE_URI);
        assertThat(requestMetadata)
                .containsEntry(AuthHttpConstants.AUTHORIZATION, List.of("Bearer access-token"));
        assertThat(restored).isEqualTo(credentials);
        assertThat(restored.toString()).contains("client-id", "quota-project");
    }

    @Test
    public void userCredentialsDeserializationRestoresCustomTransportFactory() throws Exception {
        AccessToken accessToken = new AccessToken(
                "custom-access-token",
                new Date(System.currentTimeMillis() + 3_600_000));
        OAuth2CredentialsTestTransportFactory.resetConstructorCalls();
        UserCredentials credentials = UserCredentials.newBuilder()
                .setClientId("custom-client-id")
                .setClientSecret("custom-client-secret")
                .setAccessToken(accessToken)
                .setHttpTransportFactory(new OAuth2CredentialsTestTransportFactory())
                .build();
        byte[] serializedCredentials = serialize(credentials);

        OAuth2CredentialsTestTransportFactory.resetConstructorCalls();
        UserCredentials restored = deserialize(serializedCredentials);

        assertThat(OAuth2CredentialsTestTransportFactory.getConstructorCalls()).isEqualTo(1);
        assertThat(restored).isEqualTo(credentials);
        assertThat(restored.getRequestMetadata(AUDIENCE_URI))
                .containsEntry(
                        AuthHttpConstants.AUTHORIZATION,
                        List.of("Bearer custom-access-token"));
        assertThat(restored.toString())
                .contains(OAuth2CredentialsTestTransportFactory.class.getName());
    }

    private static byte[] serialize(UserCredentials credentials) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(credentials);
        }
        return bytes.toByteArray();
    }

    private static UserCredentials deserialize(byte[] credentials) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(credentials))) {
            return (UserCredentials) input.readObject();
        }
    }
}
