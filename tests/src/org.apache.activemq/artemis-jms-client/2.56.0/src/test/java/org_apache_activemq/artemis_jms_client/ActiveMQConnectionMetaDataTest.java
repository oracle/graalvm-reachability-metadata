/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_jms_client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.ConnectionMetaData;
import javax.management.MBeanServerFactory;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionMetaData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ActiveMQConnectionMetaDataTest {

    private static final String LOOPBACK_ADDRESS = "127.0.0.1";

    @Test
    void readsJmsAndProviderMetadataFromConnectedBroker() throws Exception {
        int port = findAvailablePort();
        Map<String, Object> acceptorParameters = transportParameters(port);
        Configuration configuration = new ConfigurationImpl();
        configuration.addAcceptorConfiguration(
                new TransportConfiguration(NettyAcceptorFactory.class.getName(), acceptorParameters));
        configuration.setSecurityEnabled(false);
        configuration.setJMXManagementEnabled(false);
        configuration.setPersistenceEnabled(false);

        ActiveMQServer server = new ActiveMQServerImpl(configuration, MBeanServerFactory.newMBeanServer());
        ActiveMQConnectionFactory connectionFactory = null;
        try {
            server.start();
            assertThat(server.waitForActivation(30, TimeUnit.SECONDS)).isTrue();

            TransportConfiguration connector = new TransportConfiguration(
                    NettyConnectorFactory.class.getName(), transportParameters(port));
            connectionFactory = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, connector);
            connectionFactory.setCallTimeout(10_000);
            connectionFactory.setCallFailoverTimeout(10_000);
            connectionFactory.setInitialConnectAttempts(1);
            connectionFactory.setReconnectAttempts(0);

            try (Connection connection = connectionFactory.createConnection()) {
                ConnectionMetaData metadata = connection.getMetaData();

                assertThat(metadata).isInstanceOf(ActiveMQConnectionMetaData.class);
                assertThat(metadata.getJMSVersion())
                        .isEqualTo(metadata.getJMSMajorVersion() + "." + metadata.getJMSMinorVersion());
                assertThat(metadata.getJMSProviderName()).isEqualTo("ActiveMQ");
                assertThat(metadata.getProviderVersion()).isNotBlank();
                assertThat(metadata.getProviderMajorVersion()).isGreaterThanOrEqualTo(0);
                assertThat(metadata.getProviderMinorVersion()).isGreaterThanOrEqualTo(0);
                assertThat(Collections.list(metadata.getJMSXPropertyNames()))
                        .containsExactly("JMSXGroupID", "JMSXGroupSeq", "JMSXDeliveryCount");
            }
        } finally {
            try {
                if (connectionFactory != null) {
                    connectionFactory.close();
                }
            } finally {
                server.stop();
            }
        }
    }

    private static Map<String, Object> transportParameters(int port) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(TransportConstants.HOST_PROP_NAME, LOOPBACK_ADDRESS);
        parameters.put(TransportConstants.PORT_PROP_NAME, port);
        return parameters;
    }

    private static int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(LOOPBACK_ADDRESS, 0));
            return socket.getLocalPort();
        }
    }
}
