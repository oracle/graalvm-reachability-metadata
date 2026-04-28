/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_rabbitmq.amqp_client;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ConnectionFactoryConfigurator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConnectionFactoryConfiguratorTest {
    @Test
    void loadReadsConnectionSettingsFromClasspathResource() throws Exception {
        ConnectionFactory connectionFactory = new ConnectionFactory();

        ConnectionFactoryConfigurator.load(
            connectionFactory,
            "classpath:/connection-factory-configurator.properties",
            ConnectionFactoryConfigurator.DEFAULT_PREFIX
        );

        assertThat(connectionFactory.getUsername()).isEqualTo("resource-user");
        assertThat(connectionFactory.getPassword()).isEqualTo("resource-password");
        assertThat(connectionFactory.getVirtualHost()).isEqualTo("resource-vhost");
        assertThat(connectionFactory.getHost()).isEqualTo("resource-host");
        assertThat(connectionFactory.getPort()).isEqualTo(5678);
        assertThat(connectionFactory.getConnectionTimeout()).isEqualTo(1234);
    }
}
