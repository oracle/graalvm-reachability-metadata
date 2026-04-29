/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_jms.jakarta_jms_api;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.jms.BytesMessage;
import javax.jms.CompletionListener;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.InvalidDestinationException;
import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageFormatException;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.QueueReceiver;
import javax.jms.QueueRequestor;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicPublisher;
import javax.jms.TopicRequestor;
import javax.jms.TopicSession;
import javax.jms.TopicSubscriber;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Jakarta_jms_apiTest {

    @Test
    void exposesStableMessagingConstants() {
        assertThat(DeliveryMode.NON_PERSISTENT).isEqualTo(1);
        assertThat(DeliveryMode.PERSISTENT).isEqualTo(2);

        assertThat(Session.SESSION_TRANSACTED).isZero();
        assertThat(Session.AUTO_ACKNOWLEDGE).isEqualTo(1);
        assertThat(Session.CLIENT_ACKNOWLEDGE).isEqualTo(2);
        assertThat(Session.DUPS_OK_ACKNOWLEDGE).isEqualTo(3);

        assertThat(javax.jms.JMSContext.SESSION_TRANSACTED).isEqualTo(Session.SESSION_TRANSACTED);
        assertThat(javax.jms.JMSContext.AUTO_ACKNOWLEDGE).isEqualTo(Session.AUTO_ACKNOWLEDGE);
        assertThat(javax.jms.JMSContext.CLIENT_ACKNOWLEDGE).isEqualTo(Session.CLIENT_ACKNOWLEDGE);
        assertThat(javax.jms.JMSContext.DUPS_OK_ACKNOWLEDGE).isEqualTo(Session.DUPS_OK_ACKNOWLEDGE);

        assertThat(Message.DEFAULT_DELIVERY_MODE).isEqualTo(DeliveryMode.PERSISTENT);
        assertThat(Message.DEFAULT_PRIORITY).isEqualTo(4);
        assertThat(Message.DEFAULT_TIME_TO_LIVE).isZero();
        assertThat(Message.DEFAULT_DELIVERY_DELAY).isZero();
    }

    @Test
    void checkedExceptionsKeepReasonErrorCodeAndLinkedException() {
        JMSException base = new JMSException("base failure", "JMS-0001");
        java.lang.IllegalArgumentException linked = new java.lang.IllegalArgumentException("linked");
        base.setLinkedException(linked);

        assertThat(base).hasMessage("base failure");
        assertThat(base.getErrorCode()).isEqualTo("JMS-0001");
        assertThat(base.getLinkedException()).isSameAs(linked);

        assertCheckedException(new javax.jms.IllegalStateException("bad state"), "bad state", null);
        assertCheckedException(new javax.jms.IllegalStateException("bad state", "JMS-1000"), "bad state", "JMS-1000");
        assertCheckedException(new javax.jms.InvalidClientIDException("bad client", "JMS-1001"), "bad client", "JMS-1001");
        assertCheckedException(new InvalidDestinationException("bad destination", "JMS-1002"), "bad destination", "JMS-1002");
        assertCheckedException(new javax.jms.InvalidSelectorException("bad selector", "JMS-1003"), "bad selector", "JMS-1003");
        assertCheckedException(new javax.jms.JMSSecurityException("access denied", "JMS-1004"), "access denied", "JMS-1004");
        assertCheckedException(new javax.jms.MessageEOFException("unexpected eof", "JMS-1005"), "unexpected eof", "JMS-1005");
        assertCheckedException(new MessageFormatException("bad format", "JMS-1006"), "bad format", "JMS-1006");
        assertCheckedException(new javax.jms.MessageNotReadableException("not readable", "JMS-1007"), "not readable", "JMS-1007");
        assertCheckedException(new javax.jms.MessageNotWriteableException("not writeable", "JMS-1008"), "not writeable", "JMS-1008");
        assertCheckedException(new javax.jms.ResourceAllocationException("out of resources", "JMS-1009"), "out of resources", "JMS-1009");
        assertCheckedException(new javax.jms.TransactionInProgressException("tx in progress", "JMS-1010"), "tx in progress", "JMS-1010");
        assertCheckedException(new javax.jms.TransactionRolledBackException("tx rolled back", "JMS-1011"), "tx rolled back", "JMS-1011");
    }

    @Test
    void runtimeExceptionsKeepReasonErrorCodeAndCause() {
        RuntimeException cause = new RuntimeException("root cause");

        JMSRuntimeException base = new JMSRuntimeException("runtime failure", "JMS-2000", cause);
        assertRuntimeException(base, "runtime failure", "JMS-2000", cause);
        assertThat(new JMSRuntimeException("reason only")).hasMessage("reason only");
        assertThat(new JMSRuntimeException("reason and code", "JMS-2001").getErrorCode()).isEqualTo("JMS-2001");

        assertRuntimeException(new javax.jms.IllegalStateRuntimeException("bad state", "JMS-2002", cause), "bad state", "JMS-2002", cause);
        assertRuntimeException(new javax.jms.InvalidClientIDRuntimeException("bad client", "JMS-2003", cause), "bad client", "JMS-2003", cause);
        assertRuntimeException(new javax.jms.InvalidDestinationRuntimeException("bad destination", "JMS-2004", cause), "bad destination", "JMS-2004", cause);
        assertRuntimeException(new javax.jms.InvalidSelectorRuntimeException("bad selector", "JMS-2005", cause), "bad selector", "JMS-2005", cause);
        assertRuntimeException(new javax.jms.JMSSecurityRuntimeException("access denied", "JMS-2006", cause), "access denied", "JMS-2006", cause);
        assertRuntimeException(new javax.jms.MessageFormatRuntimeException("bad format", "JMS-2007", cause), "bad format", "JMS-2007", cause);
        assertRuntimeException(new javax.jms.MessageNotWriteableRuntimeException("not writeable", "JMS-2008", cause), "not writeable", "JMS-2008", cause);
        assertRuntimeException(new javax.jms.ResourceAllocationRuntimeException("out of resources", "JMS-2009", cause), "out of resources", "JMS-2009", cause);
        assertRuntimeException(new javax.jms.TransactionInProgressRuntimeException("tx in progress", "JMS-2010", cause), "tx in progress", "JMS-2010", cause);
        assertRuntimeException(new javax.jms.TransactionRolledBackRuntimeException("tx rolled back", "JMS-2011", cause), "tx rolled back", "JMS-2011", cause);
    }

    @Test
    void queueRequestorCreatesTemporaryReplyQueueAndReturnsReceivedReply() throws JMSException {
        RecordingMessage request = new RecordingMessage();
        RecordingMessage reply = new RecordingMessage();
        RecordingQueueSession session = new RecordingQueueSession(reply);
        Queue requestQueue = new SimpleQueue("requests");

        QueueRequestor requestor = new QueueRequestor(session, requestQueue);
        Message response = requestor.request(request);
        requestor.close();

        assertThat(response).isSameAs(reply);
        assertThat(request.getJMSReplyTo()).isSameAs(session.temporaryQueue);
        assertThat(session.createdSenderQueue).isSameAs(requestQueue);
        assertThat(session.createdReceiverQueue).isSameAs(session.temporaryQueue);
        assertThat(session.sender.lastSentMessage).isSameAs(request);
        assertThat(session.events).containsExactly(
                "session.createTemporaryQueue",
                "session.createSender",
                "session.createReceiver",
                "sender.send",
                "receiver.receive",
                "session.close",
                "temporaryQueue.delete"
        );
    }

    @Test
    void queueRequestorRejectsNullQueue() {
        assertThatThrownBy(() -> new QueueRequestor(new RecordingQueueSession(new RecordingMessage()), null))
                .isInstanceOf(InvalidDestinationException.class)
                .hasMessage("queue==null");
    }

    @Test
    void topicRequestorCreatesTemporaryReplyTopicAndReturnsReceivedReply() throws JMSException {
        RecordingMessage request = new RecordingMessage();
        RecordingMessage reply = new RecordingMessage();
        RecordingTopicSession session = new RecordingTopicSession(reply);
        Topic requestTopic = new SimpleTopic("requests");

        TopicRequestor requestor = new TopicRequestor(session, requestTopic);
        Message response = requestor.request(request);
        requestor.close();

        assertThat(response).isSameAs(reply);
        assertThat(request.getJMSReplyTo()).isSameAs(session.temporaryTopic);
        assertThat(session.createdPublisherTopic).isSameAs(requestTopic);
        assertThat(session.createdSubscriberTopic).isSameAs(session.temporaryTopic);
        assertThat(session.publisher.lastPublishedMessage).isSameAs(request);
        assertThat(session.events).containsExactly(
                "session.createTemporaryTopic",
                "session.createPublisher",
                "session.createSubscriber",
                "publisher.publish",
                "subscriber.receive",
                "session.close",
                "temporaryTopic.delete"
        );
    }

    @Test
    void topicRequestorRejectsNullTopic() {
        assertThatThrownBy(() -> new TopicRequestor(new RecordingTopicSession(new RecordingMessage()), null))
                .isInstanceOf(InvalidDestinationException.class)
                .hasMessage("topic==null");
    }

    @Test
    void messageHeadersPropertiesAndBodyCanBeManagedThroughStandardApi() throws JMSException {
        RecordingMessage message = new RecordingMessage();
        Queue replyQueue = new SimpleQueue("reply");
        Topic destinationTopic = new SimpleTopic("events");
        byte[] correlationId = new byte[] {(byte) 1, (byte) 2, (byte) 3};

        message.setJMSMessageID("ID:123");
        message.setJMSTimestamp(1234L);
        message.setJMSCorrelationIDAsBytes(correlationId);
        message.setJMSCorrelationID("corr-123");
        message.setJMSReplyTo(replyQueue);
        message.setJMSDestination(destinationTopic);
        message.setJMSDeliveryMode(DeliveryMode.NON_PERSISTENT);
        message.setJMSRedelivered(true);
        message.setJMSType("event");
        message.setJMSExpiration(5678L);
        message.setJMSDeliveryTime(91011L);
        message.setJMSPriority(7);
        message.setBooleanProperty("boolean", true);
        message.setByteProperty("byte", (byte) 9);
        message.setShortProperty("short", (short) 12);
        message.setIntProperty("int", 34);
        message.setLongProperty("long", 56L);
        message.setFloatProperty("float", 7.5f);
        message.setDoubleProperty("double", 8.5d);
        message.setStringProperty("string", "value");
        message.setObjectProperty("object", List.of("a", "b"));
        message.setBodyValue("payload");
        correlationId[0] = (byte) 99;

        assertThat(message.getJMSMessageID()).isEqualTo("ID:123");
        assertThat(message.getJMSTimestamp()).isEqualTo(1234L);
        assertThat(message.getJMSCorrelationIDAsBytes()).containsExactly((byte) 1, (byte) 2, (byte) 3);
        assertThat(message.getJMSCorrelationID()).isEqualTo("corr-123");
        assertThat(message.getJMSReplyTo()).isSameAs(replyQueue);
        assertThat(message.getJMSDestination()).isSameAs(destinationTopic);
        assertThat(message.getJMSDeliveryMode()).isEqualTo(DeliveryMode.NON_PERSISTENT);
        assertThat(message.getJMSRedelivered()).isTrue();
        assertThat(message.getJMSType()).isEqualTo("event");
        assertThat(message.getJMSExpiration()).isEqualTo(5678L);
        assertThat(message.getJMSDeliveryTime()).isEqualTo(91011L);
        assertThat(message.getJMSPriority()).isEqualTo(7);
        assertThat(message.propertyExists("boolean")).isTrue();
        assertThat(message.getBooleanProperty("boolean")).isTrue();
        assertThat(message.getByteProperty("byte")).isEqualTo((byte) 9);
        assertThat(message.getShortProperty("short")).isEqualTo((short) 12);
        assertThat(message.getIntProperty("int")).isEqualTo(34);
        assertThat(message.getLongProperty("long")).isEqualTo(56L);
        assertThat(message.getFloatProperty("float")).isEqualTo(7.5f);
        assertThat(message.getDoubleProperty("double")).isEqualTo(8.5d);
        assertThat(message.getStringProperty("string")).isEqualTo("value");
        assertThat(message.getObjectProperty("object")).isEqualTo(List.of("a", "b"));
        assertThat(Set.copyOf(Collections.list(message.getPropertyNames())))
                .containsExactlyInAnyOrder("boolean", "byte", "short", "int", "long", "float", "double", "string", "object");
        assertThat(message.isBodyAssignableTo(String.class)).isTrue();
        assertThat(message.isBodyAssignableTo(CharSequence.class)).isTrue();
        assertThat(message.isBodyAssignableTo(Integer.class)).isFalse();
        assertThat(message.getBody(String.class)).isEqualTo("payload");
        assertThat(message.getBody(CharSequence.class)).isEqualTo("payload");

        byte[] returnedCorrelationId = message.getJMSCorrelationIDAsBytes();
        returnedCorrelationId[1] = (byte) 88;

        assertThat(message.getJMSCorrelationIDAsBytes()).containsExactly((byte) 1, (byte) 2, (byte) 3);
        assertThatThrownBy(() -> message.getBody(Integer.class))
                .isInstanceOf(MessageFormatException.class)
                .hasMessage("Body is not assignable to java.lang.Integer");

        message.clearBody();
        message.clearProperties();

        assertThat(message.getBody(String.class)).isNull();
        assertThat(message.isBodyAssignableTo(Integer.class)).isTrue();
        assertThat(message.propertyExists("boolean")).isFalse();
        assertThat(Collections.list(message.getPropertyNames())).isEmpty();
    }

    @Test
    void mapMessageSupportsTypedEntriesByteSlicesAndBodyViews() throws JMSException {
        RecordingMapMessage message = new RecordingMapMessage();
        byte[] payload = new byte[] {(byte) 10, (byte) 20, (byte) 30, (byte) 40};

        message.setBoolean("enabled", true);
        message.setInt("count", 7);
        message.setString("name", "orders");
        message.setBytes("payload", payload, 1, 2);
        payload[1] = (byte) 99;

        assertThat(message.getBoolean("enabled")).isTrue();
        assertThat(message.getInt("count")).isEqualTo(7);
        assertThat(message.getString("name")).isEqualTo("orders");
        assertThat(message.getBytes("payload")).containsExactly((byte) 20, (byte) 30);
        assertThat(Set.copyOf(Collections.list(message.getMapNames())))
                .containsExactlyInAnyOrder("enabled", "count", "name", "payload");
        assertThat(message.itemExists("payload")).isTrue();
        assertThat(message.isBodyAssignableTo(Map.class)).isTrue();
        assertThat(message.isBodyAssignableTo(String.class)).isFalse();
        assertThat(message.getBody(Map.class)).containsEntry("enabled", true)
                .containsEntry("count", 7)
                .containsEntry("name", "orders");
        assertThat((byte[]) message.getBody(Map.class).get("payload")).containsExactly((byte) 20, (byte) 30);

        byte[] returnedPayload = message.getBytes("payload");
        returnedPayload[0] = (byte) 55;

        assertThat(message.getBytes("payload")).containsExactly((byte) 20, (byte) 30);
        assertThatThrownBy(() -> message.getBody(String.class))
                .isInstanceOf(MessageFormatException.class)
                .hasMessage("Body is not assignable to java.lang.String");

        message.clearBody();

        assertThat(Collections.list(message.getMapNames())).isEmpty();
        assertThat(message.itemExists("name")).isFalse();
        assertThat(message.getBody(Map.class)).isEmpty();
    }

    @Test
    void listenerContractsCanBeImplementedWithoutProviderSpecificCode() throws JMSException {
        RecordingMessage message = new RecordingMessage();
        JMSException failure = new JMSException("listener failure");
        AtomicReference<Message> deliveredMessage = new AtomicReference<>();
        AtomicReference<JMSException> deliveredException = new AtomicReference<>();
        AtomicReference<Message> completionMessage = new AtomicReference<>();
        AtomicReference<Exception> completionException = new AtomicReference<>();

        MessageListener messageListener = deliveredMessage::set;
        ExceptionListener exceptionListener = deliveredException::set;
        CompletionListener completionListener = new CompletionListener() {
            @Override
            public void onCompletion(Message completedMessage) {
                completionMessage.set(completedMessage);
            }

            @Override
            public void onException(Message completedMessage, Exception exception) {
                completionMessage.set(completedMessage);
                completionException.set(exception);
            }
        };

        messageListener.onMessage(message);
        exceptionListener.onException(failure);
        completionListener.onCompletion(message);
        completionListener.onException(message, failure);

        assertThat(deliveredMessage).hasValue(message);
        assertThat(deliveredException).hasValue(failure);
        assertThat(completionMessage).hasValue(message);
        assertThat(completionException).hasValue(failure);
    }

    private static void assertCheckedException(JMSException exception, String reason, String errorCode) {
        assertThat(exception).hasMessage(reason);
        assertThat(exception.getErrorCode()).isEqualTo(errorCode);
    }

    private static void assertRuntimeException(JMSRuntimeException exception, String reason, String errorCode, Throwable cause) {
        assertThat(exception).hasMessage(reason);
        assertThat(exception.getErrorCode()).isEqualTo(errorCode);
        assertThat(exception.getCause()).isSameAs(cause);
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("Not required by this API contract test");
    }

    private static class RecordingMessage implements Message {
        private String messageId;
        private long timestamp;
        private byte[] correlationIdBytes;
        private String correlationId;
        private Destination replyTo;
        private Destination destination;
        private int deliveryMode = DEFAULT_DELIVERY_MODE;
        private boolean redelivered;
        private String type;
        private long expiration;
        private long deliveryTime = DEFAULT_DELIVERY_DELAY;
        private int priority = DEFAULT_PRIORITY;
        private final Map<String, Object> properties = new LinkedHashMap<>();
        private Object body;

        @Override
        public String getJMSMessageID() {
            return messageId;
        }

        @Override
        public void setJMSMessageID(String id) {
            this.messageId = id;
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
            return correlationIdBytes == null ? null : correlationIdBytes.clone();
        }

        @Override
        public void setJMSCorrelationIDAsBytes(byte[] correlationId) {
            this.correlationIdBytes = correlationId == null ? null : correlationId.clone();
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
            return (String) properties.get(name);
        }

        @Override
        public Object getObjectProperty(String name) {
            return properties.get(name);
        }

        @Override
        public Enumeration getPropertyNames() {
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
            body = null;
        }

        private void setBodyValue(Object body) {
            this.body = body;
        }

        @Override
        public <T> T getBody(Class<T> type) throws JMSException {
            if (body == null) {
                return null;
            }
            if (!type.isInstance(body)) {
                throw new MessageFormatException("Body is not assignable to " + type.getName());
            }
            return type.cast(body);
        }

        @Override
        public boolean isBodyAssignableTo(Class type) {
            return body == null || type.isInstance(body);
        }
    }

    private static final class RecordingMapMessage extends RecordingMessage implements MapMessage {
        private final Map<String, Object> entries = new LinkedHashMap<>();

        @Override
        public boolean getBoolean(String name) {
            return (boolean) entries.get(name);
        }

        @Override
        public byte getByte(String name) {
            return (byte) entries.get(name);
        }

        @Override
        public short getShort(String name) {
            return (short) entries.get(name);
        }

        @Override
        public char getChar(String name) {
            return (char) entries.get(name);
        }

        @Override
        public int getInt(String name) {
            return (int) entries.get(name);
        }

        @Override
        public long getLong(String name) {
            return (long) entries.get(name);
        }

        @Override
        public float getFloat(String name) {
            return (float) entries.get(name);
        }

        @Override
        public double getDouble(String name) {
            return (double) entries.get(name);
        }

        @Override
        public String getString(String name) {
            return (String) entries.get(name);
        }

        @Override
        public byte[] getBytes(String name) {
            return copyBytes((byte[]) entries.get(name));
        }

        @Override
        public Object getObject(String name) {
            return copyValue(entries.get(name));
        }

        @Override
        public Enumeration getMapNames() {
            return Collections.enumeration(entries.keySet());
        }

        @Override
        public void setBoolean(String name, boolean value) {
            entries.put(name, value);
        }

        @Override
        public void setByte(String name, byte value) {
            entries.put(name, value);
        }

        @Override
        public void setShort(String name, short value) {
            entries.put(name, value);
        }

        @Override
        public void setChar(String name, char value) {
            entries.put(name, value);
        }

        @Override
        public void setInt(String name, int value) {
            entries.put(name, value);
        }

        @Override
        public void setLong(String name, long value) {
            entries.put(name, value);
        }

        @Override
        public void setFloat(String name, float value) {
            entries.put(name, value);
        }

        @Override
        public void setDouble(String name, double value) {
            entries.put(name, value);
        }

        @Override
        public void setString(String name, String value) {
            entries.put(name, value);
        }

        @Override
        public void setBytes(String name, byte[] value) {
            entries.put(name, copyBytes(value));
        }

        @Override
        public void setBytes(String name, byte[] value, int offset, int length) {
            byte[] slice = new byte[length];
            System.arraycopy(value, offset, slice, 0, length);
            entries.put(name, slice);
        }

        @Override
        public void setObject(String name, Object value) {
            entries.put(name, copyValue(value));
        }

        @Override
        public boolean itemExists(String name) {
            return entries.containsKey(name);
        }

        @Override
        public void clearBody() {
            super.clearBody();
            entries.clear();
        }

        @Override
        public <T> T getBody(Class<T> type) throws JMSException {
            Map<String, Object> snapshot = snapshotEntries();
            if (!type.isInstance(snapshot)) {
                throw new MessageFormatException("Body is not assignable to " + type.getName());
            }
            return type.cast(snapshot);
        }

        @Override
        public boolean isBodyAssignableTo(Class type) {
            return type.isInstance(snapshotEntries());
        }

        private Map<String, Object> snapshotEntries() {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : entries.entrySet()) {
                snapshot.put(entry.getKey(), copyValue(entry.getValue()));
            }
            return snapshot;
        }

        private static Object copyValue(Object value) {
            if (value instanceof byte[] bytes) {
                return copyBytes(bytes);
            }
            return value;
        }

        private static byte[] copyBytes(byte[] value) {
            return value == null ? null : value.clone();
        }
    }

    private abstract static class AbstractSessionSupport implements Session {

        @Override
        public BytesMessage createBytesMessage() {
            throw unsupported();
        }

        @Override
        public MapMessage createMapMessage() {
            throw unsupported();
        }

        @Override
        public Message createMessage() {
            return new RecordingMessage();
        }

        @Override
        public ObjectMessage createObjectMessage() {
            throw unsupported();
        }

        @Override
        public ObjectMessage createObjectMessage(Serializable object) {
            throw unsupported();
        }

        @Override
        public StreamMessage createStreamMessage() {
            throw unsupported();
        }

        @Override
        public TextMessage createTextMessage() {
            throw unsupported();
        }

        @Override
        public TextMessage createTextMessage(String text) {
            throw unsupported();
        }

        @Override
        public boolean getTransacted() {
            return false;
        }

        @Override
        public int getAcknowledgeMode() {
            return AUTO_ACKNOWLEDGE;
        }

        @Override
        public void commit() {
            throw unsupported();
        }

        @Override
        public void rollback() {
            throw unsupported();
        }

        @Override
        public void close() {
            throw unsupported();
        }

        @Override
        public void recover() {
            throw unsupported();
        }

        @Override
        public MessageListener getMessageListener() {
            throw unsupported();
        }

        @Override
        public void setMessageListener(MessageListener listener) {
            throw unsupported();
        }

        @Override
        public void run() {
        }

        @Override
        public MessageProducer createProducer(Destination destination) {
            throw unsupported();
        }

        @Override
        public MessageConsumer createConsumer(Destination destination) {
            throw unsupported();
        }

        @Override
        public MessageConsumer createConsumer(Destination destination, String messageSelector) {
            throw unsupported();
        }

        @Override
        public MessageConsumer createConsumer(Destination destination, String messageSelector, boolean noLocal) {
            throw unsupported();
        }

        @Override
        public MessageConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName) {
            throw unsupported();
        }

        @Override
        public MessageConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName, String messageSelector) {
            throw unsupported();
        }

        @Override
        public Queue createQueue(String queueName) {
            throw unsupported();
        }

        @Override
        public Topic createTopic(String topicName) {
            throw unsupported();
        }

        @Override
        public TopicSubscriber createDurableSubscriber(Topic topic, String name) {
            throw unsupported();
        }

        @Override
        public TopicSubscriber createDurableSubscriber(Topic topic, String name, String messageSelector, boolean noLocal) {
            throw unsupported();
        }

        @Override
        public MessageConsumer createDurableConsumer(Topic topic, String name) {
            throw unsupported();
        }

        @Override
        public MessageConsumer createDurableConsumer(Topic topic, String name, String messageSelector, boolean noLocal) {
            throw unsupported();
        }

        @Override
        public MessageConsumer createSharedDurableConsumer(Topic topic, String name) {
            throw unsupported();
        }

        @Override
        public MessageConsumer createSharedDurableConsumer(Topic topic, String name, String messageSelector) {
            throw unsupported();
        }

        @Override
        public QueueBrowser createBrowser(Queue queue) {
            throw unsupported();
        }

        @Override
        public QueueBrowser createBrowser(Queue queue, String messageSelector) {
            throw unsupported();
        }

        @Override
        public TemporaryQueue createTemporaryQueue() {
            throw unsupported();
        }

        @Override
        public TemporaryTopic createTemporaryTopic() {
            throw unsupported();
        }

        @Override
        public void unsubscribe(String name) {
            throw unsupported();
        }
    }

    private abstract static class AbstractMessageProducerSupport implements MessageProducer {
        private final Destination destination;
        private boolean disableMessageId;
        private boolean disableMessageTimestamp;
        private int deliveryMode = DeliveryMode.PERSISTENT;
        private int priority = Message.DEFAULT_PRIORITY;
        private long timeToLive;
        private long deliveryDelay;
        private boolean closed;

        protected AbstractMessageProducerSupport(Destination destination) {
            this.destination = destination;
        }

        @Override
        public void setDisableMessageID(boolean value) {
            this.disableMessageId = value;
        }

        @Override
        public boolean getDisableMessageID() {
            return disableMessageId;
        }

        @Override
        public void setDisableMessageTimestamp(boolean value) {
            this.disableMessageTimestamp = value;
        }

        @Override
        public boolean getDisableMessageTimestamp() {
            return disableMessageTimestamp;
        }

        @Override
        public void setDeliveryMode(int deliveryMode) {
            this.deliveryMode = deliveryMode;
        }

        @Override
        public int getDeliveryMode() {
            return deliveryMode;
        }

        @Override
        public void setPriority(int defaultPriority) {
            this.priority = defaultPriority;
        }

        @Override
        public int getPriority() {
            return priority;
        }

        @Override
        public void setTimeToLive(long timeToLive) {
            this.timeToLive = timeToLive;
        }

        @Override
        public long getTimeToLive() {
            return timeToLive;
        }

        @Override
        public void setDeliveryDelay(long deliveryDelay) {
            this.deliveryDelay = deliveryDelay;
        }

        @Override
        public long getDeliveryDelay() {
            return deliveryDelay;
        }

        @Override
        public Destination getDestination() {
            return destination;
        }

        @Override
        public void close() {
            this.closed = true;
        }

        protected boolean isClosed() {
            return closed;
        }

        @Override
        public void send(Message message, CompletionListener completionListener) throws JMSException {
            send(message);
            completionListener.onCompletion(message);
        }

        @Override
        public void send(Message message, int deliveryMode, int priority, long timeToLive) throws JMSException {
            send(message);
        }

        @Override
        public void send(Message message, int deliveryMode, int priority, long timeToLive, CompletionListener completionListener) throws JMSException {
            send(message);
            completionListener.onCompletion(message);
        }

        @Override
        public void send(Destination destination, Message message) throws JMSException {
            send(message);
        }

        @Override
        public void send(Destination destination, Message message, int deliveryMode, int priority, long timeToLive) throws JMSException {
            send(message);
        }

        @Override
        public void send(Destination destination, Message message, CompletionListener completionListener) throws JMSException {
            send(message);
            completionListener.onCompletion(message);
        }

        @Override
        public void send(Destination destination, Message message, int deliveryMode, int priority, long timeToLive, CompletionListener completionListener) throws JMSException {
            send(message);
            completionListener.onCompletion(message);
        }
    }

    private abstract static class AbstractMessageConsumerSupport implements MessageConsumer {
        private final Message response;
        private MessageListener messageListener;

        protected AbstractMessageConsumerSupport(Message response) {
            this.response = response;
        }

        @Override
        public String getMessageSelector() {
            return null;
        }

        @Override
        public MessageListener getMessageListener() {
            return messageListener;
        }

        @Override
        public void setMessageListener(MessageListener listener) {
            this.messageListener = listener;
        }

        @Override
        public Message receive() {
            return response;
        }

        @Override
        public Message receive(long timeout) {
            return response;
        }

        @Override
        public Message receiveNoWait() {
            return response;
        }

        @Override
        public void close() {
        }
    }

    private static final class RecordingQueueSession extends AbstractSessionSupport implements QueueSession {
        private final Message response;
        private final List<String> events = new ArrayList<>();
        private final SimpleTemporaryQueue temporaryQueue = new SimpleTemporaryQueue("reply.queue", events);
        private Queue createdSenderQueue;
        private Queue createdReceiverQueue;
        private RecordingQueueSender sender;

        private RecordingQueueSession(Message response) {
            this.response = response;
        }

        @Override
        public Queue createQueue(String queueName) {
            return new SimpleQueue(queueName);
        }

        @Override
        public QueueReceiver createReceiver(Queue queue) {
            events.add("session.createReceiver");
            this.createdReceiverQueue = queue;
            return new RecordingQueueReceiver(queue, response, events);
        }

        @Override
        public QueueReceiver createReceiver(Queue queue, String messageSelector) {
            return createReceiver(queue);
        }

        @Override
        public QueueSender createSender(Queue queue) {
            events.add("session.createSender");
            this.createdSenderQueue = queue;
            this.sender = new RecordingQueueSender(queue, events);
            return sender;
        }

        @Override
        public QueueBrowser createBrowser(Queue queue) {
            return new SimpleQueueBrowser(queue);
        }

        @Override
        public QueueBrowser createBrowser(Queue queue, String messageSelector) {
            return new SimpleQueueBrowser(queue);
        }

        @Override
        public TemporaryQueue createTemporaryQueue() {
            events.add("session.createTemporaryQueue");
            return temporaryQueue;
        }

        @Override
        public void close() {
            events.add("session.close");
        }
    }

    private static final class RecordingQueueSender extends AbstractMessageProducerSupport implements QueueSender {
        private final Queue queue;
        private final List<String> events;
        private Message lastSentMessage;

        private RecordingQueueSender(Queue queue, List<String> events) {
            super(queue);
            this.queue = queue;
            this.events = events;
        }

        @Override
        public Queue getQueue() {
            return queue;
        }

        @Override
        public void send(Message message) {
            events.add("sender.send");
            this.lastSentMessage = message;
        }

        @Override
        public void send(Queue queue, Message message) {
            send(message);
        }

        @Override
        public void send(Message message, int deliveryMode, int priority, long timeToLive) {
            send(message);
        }

        @Override
        public void send(Queue queue, Message message, int deliveryMode, int priority, long timeToLive) {
            send(message);
        }
    }

    private static final class RecordingQueueReceiver extends AbstractMessageConsumerSupport implements QueueReceiver {
        private final Queue queue;
        private final List<String> events;

        private RecordingQueueReceiver(Queue queue, Message response, List<String> events) {
            super(response);
            this.queue = queue;
            this.events = events;
        }

        @Override
        public Queue getQueue() {
            return queue;
        }

        @Override
        public Message receive() {
            events.add("receiver.receive");
            return super.receive();
        }
    }

    private static final class RecordingTopicSession extends AbstractSessionSupport implements TopicSession {
        private final Message response;
        private final List<String> events = new ArrayList<>();
        private final SimpleTemporaryTopic temporaryTopic = new SimpleTemporaryTopic("reply.topic", events);
        private Topic createdPublisherTopic;
        private Topic createdSubscriberTopic;
        private RecordingTopicPublisher publisher;

        private RecordingTopicSession(Message response) {
            this.response = response;
        }

        @Override
        public Topic createTopic(String topicName) {
            return new SimpleTopic(topicName);
        }

        @Override
        public TopicSubscriber createSubscriber(Topic topic) {
            events.add("session.createSubscriber");
            this.createdSubscriberTopic = topic;
            return new RecordingTopicSubscriber(topic, response, events);
        }

        @Override
        public TopicSubscriber createSubscriber(Topic topic, String messageSelector, boolean noLocal) {
            return createSubscriber(topic);
        }

        @Override
        public TopicSubscriber createDurableSubscriber(Topic topic, String name) {
            throw unsupported();
        }

        @Override
        public TopicSubscriber createDurableSubscriber(Topic topic, String name, String messageSelector, boolean noLocal) {
            throw unsupported();
        }

        @Override
        public TopicPublisher createPublisher(Topic topic) {
            events.add("session.createPublisher");
            this.createdPublisherTopic = topic;
            this.publisher = new RecordingTopicPublisher(topic, events);
            return publisher;
        }

        @Override
        public TemporaryTopic createTemporaryTopic() {
            events.add("session.createTemporaryTopic");
            return temporaryTopic;
        }

        @Override
        public void unsubscribe(String name) {
            throw unsupported();
        }

        @Override
        public void close() {
            events.add("session.close");
        }
    }

    private static final class RecordingTopicPublisher extends AbstractMessageProducerSupport implements TopicPublisher {
        private final Topic topic;
        private final List<String> events;
        private Message lastPublishedMessage;

        private RecordingTopicPublisher(Topic topic, List<String> events) {
            super(topic);
            this.topic = topic;
            this.events = events;
        }

        @Override
        public Topic getTopic() {
            return topic;
        }

        @Override
        public void publish(Message message) {
            events.add("publisher.publish");
            this.lastPublishedMessage = message;
        }

        @Override
        public void publish(Topic topic, Message message) {
            publish(message);
        }

        @Override
        public void publish(Message message, int deliveryMode, int priority, long timeToLive) {
            publish(message);
        }

        @Override
        public void publish(Topic topic, Message message, int deliveryMode, int priority, long timeToLive) {
            publish(message);
        }

        @Override
        public void send(Message message) {
            publish(message);
        }
    }

    private static final class RecordingTopicSubscriber extends AbstractMessageConsumerSupport implements TopicSubscriber {
        private final Topic topic;
        private final List<String> events;

        private RecordingTopicSubscriber(Topic topic, Message response, List<String> events) {
            super(response);
            this.topic = topic;
            this.events = events;
        }

        @Override
        public Topic getTopic() {
            return topic;
        }

        @Override
        public boolean getNoLocal() {
            return false;
        }

        @Override
        public Message receive() {
            events.add("subscriber.receive");
            return super.receive();
        }
    }

    private static class SimpleQueue implements Queue {
        private final String name;

        private SimpleQueue(String name) {
            this.name = name;
        }

        @Override
        public String getQueueName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final class SimpleTemporaryQueue extends SimpleQueue implements TemporaryQueue {
        private final List<String> events;

        private SimpleTemporaryQueue(String name, List<String> events) {
            super(name);
            this.events = events;
        }

        @Override
        public void delete() {
            events.add("temporaryQueue.delete");
        }
    }

    private static class SimpleTopic implements Topic {
        private final String name;

        private SimpleTopic(String name) {
            this.name = name;
        }

        @Override
        public String getTopicName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final class SimpleTemporaryTopic extends SimpleTopic implements TemporaryTopic {
        private final List<String> events;

        private SimpleTemporaryTopic(String name, List<String> events) {
            super(name);
            this.events = events;
        }

        @Override
        public void delete() {
            events.add("temporaryTopic.delete");
        }
    }

    private static final class SimpleQueueBrowser implements QueueBrowser {
        private final Queue queue;

        private SimpleQueueBrowser(Queue queue) {
            this.queue = queue;
        }

        @Override
        public Queue getQueue() {
            return queue;
        }

        @Override
        public String getMessageSelector() {
            return null;
        }

        @Override
        public Enumeration getEnumeration() {
            return Collections.emptyEnumeration();
        }

        @Override
        public void close() {
        }
    }
}
