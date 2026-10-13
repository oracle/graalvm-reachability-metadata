/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_cloud_sql.jdbc_socket_factory_core;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.sql.ConnectorConfig;
import com.google.cloud.sql.ConnectorRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class InternalConnectorRegistryTest {
    @Test
    @Timeout(55)
    void rejectsDuplicateNamedConnectors() {
        String connectorName = "duplicate-connector";
        GoogleCredentials credentials = GoogleCredentials.create(new AccessToken("offline-access-token", null));
        ConnectorConfig config = new ConnectorConfig.Builder().withGoogleCredentials(credentials).build();
        ConnectorRegistry.reset();

        try {
            ConnectorRegistry.register(connectorName, config);

            assertThatThrownBy(() -> ConnectorRegistry.register(connectorName, config))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Named connection duplicate-connector exists.");
        } finally {
            ConnectorRegistry.reset();
        }
    }
}
