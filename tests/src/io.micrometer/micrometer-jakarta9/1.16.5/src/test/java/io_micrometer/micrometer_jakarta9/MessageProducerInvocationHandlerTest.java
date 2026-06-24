/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_jakarta9;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import io.micrometer.jakarta9.instrument.jms.JmsInstrumentation;
import io.micrometer.observation.ObservationRegistry;
import jakarta.jms.BytesMessage;
import jakarta.jms.CompletionListener;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.MapMessage;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;
import jakarta.jms.MessageProducer;
import jakarta.jms.ObjectMessage;
import jakarta.jms.Queue;
import jakarta.jms.QueueBrowser;
import jakarta.jms.Session;
import jakarta.jms.StreamMessage;
import jakarta.jms.TemporaryQueue;
import jakarta.jms.TemporaryTopic;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;
import jakarta.jms.TopicSubscriber;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MessageProducerInvocationHandlerTest {

    @Test
    void instrumentedProducerDelegatesNonSendCallsToTarget() throws JMSException {
        RecordingMessageProducer targetProducer = new RecordingMessageProducer();
        Session session = JmsInstrumentation.instrumentSession(new RecordingSession(targetProducer),
                ObservationRegistry.create());

        MessageProducer instrumentedProducer = session.createProducer(null);

        assertThat(instrumentedProducer).isNotSameAs(targetProducer);
        assertThat(instrumentedProducer.getPriority()).isEqualTo(7);
        assertThat(targetProducer.getPriorityCalls).isEqualTo(1);
    }

    @Test
    void instrumentedProducerDelegatesSendCallsToTarget() throws JMSException {
        RecordingMessageProducer targetProducer = new RecordingMessageProducer();
        Session session = JmsInstrumentation.instrumentSession(new RecordingSession(targetProducer),
                ObservationRegistry.create());
        MessageProducer instrumentedProducer = session.createProducer(null);
        RecordingMessage message = new RecordingMessage(new RecordingQueue("orders"));

        instrumentedProducer.send(message);

        assertThat(targetProducer.sendCalls).isEqualTo(1);
        assertThat(targetProducer.sentMessage).isSameAs(message);
    }

    private static final class RecordingMessageProducer implements MessageProducer {

        private Message sentMessage;

        private int getPriorityCalls;

        private int sendCalls;

        @Override
        public void setDisableMessageID(boolean value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean getDisableMessageID() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setDisableMessageTimestamp(boolean value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean getDisableMessageTimestamp() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setDeliveryMode(int deliveryMode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getDeliveryMode() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setPriority(int defaultPriority) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getPriority() {
            this.getPriorityCalls++;
            return 7;
        }

        @Override
        public void setTimeToLive(long timeToLive) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getTimeToLive() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setDeliveryDelay(long deliveryDelay) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getDeliveryDelay() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Destination getDestination() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
        }

        @Override
        public void send(Message message) {
            recordSend(message);
        }

        @Override
        public void send(Message message, int deliveryMode, int priority, long timeToLive) {
            recordSend(message);
        }

        @Override
        public void send(Destination destination, Message message) {
            recordSend(message);
        }

        @Override
        public void send(Destination destination, Message message, int deliveryMode, int priority, long timeToLive) {
            recordSend(message);
        }

        @Override
        public void send(Message message, CompletionListener completionListener) {
            recordSend(message);
        }

        @Override
        public void send(Message message, int deliveryMode, int priority, long timeToLive,
                CompletionListener completionListener) {
            recordSend(message);
        }

        @Override
        public void send(Destination destination, Message message, CompletionListener completionListener) {
            recordSend(message);
        }

        @Override
        public void send(Destination destination, Message message, int deliveryMode, int priority, long timeToLive,
                CompletionListener completionListener) {
            recordSend(message);
        }

        private void recordSend(Message message) {
            this.sentMessage = message;
            this.sendCalls++;
        }

    }

    private static final class RecordingMessage implements Message {

        private final Destination destination;

        private final Map<String, Object> properties = new HashMap<>();

        private String correlationId = "conversation-1";

        private String messageId = "message-1";

        private RecordingMessage(Destination destination) {
            this.destination = destination;
        }

        @Override
        public String getJMSMessageID() {
            return this.messageId;
        }

        @Override
        public void setJMSMessageID(String id) {
            this.messageId = id;
        }

        @Override
        public long getJMSTimestamp() {
            return 0;
        }

        @Override
        public void setJMSTimestamp(long timestamp) {
        }

        @Override
        public byte[] getJMSCorrelationIDAsBytes() {
            return this.correlationId.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void setJMSCorrelationIDAsBytes(byte[] correlationId) {
            this.correlationId = new String(correlationId, StandardCharsets.UTF_8);
        }

        @Override
        public void setJMSCorrelationID(String correlationId) {
            this.correlationId = correlationId;
        }

        @Override
        public String getJMSCorrelationID() {
            return this.correlationId;
        }

        @Override
        public Destination getJMSReplyTo() {
            return null;
        }

        @Override
        public void setJMSReplyTo(Destination replyTo) {
        }

        @Override
        public Destination getJMSDestination() {
            return this.destination;
        }

        @Override
        public void setJMSDestination(Destination destination) {
        }

        @Override
        public int getJMSDeliveryMode() {
            return Message.DEFAULT_DELIVERY_MODE;
        }

        @Override
        public void setJMSDeliveryMode(int deliveryMode) {
        }

        @Override
        public boolean getJMSRedelivered() {
            return false;
        }

        @Override
        public void setJMSRedelivered(boolean redelivered) {
        }

        @Override
        public String getJMSType() {
            return null;
        }

        @Override
        public void setJMSType(String type) {
        }

        @Override
        public long getJMSExpiration() {
            return 0;
        }

        @Override
        public void setJMSExpiration(long expiration) {
        }

        @Override
        public long getJMSDeliveryTime() {
            return 0;
        }

        @Override
        public void setJMSDeliveryTime(long deliveryTime) {
        }

        @Override
        public int getJMSPriority() {
            return Message.DEFAULT_PRIORITY;
        }

        @Override
        public void setJMSPriority(int priority) {
        }

        @Override
        public void clearProperties() {
            this.properties.clear();
        }

        @Override
        public boolean propertyExists(String name) {
            return this.properties.containsKey(name);
        }

        @Override
        public boolean getBooleanProperty(String name) {
            return (Boolean) this.properties.get(name);
        }

        @Override
        public byte getByteProperty(String name) {
            return (Byte) this.properties.get(name);
        }

        @Override
        public short getShortProperty(String name) {
            return (Short) this.properties.get(name);
        }

        @Override
        public int getIntProperty(String name) {
            return (Integer) this.properties.get(name);
        }

        @Override
        public long getLongProperty(String name) {
            return (Long) this.properties.get(name);
        }

        @Override
        public float getFloatProperty(String name) {
            return (Float) this.properties.get(name);
        }

        @Override
        public double getDoubleProperty(String name) {
            return (Double) this.properties.get(name);
        }

        @Override
        public String getStringProperty(String name) {
            Object value = this.properties.get(name);
            return value != null ? value.toString() : null;
        }

        @Override
        public Object getObjectProperty(String name) {
            return this.properties.get(name);
        }

        @Override
        public Enumeration<String> getPropertyNames() {
            return Collections.enumeration(this.properties.keySet());
        }

        @Override
        public void setBooleanProperty(String name, boolean value) {
            this.properties.put(name, value);
        }

        @Override
        public void setByteProperty(String name, byte value) {
            this.properties.put(name, value);
        }

        @Override
        public void setShortProperty(String name, short value) {
            this.properties.put(name, value);
        }

        @Override
        public void setIntProperty(String name, int value) {
            this.properties.put(name, value);
        }

        @Override
        public void setLongProperty(String name, long value) {
            this.properties.put(name, value);
        }

        @Override
        public void setFloatProperty(String name, float value) {
            this.properties.put(name, value);
        }

        @Override
        public void setDoubleProperty(String name, double value) {
            this.properties.put(name, value);
        }

        @Override
        public void setStringProperty(String name, String value) {
            this.properties.put(name, value);
        }

        @Override
        public void setObjectProperty(String name, Object value) {
            this.properties.put(name, value);
        }

        @Override
        public void acknowledge() {
        }

        @Override
        public void clearBody() {
        }

        @Override
        public <T> T getBody(Class<T> type) {
            return null;
        }

        @Override
        public boolean isBodyAssignableTo(Class type) {
            return false;
        }

    }

    private static final class RecordingQueue implements Queue {

        private final String name;

        private RecordingQueue(String name) {
            this.name = name;
        }

        @Override
        public String getQueueName() {
            return this.name;
        }

    }

    private static final class RecordingSession implements Session {

        private final MessageProducer messageProducer;

        private RecordingSession(MessageProducer messageProducer) {
            this.messageProducer = messageProducer;
        }

        @Override
        public MessageProducer createProducer(Destination destination) {
            return this.messageProducer;
        }

        @Override
        public BytesMessage createBytesMessage() {
            throw new UnsupportedOperationException();
        }

        @Override
        public MapMessage createMapMessage() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Message createMessage() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ObjectMessage createObjectMessage() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ObjectMessage createObjectMessage(Serializable object) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StreamMessage createStreamMessage() {
            throw new UnsupportedOperationException();
        }

        @Override
        public TextMessage createTextMessage() {
            throw new UnsupportedOperationException();
        }

        @Override
        public TextMessage createTextMessage(String text) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean getTransacted() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getAcknowledgeMode() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void commit() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void rollback() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
        }

        @Override
        public void recover() {
            throw new UnsupportedOperationException();
        }

        @Override
        public MessageListener getMessageListener() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setMessageListener(MessageListener listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void run() {
            throw new UnsupportedOperationException();
        }

        @Override
        public MessageConsumer createConsumer(Destination destination) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MessageConsumer createConsumer(Destination destination, String messageSelector) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MessageConsumer createConsumer(Destination destination, String messageSelector, boolean noLocal) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MessageConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MessageConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName,
                String messageSelector) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Queue createQueue(String queueName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Topic createTopic(String topicName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TopicSubscriber createDurableSubscriber(Topic topic, String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TopicSubscriber createDurableSubscriber(Topic topic, String name, String messageSelector,
                boolean noLocal) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MessageConsumer createDurableConsumer(Topic topic, String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MessageConsumer createDurableConsumer(Topic topic, String name, String messageSelector,
                boolean noLocal) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MessageConsumer createSharedDurableConsumer(Topic topic, String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MessageConsumer createSharedDurableConsumer(Topic topic, String name, String messageSelector) {
            throw new UnsupportedOperationException();
        }

        @Override
        public QueueBrowser createBrowser(Queue queue) {
            throw new UnsupportedOperationException();
        }

        @Override
        public QueueBrowser createBrowser(Queue queue, String messageSelector) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TemporaryQueue createTemporaryQueue() {
            throw new UnsupportedOperationException();
        }

        @Override
        public TemporaryTopic createTemporaryTopic() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void unsubscribe(String name) {
            throw new UnsupportedOperationException();
        }

    }

}
