/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_jakarta9;

import java.io.Serializable;

import io.micrometer.jakarta9.instrument.jms.JmsInstrumentation;
import io.micrometer.observation.ObservationRegistry;
import jakarta.jms.BytesMessage;
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

public class MessageConsumerInvocationHandlerTest {

    @Test
    void instrumentedConsumerDelegatesNonListenerCallsToTarget() throws JMSException {
        RecordingMessageConsumer targetConsumer = new RecordingMessageConsumer("orders = true");
        Session session = JmsInstrumentation.instrumentSession(new RecordingSession(targetConsumer),
                ObservationRegistry.create());

        MessageConsumer instrumentedConsumer = session.createConsumer(null);

        assertThat(instrumentedConsumer).isNotSameAs(targetConsumer);
        assertThat(instrumentedConsumer.getMessageSelector()).isEqualTo("orders = true");
        assertThat(targetConsumer.getMessageSelectorCalls).isEqualTo(1);
    }

    private static final class RecordingMessageConsumer implements MessageConsumer {

        private final String messageSelector;

        private MessageListener messageListener;

        private int getMessageSelectorCalls;

        private RecordingMessageConsumer(String messageSelector) {
            this.messageSelector = messageSelector;
        }

        @Override
        public String getMessageSelector() {
            this.getMessageSelectorCalls++;
            return this.messageSelector;
        }

        @Override
        public MessageListener getMessageListener() {
            return this.messageListener;
        }

        @Override
        public void setMessageListener(MessageListener listener) {
            this.messageListener = listener;
        }

        @Override
        public Message receive() {
            return null;
        }

        @Override
        public Message receive(long timeout) {
            return null;
        }

        @Override
        public Message receiveNoWait() {
            return null;
        }

        @Override
        public void close() {
        }

    }

    private static final class RecordingSession implements Session {

        private final MessageConsumer messageConsumer;

        private RecordingSession(MessageConsumer messageConsumer) {
            this.messageConsumer = messageConsumer;
        }

        @Override
        public MessageConsumer createConsumer(Destination destination) {
            return this.messageConsumer;
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
        public MessageProducer createProducer(Destination destination) {
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
