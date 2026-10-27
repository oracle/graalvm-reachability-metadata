/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client;

import org.apache.activemq.ActiveMQSslConnectionFactory;
import org.junit.jupiter.api.Test;

import jakarta.jms.Connection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class ActiveMQSslConnectionFactoryTest {

    @Test
    void createsAConnectionWithAClasspathKeyStore() throws Exception {
        String brokerName = "ssl-resource-" + UUID.randomUUID();
        String keyStoreResource = "activemq/ssl/client-keystore.p12";
        ActiveMQSslConnectionFactory factory = new ActiveMQSslConnectionFactory(
                "vm://" + brokerName + "?broker.persistent=false&broker.useJmx=false");
        factory.setCloseTimeout(10000);
        factory.setKeyStoreType("PKCS12");
        factory.setKeyStore(keyStoreResource);
        factory.setKeyStorePassword("changeit");
        factory.setKeyStoreKeyPassword("changeit");

        try (Connection connection = factory.createConnection()) {
            assertThat(connection).isNotNull();
            assertThat(factory.getKeyStore()).isEqualTo(keyStoreResource);
        }
    }
}
