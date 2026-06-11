/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auth.google_auth_library_oauth2_http;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DefaultCredentialsProviderTest {
    private static final String GAE_RUNTIME_VERSION_PROPERTY =
            AppEngineCredentialsTest.GAE_RUNTIME_VERSION_PROPERTY;
    private static final String GAE_RUNTIME_ENVIRONMENT_PROPERTY =
            AppEngineCredentialsTest.GAE_RUNTIME_ENVIRONMENT_PROPERTY;
    private static final String JETTY_LOGGER_PROPERTY =
            AppEngineCredentialsTest.JETTY_LOGGER_PROPERTY;
    private static final String USER_HOME_PROPERTY = "user.home";
    private static final String INITIAL_GAE_RUNTIME_VERSION =
            System.getProperty(GAE_RUNTIME_VERSION_PROPERTY);
    private static final String INITIAL_GAE_RUNTIME_ENVIRONMENT =
            System.getProperty(GAE_RUNTIME_ENVIRONMENT_PROPERTY);
    private static final String INITIAL_JETTY_LOGGER = System.getProperty(JETTY_LOGGER_PROPERTY);

    static {
        configureAppEngineStandardEnvironment();
        AppEngineCredentialsTest.installAppIdentityServiceFactory();
    }

    @TempDir
    Path temporaryHome;

    private String previousUserHome;

    @BeforeEach
    public void setUp() {
        configureAppEngineStandardEnvironment();
        previousUserHome = System.getProperty(USER_HOME_PROPERTY);
        System.setProperty(USER_HOME_PROPERTY, temporaryHome.toString());
    }

    @AfterEach
    public void tearDown() {
        restoreProperty(GAE_RUNTIME_VERSION_PROPERTY, INITIAL_GAE_RUNTIME_VERSION);
        restoreProperty(GAE_RUNTIME_ENVIRONMENT_PROPERTY, INITIAL_GAE_RUNTIME_ENVIRONMENT);
        restoreProperty(JETTY_LOGGER_PROPERTY, INITIAL_JETTY_LOGGER);
        restoreProperty(USER_HOME_PROPERTY, previousUserHome);
    }

    @Test
    public void applicationDefaultCredentialsChecksAppEngineSignalClass() {
        try {
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();

            assertThat(credentials.getClass().getName())
                    .isEqualTo("com.google.auth.oauth2.AppEngineCredentials");
        } catch (IOException exception) {
            assertThat(exception).hasMessageContaining("Google App Engine");
        }
    }

    private static void configureAppEngineStandardEnvironment() {
        AppEngineCredentialsTest.configureAppEngineStandardEnvironment();
    }

    private static void restoreProperty(String name, String value) {
        AppEngineCredentialsTest.restoreProperty(name, value);
    }
}
