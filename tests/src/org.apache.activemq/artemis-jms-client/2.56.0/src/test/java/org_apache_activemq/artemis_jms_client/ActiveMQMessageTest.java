/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_jms_client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.Session;
import javax.management.MBeanServerFactory;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
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
import org.apache.activemq.artemis.jms.client.ActiveMQMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQSession;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ActiveMQMessageTest {

    private static final String LOOPBACK_ADDRESS = "127.0.0.1";
    private static final String EXPECTED_BODY = "body supplied by an Artemis core client";
    private static final int TIMEOUT_MILLIS = 10_000;

    @Test
    void readsSerializedCoreMessageBodyThroughJmsApi() throws Exception {
        int port = findAvailablePort();
        Configuration configuration = new ConfigurationImpl();
        configuration.addAcceptorConfiguration(
                new TransportConfiguration(NettyAcceptorFactory.class.getName(), transportParameters(port)));
        configuration.setSecurityEnabled(false);
        configuration.setJMXManagementEnabled(false);
        configuration.setPersistenceEnabled(false);

        ActiveMQServer server = new ActiveMQServerImpl(configuration, MBeanServerFactory.newMBeanServer());
        ActiveMQConnectionFactory connectionFactory = null;
        try {
            server.start();
            assertThat(server.waitForActivation(30, TimeUnit.SECONDS)).isTrue();

            TransportConfiguration connector =
                    new TransportConfiguration(NettyConnectorFactory.class.getName(), connectorParameters(port));
            connectionFactory = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, connector);
            connectionFactory.setCallTimeout(TIMEOUT_MILLIS);
            connectionFactory.setCallFailoverTimeout(TIMEOUT_MILLIS);
            connectionFactory.setInitialConnectAttempts(1);
            connectionFactory.setReconnectAttempts(0);

            try (Connection connection = connectionFactory.createConnection();
                    Session jmsSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
                assertThat(jmsSession).isInstanceOf(ActiveMQSession.class);
                ClientSession coreSession = ((ActiveMQSession) jmsSession).getCoreSession();
                ClientMessage coreMessage = coreSession.createMessage(ActiveMQMessage.TYPE, false);
                coreMessage.setBodyInputStream(new ByteArrayInputStream(serialize(EXPECTED_BODY)));

                ActiveMQMessage message = ActiveMQMessage.createMessage(coreMessage, coreSession);
                assertThat(message).isExactlyInstanceOf(ActiveMQMessage.class);
                assertThat(message.getBody(String.class)).isEqualTo(EXPECTED_BODY);
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

    private static byte[] serialize(String body) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(body);
        }
        return bytes.toByteArray();
    }

    private static Map<String, Object> connectorParameters(int port) {
        Map<String, Object> parameters = transportParameters(port);
        parameters.put(TransportConstants.NETTY_CONNECT_TIMEOUT, TIMEOUT_MILLIS);
        return parameters;
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
