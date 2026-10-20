/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_jms_client;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.MessagePacketI;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises client-side core protocol interceptors of the Artemis JMS client: interceptors
 * named in the `outgoingInterceptorList` and `incomingInterceptorList` connector URL
 * parameters, and interceptors added to the factory's `ServerLocator` in code.
 *
 * A customs gateway stamps its tenant on every outgoing declaration, vetoes declarations
 * bound for embargoed countries before they leave the client, and audits each declaration
 * the clearance desk receives; a second channel counts its sends with a lambda interceptor.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomsDeclarationInterceptorTest {

    static {
        // Like the Artemis distribution, keep the broker audit log off: it is not the subject here.
        System.setProperty("org.slf4j.simpleLogger.log.org.apache.activemq.audit", "warn");
    }

    private static final String DECLARATIONS = "customs.declarations";

    private static final String MANIFESTS = "customs.manifests";

    private static final String TENANT = "harbor-logistics";

    private ActiveMQServer broker;

    private String url;

    @BeforeAll
    void startBroker() throws Exception {
        url = "tcp://127.0.0.1:" + freePort();
        Configuration configuration = new ConfigurationImpl()
                .setPersistenceEnabled(false)
                .setSecurityEnabled(false)
                .addAcceptorConfiguration("netty", url);
        broker = ActiveMQServers.newActiveMQServer(configuration);
        broker.start();
        assertThat(broker.waitForActivation(30, TimeUnit.SECONDS)).isTrue();
    }

    @AfterAll
    void stopBroker() throws Exception {
        if (broker != null) {
            broker.stop();
        }
    }

    @Test
    void urlConfiguredInterceptorsStampVetoAndAuditDeclarations() throws Exception {
        String outgoing = TenantStampingInterceptor.class.getName() + "," + ExportControlInterceptor.class.getName();
        String incoming = ClearanceAuditInterceptor.class.getName();
        ClearanceAuditInterceptor.AUDITED.clear();

        try (ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(
                url + "?outgoingInterceptorList=" + outgoing + "&incomingInterceptorList=" + incoming)) {
            assertThat(factory.getOutgoingInterceptorList()).isEqualTo(outgoing);
            assertThat(factory.getIncomingInterceptorList()).isEqualTo(incoming);

            try (Connection connection = factory.createConnection()) {
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Queue declarations = session.createQueue(DECLARATIONS);
                MessageProducer gateway = session.createProducer(declarations);
                gateway.setDeliveryMode(DeliveryMode.PERSISTENT);

                gateway.send(declaration(session, "D-1", "DE"));
                gateway.send(declaration(session, "D-2", "FR"));
                assertThatThrownBy(() -> gateway.send(declaration(session, "D-3", "KP")))
                        .isInstanceOf(JMSException.class)
                        .hasMessageContaining(ExportControlInterceptor.class.getName());
                gateway.send(declaration(session, "D-4", "JP"));

                MessageConsumer clearanceDesk = session.createConsumer(declarations, "tenant = '" + TENANT + "'");
                connection.start();
                List<String> cleared = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    Message received = clearanceDesk.receive(10_000);
                    assertThat(received).isNotNull();
                    assertThat(received.getStringProperty("tenant")).isEqualTo(TENANT);
                    assertThat(received.getBooleanProperty("audited")).isTrue();
                    cleared.add(received.getStringProperty("declarationId") + ":" + ((TextMessage) received).getText());
                }
                assertThat(cleared).containsExactly("D-1:cargo to DE", "D-2:cargo to FR", "D-4:cargo to JP");
                assertThat(clearanceDesk.receiveNoWait()).isNull();
            }
        }

        assertThat(ClearanceAuditInterceptor.AUDITED).containsExactly("D-1", "D-2", "D-4");
        assertThat(broker.locateQueue(DECLARATIONS).getMessagesAdded()).isEqualTo(3);
    }

    @Test
    void serverLocatorInterceptorsObserveSendsUntilRemoved() throws Exception {
        List<String> sentTo = new CopyOnWriteArrayList<>();
        Interceptor sendCounter = (packet, connection) -> {
            if (packet.getType() == PacketImpl.SESS_SEND && packet instanceof MessagePacketI send) {
                sentTo.add(send.getMessage().getAddress() + "/" + send.getMessage().getStringProperty("manifest"));
            }
            return true;
        };

        try (ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(url)) {
            ServerLocator locator = factory.getServerLocator();
            locator.addOutgoingInterceptor(sendCounter);

            try (JMSContext harbor = factory.createContext()) {
                Queue manifests = harbor.createQueue(MANIFESTS);
                harbor.createProducer().setProperty("manifest", "M-1").send(manifests, "containers 12");
                harbor.createProducer().setProperty("manifest", "M-2").send(manifests, "containers 7");
            }
            assertThat(sentTo).containsExactly(MANIFESTS + "/M-1", MANIFESTS + "/M-2");

            assertThat(locator.removeOutgoingInterceptor(sendCounter)).isTrue();
            try (JMSContext harbor = factory.createContext()) {
                Queue manifests = harbor.createQueue(MANIFESTS);
                harbor.createProducer().setProperty("manifest", "M-3").send(manifests, "containers 3");

                List<String> received = new ArrayList<>();
                try (JMSConsumer customsOffice = harbor.createConsumer(manifests)) {
                    for (int i = 0; i < 3; i++) {
                        Message manifest = customsOffice.receive(10_000);
                        assertThat(manifest).isNotNull();
                        received.add(manifest.getStringProperty("manifest"));
                    }
                }
                assertThat(received).containsExactly("M-1", "M-2", "M-3");
            }
            assertThat(sentTo).hasSize(2);
        }
    }

    private static TextMessage declaration(Session session, String id, String country) throws JMSException {
        TextMessage message = session.createTextMessage("cargo to " + country);
        message.setStringProperty("declarationId", id);
        message.setStringProperty("destinationCountry", country);
        return message;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** Stamps the gateway's tenant on every message the client sends to the broker. */
    public static class TenantStampingInterceptor implements Interceptor {

        public TenantStampingInterceptor() {
        }

        @Override
        public boolean intercept(Packet packet, RemotingConnection connection) throws ActiveMQException {
            if (packet.getType() == PacketImpl.SESS_SEND && packet instanceof MessagePacketI send) {
                send.getMessage().putStringProperty("tenant", TENANT);
            }
            return true;
        }
    }

    /** Refuses to let declarations for embargoed countries leave the client. */
    public static class ExportControlInterceptor implements Interceptor {

        private static final Set<String> EMBARGOED = Set.of("KP", "IR");

        public ExportControlInterceptor() {
        }

        @Override
        public boolean intercept(Packet packet, RemotingConnection connection) throws ActiveMQException {
            if (packet.getType() == PacketImpl.SESS_SEND && packet instanceof MessagePacketI send) {
                return !EMBARGOED.contains(send.getMessage().getStringProperty("destinationCountry"));
            }
            return true;
        }
    }

    /** Records every declaration delivered to the client and marks it as audited. */
    public static class ClearanceAuditInterceptor implements Interceptor {

        static final List<String> AUDITED = new CopyOnWriteArrayList<>();

        public ClearanceAuditInterceptor() {
        }

        @Override
        public boolean intercept(Packet packet, RemotingConnection connection) throws ActiveMQException {
            if (packet.getType() == PacketImpl.SESS_RECEIVE_MSG && packet instanceof MessagePacketI delivery) {
                // `receiveNoWait` also delivers an empty forced-delivery marker; only audit declarations.
                String declarationId = delivery.getMessage().getStringProperty("declarationId");
                if (declarationId != null) {
                    AUDITED.add(declarationId);
                    delivery.getMessage().putBooleanProperty("audited", true);
                }
            }
            return true;
        }
    }
}
