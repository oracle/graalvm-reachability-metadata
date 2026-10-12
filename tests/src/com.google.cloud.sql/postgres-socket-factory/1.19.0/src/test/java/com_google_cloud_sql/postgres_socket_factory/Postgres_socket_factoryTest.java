/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_cloud_sql.postgres_socket_factory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.google.cloud.sql.ConnectorRegistry;
import com.google.cloud.sql.postgres.SocketFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

public class Postgres_socket_factoryTest {
    private static final String JDBC_URL = "jdbc:postgresql:///postgres"
            + "?socketFactory=com.google.cloud.sql.postgres.SocketFactory"
            + "&cloudSqlInstance=sample-project:us-central1:sample-instance";
    private static final String WORKLOAD_IDENTITY_AUDIENCE =
            "//iam.googleapis.com/projects/123456789/locations/global/"
                    + "workloadIdentityPools/test-pool/providers/test-provider";
    private static final String EXTERNAL_ACCOUNT_CREDENTIALS = """
            {
              "type": "external_account",
              "audience": "%s",
              "subject_token_type": "urn:ietf:params:oauth:token-type:jwt",
              "token_url": "https://127.0.0.1:1/token",
              "credential_source": {
                "url": "http://127.0.0.1:1/subject-token"
              }
            }
            """.formatted(WORKLOAD_IDENTITY_AUDIENCE);

    @Test
    void constructorTranslatesDeprecatedSocketFactoryArgument() {
        String instanceConnectionName = "sample-project:us-central1:legacy-instance";
        Properties properties = new Properties();
        properties.setProperty("SocketFactoryArg", instanceConnectionName);

        new SocketFactory(properties);

        assertThat(properties.getProperty("cloudSqlInstance")).isEqualTo(instanceConnectionName);
    }

    @Test
    @Timeout(55)
    void jdbcDriverLoadsSocketFactoryAndReportsOfflineAuthenticationFailure(
            @TempDir Path temporaryDirectory) throws IOException {
        Path credentialsFile = temporaryDirectory.resolve("external-account-credentials.json");
        Files.writeString(credentialsFile, EXTERNAL_ACCOUNT_CREDENTIALS);

        Properties properties = new Properties();
        properties.setProperty("user", "postgres");
        properties.setProperty("password", "offline-password");
        properties.setProperty("cloudSqlGoogleCredentialsPath", credentialsFile.toString());
        properties.setProperty("cloudSqlAdminRootUrl", "http://127.0.0.1:1/");
        properties.setProperty("cloudSqlRefreshStrategy", "lazy");
        properties.setProperty("connectTimeout", "10");
        properties.setProperty("socketTimeout", "10");

        try {
            Throwable failure = catchThrowable(() -> DriverManager.getConnection(JDBC_URL, properties));

            assertThat(failure).isInstanceOfAny(SQLException.class, RuntimeException.class);
            assertThat(failure).hasRootCauseInstanceOf(IOException.class);
        } finally {
            ConnectorRegistry.reset();
        }
    }
}
