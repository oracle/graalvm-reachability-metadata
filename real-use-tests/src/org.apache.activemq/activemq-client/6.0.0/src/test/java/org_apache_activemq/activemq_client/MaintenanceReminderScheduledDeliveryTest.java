/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client;

import jakarta.jms.Connection;
import jakarta.jms.Destination;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.QueueBrowser;
import jakarta.jms.Session;
import jakarta.jms.TemporaryQueue;
import jakarta.jms.TextMessage;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ScheduledMessage;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.TransportConnector;
import org.apache.activemq.command.ActiveMQTopic;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Schedules delayed and repeating deliveries through the broker job scheduler with the
 * client's `ScheduledMessage` properties: `AMQ_SCHEDULED_DELAY`, `AMQ_SCHEDULED_PERIOD`,
 * `AMQ_SCHEDULED_REPEAT` and `AMQ_SCHEDULED_CRON` on producer sends, and the
 * `ActiveMQ.Scheduler.Management` topic with `BROWSE`, `REMOVE` and `REMOVEALL` actions
 * replying to a temporary queue with each pending job's `scheduledJobId`.
 *
 * A fleet maintenance planner books a short-delay inspection reminder, a repeating
 * tyre-pressure check, a far-future service reminder and a nightly cron audit. The
 * workshop queue sees only what is due, and the planner lists the still-pending jobs,
 * cancels one by id, and finally clears the schedule.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MaintenanceReminderScheduledDeliveryTest {

    private static final String WORKSHOP = "fleet.workshop.reminders";

    private static final long RECEIVE_TIMEOUT_MILLIS = 15_000L;

    private static final long FAR_FUTURE_DELAY_MILLIS = 3_600_000L;

    private BrokerService broker;

    private String brokerUrl;

    @BeforeAll
    void startBroker() throws Exception {
        broker = new BrokerService();
        broker.setBrokerName("fleet-planner-broker");
        broker.setPersistent(false);
        broker.setSchedulerSupport(true);
        broker.setUseJmx(false);
        broker.setUseShutdownHook(false);
        TransportConnector connector = broker.addConnector("tcp://127.0.0.1:0");
        broker.start();
        assertThat(broker.waitUntilStarted()).isTrue();
        brokerUrl = connector.getConnectUri().toString();
    }

    @AfterAll
    void stopBroker() throws Exception {
        broker.stop();
        broker.waitUntilStopped();
    }

    @Test
    void deliversDueRemindersAndManagesPendingJobs() throws Exception {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        Connection connection = factory.createConnection();
        try {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue workshop = session.createQueue(WORKSHOP);
            MessageProducer planner = session.createProducer(workshop);
            MessageConsumer mechanic = session.createConsumer(workshop);

            planner.send(reminder(session, "service-truck-07", FAR_FUTURE_DELAY_MILLIS));
            planner.send(reminder(session, "inspect-van-12", 250L));
            TextMessage inspection = (TextMessage) mechanic.receive(RECEIVE_TIMEOUT_MILLIS);
            assertThat(inspection).as("short-delay inspection reminder").isNotNull();
            assertThat(inspection.getText()).isEqualTo("inspect-van-12");
            assertThat(inspection.getStringProperty("vehicle")).isEqualTo("van-12");
            assertThat(inspection.getJMSDestination()).isEqualTo(workshop);
            assertThat(queuedReminders(session, workshop)).isEmpty();

            TextMessage tyreCheck = reminder(session, "tyre-check-bus-03", 100L);
            tyreCheck.setLongProperty(ScheduledMessage.AMQ_SCHEDULED_PERIOD, 150L);
            tyreCheck.setIntProperty(ScheduledMessage.AMQ_SCHEDULED_REPEAT, 2);
            planner.send(tyreCheck);
            List<String> repeats = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                TextMessage repeat = (TextMessage) mechanic.receive(RECEIVE_TIMEOUT_MILLIS);
                assertThat(repeat).as("tyre check delivery %d", i).isNotNull();
                repeats.add(repeat.getText());
            }
            assertThat(repeats).containsOnly("tyre-check-bus-03").hasSize(3);

            TextMessage audit = session.createTextMessage("nightly-fleet-audit");
            audit.setStringProperty(ScheduledMessage.AMQ_SCHEDULED_CRON, "0 2 * * *");
            planner.send(audit);

            TemporaryQueue listing = session.createTemporaryQueue();
            List<Message> pending = browseSchedule(session, listing, 2);
            assertThat(pending).extracting(job -> ((TextMessage) job).getText())
                    .containsExactlyInAnyOrder("service-truck-07", "nightly-fleet-audit");
            Message service = pending.stream()
                    .filter(job -> "truck-07".equals(property(job, "vehicle")))
                    .findFirst().orElseThrow();
            Message nightly = pending.stream()
                    .filter(job -> property(job, "vehicle") == null)
                    .findFirst().orElseThrow();
            assertThat(nightly.getStringProperty(ScheduledMessage.AMQ_SCHEDULED_CRON))
                    .isEqualTo("0 2 * * *");
            assertThat(service.getLongProperty(ScheduledMessage.AMQ_SCHEDULED_DELAY))
                    .isEqualTo(FAR_FUTURE_DELAY_MILLIS);
            String serviceJobId = service.getStringProperty(ScheduledMessage.AMQ_SCHEDULED_ID);
            assertThat(serviceJobId).isNotBlank()
                    .isNotEqualTo(nightly.getStringProperty(ScheduledMessage.AMQ_SCHEDULED_ID));

            Message cancel = session.createMessage();
            cancel.setStringProperty(ScheduledMessage.AMQ_SCHEDULER_ACTION,
                    ScheduledMessage.AMQ_SCHEDULER_ACTION_REMOVE);
            cancel.setStringProperty(ScheduledMessage.AMQ_SCHEDULED_ID, serviceJobId);
            manage(session, cancel);
            assertThat(browseSchedule(session, listing, 1))
                    .extracting(job -> ((TextMessage) job).getText())
                    .containsExactly("nightly-fleet-audit");

            Message clear = session.createMessage();
            clear.setStringProperty(ScheduledMessage.AMQ_SCHEDULER_ACTION,
                    ScheduledMessage.AMQ_SCHEDULER_ACTION_REMOVEALL);
            manage(session, clear);
            planner.send(reminder(session, "service-coach-21", FAR_FUTURE_DELAY_MILLIS));
            assertThat(browseSchedule(session, listing, 1))
                    .extracting(job -> ((TextMessage) job).getText())
                    .containsExactly("service-coach-21");
            assertThat(queuedReminders(session, workshop)).isEmpty();
        } finally {
            connection.close();
        }
    }

    /// Builds a reminder for the vehicle named after the first dash, due after `delayMillis`.
    private static TextMessage reminder(Session session, String task, long delayMillis)
            throws Exception {
        TextMessage message = session.createTextMessage(task);
        message.setStringProperty("vehicle", task.substring(task.indexOf('-') + 1));
        message.setLongProperty(ScheduledMessage.AMQ_SCHEDULED_DELAY, delayMillis);
        return message;
    }

    /// Asks the scheduler to copy every pending job to `listing` and collects `expected` of them.
    private static List<Message> browseSchedule(Session session, TemporaryQueue listing,
            int expected) throws Exception {
        Message browse = session.createMessage();
        browse.setStringProperty(ScheduledMessage.AMQ_SCHEDULER_ACTION,
                ScheduledMessage.AMQ_SCHEDULER_ACTION_BROWSE);
        browse.setJMSReplyTo(listing);
        manage(session, browse);
        List<Message> jobs = new ArrayList<>();
        try (MessageConsumer reader = session.createConsumer(listing)) {
            for (int i = 0; i < expected; i++) {
                Message job = reader.receive(RECEIVE_TIMEOUT_MILLIS);
                assertThat(job).as("scheduled job %d of %d", i, expected).isNotNull();
                assertThat(job.getJMSDestination()).isEqualTo(listing);
                jobs.add(job);
            }
            assertThat(reader.receiveNoWait()).isNull();
        }
        return jobs;
    }

    /// Sends a scheduler action to the broker's management topic.
    private static void manage(Session session, Message action) throws Exception {
        Destination management =
                new ActiveMQTopic(ScheduledMessage.AMQ_SCHEDULER_MANAGEMENT_DESTINATION);
        try (MessageProducer producer = session.createProducer(management)) {
            producer.send(action);
        }
    }

    /// Lists the reminders currently waiting on the workshop queue itself.
    private static List<String> queuedReminders(Session session, Queue workshop)
            throws Exception {
        List<String> queued = new ArrayList<>();
        try (QueueBrowser browser = session.createBrowser(workshop)) {
            Enumeration<?> messages = browser.getEnumeration();
            while (messages.hasMoreElements()) {
                queued.add(((TextMessage) messages.nextElement()).getText());
            }
        }
        return queued;
    }

    private static String property(Message message, String name) {
        try {
            return message.getStringProperty(name);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
