/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auth.google_auth_library_oauth2_http;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.auth.ServiceAccountSigner;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AppEngineCredentialsTest {
    static {
        AppEngineCredentialsTestSupport.configureAppEngineStandardEnvironment();
    }

    @TempDir Path temporaryHome;

    private AppEngineCredentialsTestSupport support;

    @BeforeEach
    public void setUp() {
        support = AppEngineCredentialsTestSupport.install(temporaryHome);
    }

    @AfterEach
    public void tearDown() {
        support.close();
    }

    @Test
    public void appEngineCredentialsRefreshesTokensAndSignsBytes() throws Exception {
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
        ServiceAccountSigner signer = (ServiceAccountSigner) credentials;
        GoogleCredentials scopedCredentials =
                credentials.createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));

        AccessToken accessToken = scopedCredentials.refreshAccessToken();
        byte[] signature = signer.sign("payload".getBytes(StandardCharsets.UTF_8));

        assertThat(credentials.getClass().getName())
                .isEqualTo("com.google.auth.oauth2.AppEngineCredentials");
        assertThat(signer.getAccount()).isEqualTo(AppEngineCredentialsTestSupport.SERVICE_ACCOUNT);
        assertThat(accessToken.getTokenValue())
                .isEqualTo(AppEngineCredentialsTestSupport.ACCESS_TOKEN);
        assertThat(accessToken.getExpirationTime()).isNotNull();
        assertThat(signature).containsExactly(AppEngineCredentialsTestSupport.SIGNATURE);
    }

    @Test
    public void appEngineCredentialsDeserializationRestoresAppIdentityAccess() throws Exception {
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
        GoogleCredentials restored = deserialize(serialize(credentials));
        ServiceAccountSigner signer = (ServiceAccountSigner) restored;
        GoogleCredentials scopedCredentials =
                restored.createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));

        AccessToken accessToken = scopedCredentials.refreshAccessToken();
        byte[] signature = signer.sign("restored-payload".getBytes(StandardCharsets.UTF_8));

        assertThat(restored).isEqualTo(credentials);
        assertThat(signer.getAccount()).isEqualTo(AppEngineCredentialsTestSupport.SERVICE_ACCOUNT);
        assertThat(accessToken.getTokenValue())
                .isEqualTo(AppEngineCredentialsTestSupport.ACCESS_TOKEN);
        assertThat(signature).containsExactly(AppEngineCredentialsTestSupport.SIGNATURE);
    }

    private static byte[] serialize(GoogleCredentials credentials) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(credentials);
        }
        return bytes.toByteArray();
    }

    private static GoogleCredentials deserialize(byte[] credentials) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(credentials))) {
            return (GoogleCredentials) input.readObject();
        }
    }
}
