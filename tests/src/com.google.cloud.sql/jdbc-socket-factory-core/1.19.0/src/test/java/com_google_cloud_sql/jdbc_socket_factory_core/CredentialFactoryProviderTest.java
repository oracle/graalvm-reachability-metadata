/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_cloud_sql.jdbc_socket_factory_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.sql.ConnectorConfig;
import com.google.cloud.sql.ConnectorRegistry;
import com.google.cloud.sql.CredentialFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class CredentialFactoryProviderTest {
    @Test
    @Timeout(55)
    void connectorRegistryUsesConfiguredCredentialFactory() {
        String propertyName = CredentialFactory.CREDENTIAL_FACTORY_PROPERTY;
        String previousFactory = System.getProperty(propertyName);
        String connectorName = "custom-credentials";
        RecordingCredentialFactory.reset();
        ConnectorRegistry.reset();

        try {
            System.setProperty(propertyName, RecordingCredentialFactory.class.getName());
            ConnectorConfig config = new ConnectorConfig.Builder().build();

            ConnectorRegistry.register(connectorName, config);

            assertThat(RecordingCredentialFactory.constructorCalls).isEqualTo(1);
            assertThat(RecordingCredentialFactory.initializationCalls).isGreaterThanOrEqualTo(2);
            ConnectorRegistry.close(connectorName);
        } finally {
            ConnectorRegistry.reset();
            if (previousFactory == null) {
                System.clearProperty(propertyName);
            } else {
                System.setProperty(propertyName, previousFactory);
            }
        }
    }

    public static final class RecordingCredentialFactory implements CredentialFactory {
        private static int constructorCalls;
        private static int initializationCalls;

        public RecordingCredentialFactory() {
            constructorCalls++;
        }

        @Override
        public HttpRequestInitializer create() {
            initializationCalls++;
            GoogleCredentials credentials =
                    GoogleCredentials.create(new AccessToken("offline-access-token", null));
            return new HttpCredentialsAdapter(credentials);
        }

        private static void reset() {
            constructorCalls = 0;
            initializationCalls = 0;
        }
    }
}
