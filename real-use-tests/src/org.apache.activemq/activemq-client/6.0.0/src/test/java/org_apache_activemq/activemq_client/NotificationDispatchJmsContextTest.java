/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client;

import jakarta.jms.DeliveryMode;
import jakarta.jms.IllegalStateRuntimeException;
import jakarta.jms.InvalidSelectorRuntimeException;
import jakarta.jms.JMSConsumer;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSProducer;
import jakarta.jms.Message;
import jakarta.jms.Queue;
import jakarta.jms.QueueBrowser;
import jakarta.jms.TextMessage;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.region.policy.PolicyEntry;
import org.apache.activemq.broker.region.policy.PolicyMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dispatches customer notifications through the JMS 2.0 simplified API: a `JMSContext`
 * created from the connection factory, fluent `JMSProducer` headers, properties, priority
 * and time-to-live, `JMSConsumer` selectors with `Message.getBody`, child contexts that
 * share one connection in client-acknowledge and transacted modes, and unchecked
 * `JMSRuntimeException` error reporting.
 *
 * A notification service queues e-mail, SMS and push notifications with different
 * priorities; channel workers drain their own channel in priority order, an audit worker
 * acknowledges and recovers in batches, a batching worker commits and rolls back, and a
 * one-time passcode that outlives its time-to-live never reaches the SMS gateway.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationDispatchJmsContextTest {

    private static final String BROKER_NAME = "notification-broker";

    private static final long RECEIVE_TIMEOUT_MILLIS = 10_000L;

    private BrokerService broker;

    private ActiveMQConnectionFactory factory;

    @BeforeAll
    void startBroker() throws Exception {
        PolicyEntry prioritized = new PolicyEntry();
        prioritized.setQueue(">");
        prioritized.setPrioritizedMessages(true);
        PolicyMap policies = new PolicyMap();
        policies.setDefaultEntry(prioritized);

        broker = new BrokerService();
        broker.setBrokerName(BROKER_NAME);
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.setUseShutdownHook(false);
        broker.setDestinationPolicy(policies);
        broker.start();
        assertThat(broker.waitUntilStarted()).isTrue();

        factory = new ActiveMQConnectionFactory("vm://" + BROKER_NAME + "?create=false");
    }

    @AfterAll
    void stopBroker() throws Exception {
        broker.stop();
        broker.waitUntilStopped();
    }

    @Test
    void channelWorkersReceiveTheirNotificationsInPriorityOrder() throws Exception {
        try (JMSContext context = factory.createContext()) {
            context.setClientID("notification-service");
            assertThat(context.getClientID()).isEqualTo("notification-service");
            assertThat(context.getSessionMode()).isEqualTo(JMSContext.AUTO_ACKNOWLEDGE);
            assertThat(context.getAutoStart()).isTrue();
            Queue outbox = context.createQueue("notifications.outbox");

            JMSProducer producer = context.createProducer()
                    .setDeliveryMode(DeliveryMode.PERSISTENT)
                    .setJMSType("notification");
            producer.setPriority(2).setProperty("channel", "email")
                    .setJMSCorrelationID("N-1")
                    .send(outbox, "Your monthly statement is ready");
            producer.setPriority(9).setProperty("channel", "sms")
                    .setJMSCorrelationID("N-2")
                    .send(outbox, "Passcode 481516");
            Map<String, Object> push = new LinkedHashMap<>();
            push.put("title", "Parcel delivered");
            push.put("badge", 3);
            producer.setPriority(6).setProperty("channel", "push")
                    .setJMSCorrelationID("N-3")
                    .send(outbox, push);
            producer.setPriority(7).setProperty("channel", "email")
                    .setJMSCorrelationID("N-4")
                    .send(outbox, "Unusual sign-in detected");
            producer.setPriority(4).setProperty("channel", "sms")
                    .setProperty("attempt", 2)
                    .setJMSCorrelationID("N-5")
                    .send(outbox, "Appointment tomorrow at 10:00".getBytes(StandardCharsets.UTF_8));

            assertThat(producer.getPriority()).isEqualTo(4);
            assertThat(producer.getIntProperty("attempt")).isEqualTo(2);
            assertThat(producer.getPropertyNames()).containsExactlyInAnyOrder("channel", "attempt");
            assertThat(browseCorrelationIds(context, outbox))
                    .containsExactlyInAnyOrder("N-1", "N-2", "N-3", "N-4", "N-5");

            List<String> email = new ArrayList<>();
            try (JMSConsumer worker = context.createConsumer(outbox, "channel = 'email'")) {
                for (int i = 0; i < 2; i++) {
                    Message message = worker.receive(RECEIVE_TIMEOUT_MILLIS);
                    assertThat(message).isNotNull();
                    assertThat(message.getJMSType()).isEqualTo("notification");
                    assertThat(message.getJMSDeliveryMode()).isEqualTo(DeliveryMode.PERSISTENT);
                    assertThat(message.isBodyAssignableTo(String.class)).isTrue();
                    email.add(message.getJMSPriority() + ":" + message.getBody(String.class));
                }
                assertThat(worker.receiveNoWait()).isNull();
            }
            assertThat(email).containsExactly(
                    "7:Unusual sign-in detected", "2:Your monthly statement is ready");

            String mobile = "channel IN ('sms', 'push')";
            try (JMSConsumer worker = context.createConsumer(outbox, mobile)) {
                Message passcode = worker.receive(RECEIVE_TIMEOUT_MILLIS);
                assertThat(passcode.getJMSCorrelationID()).isEqualTo("N-2");
                assertThat(passcode.getBody(String.class)).isEqualTo("Passcode 481516");

                Message parcel = worker.receive(RECEIVE_TIMEOUT_MILLIS);
                assertThat(parcel.getJMSCorrelationID()).isEqualTo("N-3");
                assertThat(parcel.isBodyAssignableTo(String.class)).isFalse();
                Map<?, ?> payload = parcel.getBody(Map.class);
                assertThat(payload).hasSize(2);
                assertThat(payload.get("title")).isEqualTo("Parcel delivered");
                assertThat(payload.get("badge")).isEqualTo(3);

                Message reminder = worker.receive(RECEIVE_TIMEOUT_MILLIS);
                assertThat(reminder.getJMSCorrelationID()).isEqualTo("N-5");
                assertThat(reminder.getIntProperty("attempt")).isEqualTo(2);
                assertThat(new String(reminder.getBody(byte[].class), StandardCharsets.UTF_8))
                        .isEqualTo("Appointment tomorrow at 10:00");
                assertThat(worker.receiveNoWait()).isNull();
            }
            assertThat(browseCorrelationIds(context, outbox)).isEmpty();
        }
    }

    @Test
    void childContextsShareTheConnectionForAcknowledgedAndTransactedWork() throws Exception {
        try (JMSContext service = factory.createContext()) {
            Queue audit = service.createQueue("notifications.audit");
            Queue digests = service.createQueue("notifications.digests");
            JMSProducer producer = service.createProducer();
            for (String entry : List.of("A-1", "A-2", "A-3")) {
                producer.send(audit, entry);
            }

            try (JMSContext auditor = service.createContext(JMSContext.CLIENT_ACKNOWLEDGE)) {
                assertThat(auditor.getSessionMode()).isEqualTo(JMSContext.CLIENT_ACKNOWLEDGE);
                JMSConsumer consumer = auditor.createConsumer(audit);
                Message first = consumer.receive(RECEIVE_TIMEOUT_MILLIS);
                Message second = consumer.receive(RECEIVE_TIMEOUT_MILLIS);
                assertThat(List.of(first.getBody(String.class), second.getBody(String.class)))
                        .containsExactly("A-1", "A-2");
                auditor.recover();

                List<String> redelivered = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    Message message = consumer.receive(RECEIVE_TIMEOUT_MILLIS);
                    redelivered.add(message.getBody(String.class) + ":"
                            + message.getJMSRedelivered());
                }
                assertThat(redelivered).containsExactly("A-1:true", "A-2:true", "A-3:false");
                auditor.acknowledge();
                consumer.close();
            }
            assertThat(browseCorrelationIds(service, audit)).isEmpty();

            try (JMSContext batcher = service.createContext(JMSContext.SESSION_TRANSACTED)) {
                assertThat(batcher.getTransacted()).isTrue();
                JMSProducer digestProducer = batcher.createProducer();
                digestProducer.setJMSCorrelationID("D-draft").send(digests, "draft digest");
                batcher.rollback();
                digestProducer.setJMSCorrelationID("D-1").send(digests, "morning digest");
                digestProducer.setJMSCorrelationID("D-2").send(digests, "evening digest");
                batcher.commit();
                assertThat(browseCorrelationIds(service, digests)).containsExactly("D-1", "D-2");

                JMSConsumer consumer = batcher.createConsumer(digests);
                assertThat(consumer.receive(RECEIVE_TIMEOUT_MILLIS).getJMSCorrelationID())
                        .isEqualTo("D-1");
                batcher.rollback();
                Message retried = consumer.receive(RECEIVE_TIMEOUT_MILLIS);
                assertThat(retried.getJMSCorrelationID()).isEqualTo("D-1");
                assertThat(retried.getJMSRedelivered()).isTrue();
                assertThat(consumer.receive(RECEIVE_TIMEOUT_MILLIS).getJMSCorrelationID())
                        .isEqualTo("D-2");
                batcher.commit();
                consumer.close();
            }
            assertThat(browseCorrelationIds(service, digests)).isEmpty();
        }
    }

    @Test
    void expiredPasscodeIsDeadLetteredInsteadOfDelivered() throws Exception {
        try (JMSContext context = factory.createContext()) {
            Queue gateway = context.createQueue("notifications.sms-gateway");
            JMSProducer producer = context.createProducer();

            TextMessage stale = context.createTextMessage("Passcode 112358");
            stale.setJMSCorrelationID("OTP-stale");
            producer.setTimeToLive(1L).send(gateway, stale);
            long expiration = stale.getJMSExpiration();
            assertThat(expiration).isPositive();
            assertThat(producer.getTimeToLive()).isEqualTo(1L);

            long deadline = System.currentTimeMillis() + RECEIVE_TIMEOUT_MILLIS;
            while (System.currentTimeMillis() <= expiration) {
                assertThat(System.currentTimeMillis()).isLessThan(deadline);
                Thread.onSpinWait();
            }

            producer.setTimeToLive(0L).setJMSCorrelationID("OTP-fresh")
                    .send(gateway, "Passcode 314159");

            try (JMSConsumer sms = context.createConsumer(gateway)) {
                Message delivered = sms.receive(RECEIVE_TIMEOUT_MILLIS);
                assertThat(delivered.getJMSCorrelationID()).isEqualTo("OTP-fresh");
                assertThat(delivered.getJMSExpiration()).isZero();
                assertThat(delivered.getBody(String.class)).isEqualTo("Passcode 314159");
                assertThat(sms.receiveNoWait()).isNull();
            }

            try (JMSConsumer deadLetters = context.createConsumer(
                    context.createQueue("ActiveMQ.DLQ"))) {
                Message dead = deadLetters.receive(RECEIVE_TIMEOUT_MILLIS);
                assertThat(dead).isNotNull();
                assertThat(dead.getJMSCorrelationID()).isEqualTo("OTP-stale");
                assertThat(dead.getBody(String.class)).isEqualTo("Passcode 112358");
            }
        }
    }

    @Test
    void reportsMisuseThroughUncheckedRuntimeExceptions() {
        try (JMSContext context = factory.createContext()) {
            Queue outbox = context.createQueue("notifications.invalid");
            assertThatThrownBy(() -> context.createConsumer(outbox, "channel = = 'sms'"))
                    .isInstanceOf(InvalidSelectorRuntimeException.class);
            assertThatThrownBy(() -> context.createProducer().setPriority(12))
                    .isInstanceOf(IllegalStateRuntimeException.class)
                    .hasMessageContaining("between 0 and 9");
        }
    }

    private static List<String> browseCorrelationIds(JMSContext context, Queue queue)
            throws Exception {
        List<String> ids = new ArrayList<>();
        try (QueueBrowser browser = context.createBrowser(queue)) {
            Enumeration<?> messages = browser.getEnumeration();
            for (Object message : Collections.list(messages)) {
                ids.add(((Message) message).getJMSCorrelationID());
            }
        }
        return ids;
    }
}
