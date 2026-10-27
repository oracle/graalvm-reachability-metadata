/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client_jakarta;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.jms.Connection;
import jakarta.jms.DeliveryMode;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ActiveMQSessionTest {

    private static final int SEND_TIMEOUT_MILLIS = 10_000;

    @Test
    void producerSendSetsDeliveryTimeOnForeignJakartaMessage() throws JMSException {
        String brokerName = "session-foreign-message-" + UUID.randomUUID();
        String brokerUrl = "vm://" + brokerName + "?broker.persistent=false&broker.useJmx=false";
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(brokerUrl);
        connectionFactory.setSendTimeout(SEND_TIMEOUT_MILLIS);

        try (Connection connection = connectionFactory.createConnection();
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            Queue queue = session.createQueue("queue." + brokerName);
            try (MessageProducer producer = session.createProducer(queue)) {
                ForeignJakartaMessage foreignMessage = new ForeignJakartaMessage();
                producer.send(foreignMessage, DeliveryMode.NON_PERSISTENT, 4, 0L);

                assertThat(foreignMessage.getJMSDeliveryTime()).isGreaterThan(0L);
                assertThat(foreignMessage.getJMSMessageID()).startsWith("ID:");
                assertThat(foreignMessage.getJMSDestination()).isEqualTo(queue);
            }
        }
    }

    public static class ForeignJakartaMessage implements Message {

        private final Map<String, Object> properties = new LinkedHashMap<>();
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
        public String getJMSMessageID() {
            return messageId;
        }

        @Override
        public void setJMSMessageID(String messageId) {
            this.messageId = messageId;
        }

        @Override
        public long getJMSTimestamp() {
            return timestamp;
        }

        @Override
        public void setJMSTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }

        @Override
        public byte[] getJMSCorrelationIDAsBytes() {
            return correlationIdBytes;
        }

        @Override
        public void setJMSCorrelationIDAsBytes(byte[] correlationIdBytes) {
            this.correlationIdBytes = correlationIdBytes;
        }

        @Override
        public void setJMSCorrelationID(String correlationId) {
            this.correlationId = correlationId;
        }

        @Override
        public String getJMSCorrelationID() {
            return correlationId;
        }

        @Override
        public Destination getJMSReplyTo() {
            return replyTo;
        }

        @Override
        public void setJMSReplyTo(Destination replyTo) {
            this.replyTo = replyTo;
        }

        @Override
        public Destination getJMSDestination() {
            return destination;
        }

        @Override
        public void setJMSDestination(Destination destination) {
            this.destination = destination;
        }

        @Override
        public int getJMSDeliveryMode() {
            return deliveryMode;
        }

        @Override
        public void setJMSDeliveryMode(int deliveryMode) {
            this.deliveryMode = deliveryMode;
        }

        @Override
        public boolean getJMSRedelivered() {
            return redelivered;
        }

        @Override
        public void setJMSRedelivered(boolean redelivered) {
            this.redelivered = redelivered;
        }

        @Override
        public String getJMSType() {
            return type;
        }

        @Override
        public void setJMSType(String type) {
            this.type = type;
        }

        @Override
        public long getJMSExpiration() {
            return expiration;
        }

        @Override
        public void setJMSExpiration(long expiration) {
            this.expiration = expiration;
        }

        @Override
        public long getJMSDeliveryTime() {
            return deliveryTime;
        }

        @Override
        public void setJMSDeliveryTime(long deliveryTime) {
            this.deliveryTime = deliveryTime;
        }

        @Override
        public int getJMSPriority() {
            return priority;
        }

        @Override
        public void setJMSPriority(int priority) {
            this.priority = priority;
        }

        @Override
        public void clearProperties() {
            properties.clear();
        }

        @Override
        public boolean propertyExists(String name) {
            return properties.containsKey(name);
        }

        @Override
        public boolean getBooleanProperty(String name) {
            return (Boolean) properties.get(name);
        }

        @Override
        public byte getByteProperty(String name) {
            return (Byte) properties.get(name);
        }

        @Override
        public short getShortProperty(String name) {
            return (Short) properties.get(name);
        }

        @Override
        public int getIntProperty(String name) {
            return (Integer) properties.get(name);
        }

        @Override
        public long getLongProperty(String name) {
            return (Long) properties.get(name);
        }

        @Override
        public float getFloatProperty(String name) {
            return (Float) properties.get(name);
        }

        @Override
        public double getDoubleProperty(String name) {
            return (Double) properties.get(name);
        }

        @Override
        public String getStringProperty(String name) {
            Object value = properties.get(name);
            return value == null ? null : value.toString();
        }

        @Override
        public Object getObjectProperty(String name) {
            return properties.get(name);
        }

        @Override
        public Enumeration<String> getPropertyNames() {
            return Collections.enumeration(properties.keySet());
        }

        @Override
        public void setBooleanProperty(String name, boolean value) {
            properties.put(name, value);
        }

        @Override
        public void setByteProperty(String name, byte value) {
            properties.put(name, value);
        }

        @Override
        public void setShortProperty(String name, short value) {
            properties.put(name, value);
        }

        @Override
        public void setIntProperty(String name, int value) {
            properties.put(name, value);
        }

        @Override
        public void setLongProperty(String name, long value) {
            properties.put(name, value);
        }

        @Override
        public void setFloatProperty(String name, float value) {
            properties.put(name, value);
        }

        @Override
        public void setDoubleProperty(String name, double value) {
            properties.put(name, value);
        }

        @Override
        public void setStringProperty(String name, String value) {
            properties.put(name, value);
        }

        @Override
        public void setObjectProperty(String name, Object value) {
            properties.put(name, value);
        }

        @Override
        public void acknowledge() {
        }

        @Override
        public void clearBody() {
        }

        @Override
        public <T> T getBody(Class<T> c) {
            return null;
        }

        @Override
        public boolean isBodyAssignableTo(Class c) {
            return false;
        }
    }
}
