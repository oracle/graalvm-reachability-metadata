/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auth.google_auth_library_oauth2_http;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.auth.oauth2.GoogleCredentials;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DefaultCredentialsProviderTest {
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
    public void applicationDefaultCredentialsChecksAppEngineSignalClass() throws Exception {
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();

        assertThat(credentials.getClass().getName())
                .isEqualTo("com.google.auth.oauth2.AppEngineCredentials");
    }
}
