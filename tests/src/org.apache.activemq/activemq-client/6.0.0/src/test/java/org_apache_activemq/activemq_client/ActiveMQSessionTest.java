/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.command.ActiveMQMessage;
import org.junit.jupiter.api.Test;

import jakarta.jms.Connection;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import java.util.Enumeration;

import static org.assertj.core.api.Assertions.assertThat;

public class ActiveMQSessionTest {

    @Test
    void sendsAProviderNeutralJmsMessageWithDeliveryTime() throws Exception {
        BrokerService broker = new BrokerService();
        broker.setBrokerName("foreign-message-test");
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.start();
        broker.waitUntilStarted();

        try (Connection connection = new ActiveMQConnectionFactory("vm://foreign-message-test?create=false")
                .createConnection()) {
            connection.start();
            try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
                Destination queue = session.createQueue("foreign.message");
                ForeignMessage sent = new ForeignMessage();
                sent.setStringProperty("kind", "provider-neutral");
                try (MessageProducer producer = session.createProducer(queue)) {
                    producer.send(sent);
                }
                try (MessageConsumer consumer = session.createConsumer(queue)) {
                    Message received = consumer.receive(10000);
                    assertThat(received.getStringProperty("kind")).isEqualTo("provider-neutral");
                    assertThat(sent.getJMSDeliveryTime()).isPositive();
                }
            }
        } finally {
            broker.stop();
            broker.waitUntilStopped();
        }
    }

    public static final class ForeignMessage implements Message {
        private final ActiveMQMessage delegate = new ActiveMQMessage();

        @Override
        public String getJMSMessageID() throws JMSException {
            return delegate.getJMSMessageID();
        }
        @Override
        public void setJMSMessageID(String value) throws JMSException {
            delegate.setJMSMessageID(value);
        }
        @Override
        public long getJMSTimestamp() throws JMSException {
            return delegate.getJMSTimestamp();
        }
        @Override
        public void setJMSTimestamp(long value) throws JMSException {
            delegate.setJMSTimestamp(value);
        }
        @Override
        public byte[] getJMSCorrelationIDAsBytes() throws JMSException {
            return delegate.getJMSCorrelationIDAsBytes();
        }
        @Override
        public void setJMSCorrelationIDAsBytes(byte[] value) throws JMSException {
            delegate.setJMSCorrelationIDAsBytes(value);
        }
        @Override
        public String getJMSCorrelationID() throws JMSException {
            return delegate.getJMSCorrelationID();
        }
        @Override
        public void setJMSCorrelationID(String value) throws JMSException {
            delegate.setJMSCorrelationID(value);
        }
        @Override
        public Destination getJMSReplyTo() throws JMSException {
            return delegate.getJMSReplyTo();
        }
        @Override
        public void setJMSReplyTo(Destination value) throws JMSException {
            delegate.setJMSReplyTo(value);
        }
        @Override
        public Destination getJMSDestination() throws JMSException {
            return delegate.getJMSDestination();
        }
        @Override
        public void setJMSDestination(Destination value) throws JMSException {
            delegate.setJMSDestination(value);
        }
        @Override
        public int getJMSDeliveryMode() throws JMSException {
            return delegate.getJMSDeliveryMode();
        }
        @Override
        public void setJMSDeliveryMode(int value) throws JMSException {
            delegate.setJMSDeliveryMode(value);
        }
        @Override
        public boolean getJMSRedelivered() throws JMSException {
            return delegate.getJMSRedelivered();
        }
        @Override
        public void setJMSRedelivered(boolean value) throws JMSException {
            delegate.setJMSRedelivered(value);
        }
        @Override
        public String getJMSType() throws JMSException {
            return delegate.getJMSType();
        }
        @Override
        public void setJMSType(String value) throws JMSException {
            delegate.setJMSType(value);
        }
        @Override
        public long getJMSExpiration() throws JMSException {
            return delegate.getJMSExpiration();
        }
        @Override
        public void setJMSExpiration(long value) throws JMSException {
            delegate.setJMSExpiration(value);
        }
        @Override
        public long getJMSDeliveryTime() throws JMSException {
            return delegate.getJMSDeliveryTime();
        }
        @Override
        public void setJMSDeliveryTime(long value) throws JMSException {
            delegate.setJMSDeliveryTime(value);
        }
        @Override
        public int getJMSPriority() throws JMSException {
            return delegate.getJMSPriority();
        }
        @Override
        public void setJMSPriority(int value) throws JMSException {
            delegate.setJMSPriority(value);
        }
        @Override
        public void clearProperties() throws JMSException {
            delegate.clearProperties();
        }
        @Override
        public boolean propertyExists(String name) throws JMSException {
            return delegate.propertyExists(name);
        }
        @Override
        public boolean getBooleanProperty(String name) throws JMSException {
            return delegate.getBooleanProperty(name);
        }
        @Override
        public byte getByteProperty(String name) throws JMSException {
            return delegate.getByteProperty(name);
        }
        @Override
        public short getShortProperty(String name) throws JMSException {
            return delegate.getShortProperty(name);
        }
        @Override
        public int getIntProperty(String name) throws JMSException {
            return delegate.getIntProperty(name);
        }
        @Override
        public long getLongProperty(String name) throws JMSException {
            return delegate.getLongProperty(name);
        }
        @Override
        public float getFloatProperty(String name) throws JMSException {
            return delegate.getFloatProperty(name);
        }
        @Override
        public double getDoubleProperty(String name) throws JMSException {
            return delegate.getDoubleProperty(name);
        }
        @Override
        public String getStringProperty(String name) throws JMSException {
            return delegate.getStringProperty(name);
        }
        @Override
        public Object getObjectProperty(String name) throws JMSException {
            return delegate.getObjectProperty(name);
        }
        @Override
        public Enumeration<String> getPropertyNames() throws JMSException {
            return delegate.getPropertyNames();
        }
        @Override
        public void setBooleanProperty(String name, boolean value) throws JMSException {
            delegate.setBooleanProperty(name, value);
        }
        @Override
        public void setByteProperty(String name, byte value) throws JMSException {
            delegate.setByteProperty(name, value);
        }
        @Override
        public void setShortProperty(String name, short value) throws JMSException {
            delegate.setShortProperty(name, value);
        }
        @Override
        public void setIntProperty(String name, int value) throws JMSException {
            delegate.setIntProperty(name, value);
        }
        @Override
        public void setLongProperty(String name, long value) throws JMSException {
            delegate.setLongProperty(name, value);
        }
        @Override
        public void setFloatProperty(String name, float value) throws JMSException {
            delegate.setFloatProperty(name, value);
        }
        @Override
        public void setDoubleProperty(String name, double value) throws JMSException {
            delegate.setDoubleProperty(name, value);
        }
        @Override
        public void setStringProperty(String name, String value) throws JMSException {
            delegate.setStringProperty(name, value);
        }
        @Override
        public void setObjectProperty(String name, Object value) throws JMSException {
            delegate.setObjectProperty(name, value);
        }
        @Override
        public void acknowledge() throws JMSException {
            delegate.acknowledge();
        }
        @Override
        public void clearBody() throws JMSException {
            delegate.clearBody();
        }
        @Override
        public <T> T getBody(Class<T> type) throws JMSException {
            return delegate.getBody(type);
        }
        @Override
        public boolean isBodyAssignableTo(Class type) throws JMSException {
            return delegate.isBodyAssignableTo(type);
        }
    }
}
