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
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import jakarta.jms.Connection;
import jakarta.jms.DeliveryMode;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.command.Response;
import org.apache.activemq.management.JMSStatsImpl;
import org.apache.activemq.transport.TransportSupport;
import org.apache.activemq.util.IdGenerator;
import org.apache.activemq.util.ServiceStopper;
import org.apache.activemq.wireformat.WireFormat;
import org.junit.jupiter.api.Test;

public class ActiveMQSessionTest {
    @Test
    void sendingForeignMessageSetsDeliveryTimeBeforeTransformingMessage() throws Exception {
        ForeignMessage message = new ForeignMessage();

        try (Connection connection = new TestConnection();
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            Destination destination = session.createQueue("active-mq-session-foreign-message");
            try (MessageProducer producer = session.createProducer(destination)) {
                producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
                producer.send(message);
            }
        }

        assertThat(message.getJMSDeliveryTime()).isPositive();
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
            return "memory://active-mq-session-test";
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

    private static final class ForeignMessage implements Message {
        private final Map<String, Object> properties = new HashMap<>();
        private String messageId;
        private long timestamp;
        private byte[] correlationIdBytes;
        private String correlationId;
        private Destination replyTo;
        private Destination destination;
        private int deliveryMode;
        private boolean redelivered;
        private String type;
        private long expiration;
        private long deliveryTime;
        private int priority;

        @Override
        public String getJMSMessageID() throws JMSException {
            return messageId;
        }

        @Override
        public void setJMSMessageID(String id) throws JMSException {
            this.messageId = id;
        }

        @Override
        public long getJMSTimestamp() throws JMSException {
            return timestamp;
        }

        @Override
        public void setJMSTimestamp(long timestamp) throws JMSException {
            this.timestamp = timestamp;
        }

        @Override
        public byte[] getJMSCorrelationIDAsBytes() throws JMSException {
            return correlationIdBytes;
        }

        @Override
        public void setJMSCorrelationIDAsBytes(byte[] correlationIdBytes) throws JMSException {
            this.correlationIdBytes = correlationIdBytes;
        }

        @Override
        public void setJMSCorrelationID(String correlationId) throws JMSException {
            this.correlationId = correlationId;
        }

        @Override
        public String getJMSCorrelationID() throws JMSException {
            return correlationId;
        }

        @Override
        public Destination getJMSReplyTo() throws JMSException {
            return replyTo;
        }

        @Override
        public void setJMSReplyTo(Destination replyTo) throws JMSException {
            this.replyTo = replyTo;
        }

        @Override
        public Destination getJMSDestination() throws JMSException {
            return destination;
        }

        @Override
        public void setJMSDestination(Destination destination) throws JMSException {
            this.destination = destination;
        }

        @Override
        public int getJMSDeliveryMode() throws JMSException {
            return deliveryMode;
        }

        @Override
        public void setJMSDeliveryMode(int deliveryMode) throws JMSException {
            this.deliveryMode = deliveryMode;
        }

        @Override
        public boolean getJMSRedelivered() throws JMSException {
            return redelivered;
        }

        @Override
        public void setJMSRedelivered(boolean redelivered) throws JMSException {
            this.redelivered = redelivered;
        }

        @Override
        public String getJMSType() throws JMSException {
            return type;
        }

        @Override
        public void setJMSType(String type) throws JMSException {
            this.type = type;
        }

        @Override
        public long getJMSExpiration() throws JMSException {
            return expiration;
        }

        @Override
        public void setJMSExpiration(long expiration) throws JMSException {
            this.expiration = expiration;
        }

        @Override
        public long getJMSDeliveryTime() throws JMSException {
            return deliveryTime;
        }

        @Override
        public void setJMSDeliveryTime(long deliveryTime) throws JMSException {
            this.deliveryTime = deliveryTime;
        }

        @Override
        public int getJMSPriority() throws JMSException {
            return priority;
        }

        @Override
        public void setJMSPriority(int priority) throws JMSException {
            this.priority = priority;
        }

        @Override
        public void clearProperties() throws JMSException {
            properties.clear();
        }

        @Override
        public boolean propertyExists(String name) throws JMSException {
            return properties.containsKey(name);
        }

        @Override
        public boolean getBooleanProperty(String name) throws JMSException {
            return (Boolean) properties.get(name);
        }

        @Override
        public byte getByteProperty(String name) throws JMSException {
            return (Byte) properties.get(name);
        }

        @Override
        public short getShortProperty(String name) throws JMSException {
            return (Short) properties.get(name);
        }

        @Override
        public int getIntProperty(String name) throws JMSException {
            return (Integer) properties.get(name);
        }

        @Override
        public long getLongProperty(String name) throws JMSException {
            return (Long) properties.get(name);
        }

        @Override
        public float getFloatProperty(String name) throws JMSException {
            return (Float) properties.get(name);
        }

        @Override
        public double getDoubleProperty(String name) throws JMSException {
            return (Double) properties.get(name);
        }

        @Override
        public String getStringProperty(String name) throws JMSException {
            return (String) properties.get(name);
        }

        @Override
        public Object getObjectProperty(String name) throws JMSException {
            return properties.get(name);
        }

        @Override
        public Enumeration<String> getPropertyNames() throws JMSException {
            return Collections.enumeration(properties.keySet());
        }

        @Override
        public void setBooleanProperty(String name, boolean value) throws JMSException {
            properties.put(name, value);
        }

        @Override
        public void setByteProperty(String name, byte value) throws JMSException {
            properties.put(name, value);
        }

        @Override
        public void setShortProperty(String name, short value) throws JMSException {
            properties.put(name, value);
        }

        @Override
        public void setIntProperty(String name, int value) throws JMSException {
            properties.put(name, value);
        }

        @Override
        public void setLongProperty(String name, long value) throws JMSException {
            properties.put(name, value);
        }

        @Override
        public void setFloatProperty(String name, float value) throws JMSException {
            properties.put(name, value);
        }

        @Override
        public void setDoubleProperty(String name, double value) throws JMSException {
            properties.put(name, value);
        }

        @Override
        public void setStringProperty(String name, String value) throws JMSException {
            properties.put(name, value);
        }

        @Override
        public void setObjectProperty(String name, Object value) throws JMSException {
            properties.put(name, value);
        }

        @Override
        public void acknowledge() throws JMSException {
        }

        @Override
        public void clearBody() throws JMSException {
        }

        @Override
        public <T> T getBody(Class<T> c) throws JMSException {
            return null;
        }

        @Override
        public boolean isBodyAssignableTo(Class c) throws JMSException {
            return true;
        }
    }
}
