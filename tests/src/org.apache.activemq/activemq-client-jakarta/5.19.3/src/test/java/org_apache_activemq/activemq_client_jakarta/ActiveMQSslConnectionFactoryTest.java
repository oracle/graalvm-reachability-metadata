/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client_jakarta;

import java.util.UUID;

import jakarta.jms.Connection;

import org.apache.activemq.ActiveMQSslConnectionFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ActiveMQSslConnectionFactoryTest {

    private static final int CLOSE_TIMEOUT_MILLIS = 10_000;
    private static final String KEY_STORE_PASSWORD = "changeit";

    @Test
    void createsConnectionWithKeyStoreLoadedFromContextClassLoaderResource() throws Exception {
        String brokerName = "ssl-keystore-resource-" + UUID.randomUUID();
        String resourceName = "activemq/ssl/client-keystore.p12";
        String keyStoreType = "PKCS12";
        ActiveMQSslConnectionFactory connectionFactory = new ActiveMQSslConnectionFactory(
                "vm://" + brokerName + "?broker.persistent=false&broker.useJmx=false");
        connectionFactory.setCloseTimeout(CLOSE_TIMEOUT_MILLIS);
        connectionFactory.setKeyStoreType(keyStoreType);
        connectionFactory.setKeyStore(resourceName);
        connectionFactory.setKeyStorePassword(KEY_STORE_PASSWORD);
        connectionFactory.setKeyStoreKeyPassword(KEY_STORE_PASSWORD);

        try (Connection connection = connectionFactory.createConnection()) {
            assertThat(connection).isNotNull();
            assertThat(connectionFactory.getKeyStore()).isEqualTo(resourceName);
        }
    }
}
