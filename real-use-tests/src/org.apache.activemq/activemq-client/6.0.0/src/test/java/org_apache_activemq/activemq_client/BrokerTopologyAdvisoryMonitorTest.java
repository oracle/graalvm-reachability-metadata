/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client;

import jakarta.jms.Connection;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.advisory.AdvisorySupport;
import org.apache.activemq.advisory.ConsumerEvent;
import org.apache.activemq.advisory.ConsumerEventSource;
import org.apache.activemq.advisory.DestinationEvent;
import org.apache.activemq.advisory.DestinationSource;
import org.apache.activemq.advisory.ProducerEvent;
import org.apache.activemq.advisory.ProducerEventSource;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.TransportConnector;
import org.apache.activemq.command.ActiveMQMessage;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ActiveMQTopic;
import org.apache.activemq.command.ConsumerInfo;
import org.apache.activemq.command.DataStructure;
import org.apache.activemq.command.RemoveInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Watches broker topology through advisory messages: raw consumer advisories carrying
 * `ConsumerInfo` and `RemoveInfo` commands, the `ConsumerEventSource` and
 * `ProducerEventSource` listeners that count attached clients, and the connection's
 * `DestinationSource` that discovers queues as they are created and destroyed.
 *
 * An operations console for a shipping platform must know, without polling the broker's
 * management interface, which label printers are attached to the print queue, which
 * dispatchers are feeding it, and which warehouse restock queues exist right now. It
 * learns all of that by listening to the advisory topics the broker publishes.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BrokerTopologyAdvisoryMonitorTest {

    private static final String PRINT_QUEUE = "shipping.labels.print";

    private static final String RESTOCK_QUEUE = "warehouse.restock.north";

    private static final long EVENT_TIMEOUT_MILLIS = 10_000L;

    private BrokerService broker;

    private ActiveMQConnectionFactory factory;

    @BeforeAll
    void startBroker() throws Exception {
        broker = new BrokerService();
        broker.setBrokerName("topology-broker");
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.setUseShutdownHook(false);
        broker.setAdvisorySupport(true);
        TransportConnector connector = broker.addConnector("tcp://127.0.0.1:0");
        broker.start();
        assertThat(broker.waitUntilStarted()).isTrue();
        factory = new ActiveMQConnectionFactory(connector.getConnectUri().toString());
    }

    @AfterAll
    void stopBroker() throws Exception {
        broker.stop();
        broker.waitUntilStopped();
    }

    @Test
    void consumerAdvisoriesDescribePrintersJoiningAndLeaving() throws Exception {
        Connection console = factory.createConnection();
        Connection printers = factory.createConnection();
        try {
            console.start();
            printers.start();
            Session consoleSession = console.createSession(false, Session.AUTO_ACKNOWLEDGE);
            ActiveMQQueue printQueue = new ActiveMQQueue(PRINT_QUEUE + ".raw");
            ActiveMQTopic advisoryTopic = AdvisorySupport.getConsumerAdvisoryTopic(printQueue);
            assertThat(advisoryTopic.getPhysicalName())
                    .isEqualTo("ActiveMQ.Advisory.Consumer.Queue." + PRINT_QUEUE + ".raw");
            MessageConsumer advisories = consoleSession.createConsumer(advisoryTopic);

            Session printerSession = printers.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageConsumer printer = printerSession.createConsumer(printQueue);

            ActiveMQMessage joined = receiveAdvisory(advisories);
            assertThat(joined.getJMSDestination()).isEqualTo(advisoryTopic);
            assertThat(joined.getIntProperty("consumerCount")).isEqualTo(1);
            DataStructure joinedCommand = joined.getDataStructure();
            assertThat(joinedCommand).isInstanceOf(ConsumerInfo.class);
            ConsumerInfo printerInfo = (ConsumerInfo) joinedCommand;
            assertThat(printerInfo.getDestination()).isEqualTo(printQueue);
            assertThat(printerInfo.getPrefetchSize())
                    .isEqualTo(factory.getPrefetchPolicy().getQueuePrefetch());
            assertThat(printerInfo.getConsumerId().getConnectionId())
                    .isEqualTo(((ActiveMQConnection) printers).getConnectionInfo()
                            .getConnectionId().getValue());

            printer.close();

            ActiveMQMessage left = receiveAdvisory(advisories);
            assertThat(left.getIntProperty("consumerCount")).isZero();
            DataStructure leftCommand = left.getDataStructure();
            assertThat(leftCommand).isInstanceOf(RemoveInfo.class);
            assertThat(((RemoveInfo) leftCommand).getObjectId()).isEqualTo(printerInfo.getConsumerId());
        } finally {
            printers.close();
            console.close();
        }
    }

    @Test
    void eventSourcesCountPrintersAndDispatchersOnTheQueue() throws Exception {
        ActiveMQConnection console = (ActiveMQConnection) factory.createConnection();
        Connection clients = factory.createConnection();
        BlockingQueue<ConsumerEvent> consumerEvents = new LinkedBlockingQueue<>();
        BlockingQueue<ProducerEvent> producerEvents = new LinkedBlockingQueue<>();
        ActiveMQQueue printQueue = new ActiveMQQueue(PRINT_QUEUE);
        ConsumerEventSource printerWatch = new ConsumerEventSource(console, printQueue);
        ProducerEventSource dispatcherWatch = new ProducerEventSource(console, printQueue);
        try {
            console.start();
            clients.start();
            printerWatch.setConsumerListener(consumerEvents::add);
            dispatcherWatch.setProducerListener(producerEvents::add);
            printerWatch.start();
            dispatcherWatch.start();

            Session session = clients.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue(PRINT_QUEUE);
            MessageConsumer firstPrinter = session.createConsumer(queue);
            ConsumerEvent firstStarted = nextEvent(consumerEvents);
            assertThat(firstStarted.isStarted()).isTrue();
            assertThat(firstStarted.getConsumerCount()).isEqualTo(1);
            assertThat(firstStarted.getDestination()).isEqualTo(printQueue);

            MessageConsumer secondPrinter = session.createConsumer(queue);
            ConsumerEvent secondStarted = nextEvent(consumerEvents);
            assertThat(secondStarted.isStarted()).isTrue();
            assertThat(secondStarted.getConsumerCount()).isEqualTo(2);
            assertThat(secondStarted.getConsumerId()).isNotEqualTo(firstStarted.getConsumerId());

            MessageProducer dispatcher = session.createProducer(queue);
            ProducerEvent dispatcherStarted = nextEvent(producerEvents);
            assertThat(dispatcherStarted.isStarted()).isTrue();
            assertThat(dispatcherStarted.getProducerCount()).isEqualTo(1);
            assertThat(dispatcherStarted.getDestination()).isEqualTo(printQueue);

            dispatcher.close();
            ProducerEvent dispatcherStopped = nextEvent(producerEvents);
            assertThat(dispatcherStopped.isStarted()).isFalse();
            assertThat(dispatcherStopped.getProducerCount()).isZero();
            assertThat(dispatcherStopped.getProducerId()).isEqualTo(dispatcherStarted.getProducerId());

            firstPrinter.close();
            ConsumerEvent firstStopped = nextEvent(consumerEvents);
            assertThat(firstStopped.isStarted()).isFalse();
            assertThat(firstStopped.getConsumerCount()).isEqualTo(1);
            assertThat(firstStopped.getConsumerId()).isEqualTo(firstStarted.getConsumerId());

            secondPrinter.close();
            ConsumerEvent secondStopped = nextEvent(consumerEvents);
            assertThat(secondStopped.isStarted()).isFalse();
            assertThat(secondStopped.getConsumerCount()).isZero();
        } finally {
            printerWatch.stop();
            dispatcherWatch.stop();
            clients.close();
            console.close();
        }
    }

    @Test
    void destinationSourceDiscoversRestockQueuesAsTheyComeAndGo() throws Exception {
        ActiveMQConnection console = (ActiveMQConnection) factory.createConnection();
        Connection warehouse = factory.createConnection();
        BlockingQueue<DestinationEvent> destinationEvents = new LinkedBlockingQueue<>();
        try {
            console.start();
            warehouse.start();
            DestinationSource destinations = console.getDestinationSource();
            destinations.setDestinationListener(destinationEvents::add);

            Session session = warehouse.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue restock = session.createQueue(RESTOCK_QUEUE);
            MessageProducer producer = session.createProducer(restock);
            producer.send(session.createTextMessage("pallets:12"));
            producer.close();

            DestinationEvent created = nextEventFor(destinationEvents, RESTOCK_QUEUE);
            assertThat(created.isAddOperation()).isTrue();
            assertThat(created.getDestination()).isEqualTo(new ActiveMQQueue(RESTOCK_QUEUE));
            assertThat(destinations.getQueues()).contains(new ActiveMQQueue(RESTOCK_QUEUE));

            MessageConsumer drain = session.createConsumer(restock);
            Message pending = drain.receive(EVENT_TIMEOUT_MILLIS);
            assertThat(pending).isNotNull();
            drain.close();

            ((ActiveMQConnection) warehouse).destroyDestination(new ActiveMQQueue(RESTOCK_QUEUE));

            DestinationEvent removed = nextEventFor(destinationEvents, RESTOCK_QUEUE);
            assertThat(removed.isRemoveOperation()).isTrue();
            awaitCondition(() -> !destinations.getQueues().contains(new ActiveMQQueue(RESTOCK_QUEUE)));
            assertThat(destinations.getQueues()).doesNotContain(new ActiveMQQueue(RESTOCK_QUEUE));
        } finally {
            warehouse.close();
            console.close();
        }
    }

    private ActiveMQMessage receiveAdvisory(MessageConsumer advisories) throws Exception {
        Message message = advisories.receive(EVENT_TIMEOUT_MILLIS);
        assertThat(message).isInstanceOf(ActiveMQMessage.class);
        return (ActiveMQMessage) message;
    }

    private static <T> T nextEvent(BlockingQueue<T> events) throws InterruptedException {
        T event = events.poll(EVENT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertThat(event).isNotNull();
        return event;
    }

    /// Skips events for destinations other than `queueName`, such as advisory topics.
    private static DestinationEvent nextEventFor(BlockingQueue<DestinationEvent> events, String queueName)
            throws InterruptedException {
        while (true) {
            DestinationEvent event = nextEvent(events);
            if (event.getDestination().isQueue()
                    && event.getDestination().getPhysicalName().equals(queueName)) {
                return event;
            }
        }
    }

    private static void awaitCondition(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(EVENT_TIMEOUT_MILLIS);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(20L);
        }
    }
}
