/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client_jakarta;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.security.cert.X509Certificate;

import jakarta.jms.Connection;
import jakarta.jms.Message;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.command.Response;
import org.apache.activemq.management.JMSStatsImpl;
import org.apache.activemq.transport.TransportSupport;
import org.apache.activemq.util.IdGenerator;
import org.apache.activemq.util.ProducerThread;
import org.apache.activemq.util.ServiceStopper;
import org.apache.activemq.wireformat.WireFormat;
import org.junit.jupiter.api.Test;

public class ProducerThreadTest {
    @Test
    void createMessageLoadsTextPayloadFromClasspathResource() throws Exception {
        try (Connection connection = new TestConnection();
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            TestProducerThread producerThread = new TestProducerThread(session);
            producerThread.setTextMessageSize(19);

            Message message = producerThread.createTestMessage(7);

            assertThat(message).isInstanceOf(TextMessage.class);
            assertThat(((TextMessage) message).getText()).isEqualTo("producer-demo-paylo");
        }
    }

    private static final class TestProducerThread extends ProducerThread {
        TestProducerThread(Session session) {
            super(session, null);
        }

        Message createTestMessage(int index) throws Exception {
            return createMessage(index);
        }
    }

    private static final class TestConnection extends ActiveMQConnection {
        TestConnection() throws Exception {
            super(new NoOpTransport(), new IdGenerator(), new IdGenerator(), new JMSStatsImpl());
            setWatchTopicAdvisories(false);
        }
    }

    private static final class NoOpTransport extends TransportSupport {
        @Override
        public void oneway(Object command) throws IOException {
        }

        @Override
        public Object request(Object command) throws IOException {
            return new Response();
        }

        @Override
        public Object request(Object command, int timeout) throws IOException {
            return new Response();
        }

        @Override
        public String getRemoteAddress() {
            return "memory://producer-thread-test";
        }

        @Override
        public int getReceiveCounter() {
            return 0;
        }

        @Override
        public X509Certificate[] getPeerCertificates() {
            return null;
        }

        @Override
        public void setPeerCertificates(X509Certificate[] certificates) {
        }

        @Override
        public WireFormat getWireFormat() {
            return null;
        }

        @Override
        protected void doStart() throws Exception {
        }

        @Override
        protected void doStop(ServiceStopper stopper) throws Exception {
        }
    }
}
