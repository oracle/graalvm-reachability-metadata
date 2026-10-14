/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_api_client.google_api_client;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DefaultCredentialProviderTest {
    private static final String RUNTIME_ENVIRONMENT_PROPERTY =
            "com.google.appengine.runtime.environment";
    private static final String USER_HOME_PROPERTY = "user.home";

    @TempDir Path temporaryHome;

    private String previousRuntimeEnvironment;
    private String previousUserHome;

    @BeforeEach
    public void setUpAppEngineEnvironment() {
        previousRuntimeEnvironment = System.getProperty(RUNTIME_ENVIRONMENT_PROPERTY);
        previousUserHome = System.getProperty(USER_HOME_PROPERTY);
        System.setProperty(RUNTIME_ENVIRONMENT_PROPERTY, "Development");
        System.setProperty(USER_HOME_PROPERTY, temporaryHome.toString());
    }

    @AfterEach
    public void restoreEnvironment() {
        restoreProperty(RUNTIME_ENVIRONMENT_PROPERTY, previousRuntimeEnvironment);
        restoreProperty(USER_HOME_PROPERTY, previousUserHome);
    }

    @Test
    public void applicationDefaultCredentialUsesAppEngineIdentity() throws Exception {
        GoogleCredential credential =
                GoogleCredential.getApplicationDefault(
                        new MockHttpTransport(), GsonFactory.getDefaultInstance());

        assertThat(credential.getClass().getName())
                .isEqualTo(
                        "com.google.api.client.googleapis.extensions.appengine.auth.oauth2"
                                + ".AppIdentityCredential$AppEngineCredentialWrapper");
        assertThat(credential.createScopedRequired()).isTrue();

        GoogleCredential scoped =
                credential.createScoped(Collections.singleton("https://example.test/scope"));
        assertThat(scoped.createScopedRequired()).isFalse();
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
