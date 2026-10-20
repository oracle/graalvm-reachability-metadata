/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_nats.jnats;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.api.AckPolicy;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.ConsumerInfo;
import io.nats.client.api.DeliverPolicy;
import io.nats.client.api.PublishAck;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * Feeds a market-ticker dashboard from a JetStream stream through jnats push consumers:
 * an ordered consumer, an asynchronous dispatcher-driven consumer, and a durable replay consumer.
 *
 * A feed connection publishes JSON price ticks per symbol. A chart replays the whole history
 * through an ordered consumer, a dashboard builds its latest-price snapshot from a
 * last-per-subject consumer delivered to a dispatcher, and an auditor replays from a start
 * sequence with flow control and heartbeats, keeps receiving live ticks, acknowledges each one,
 * and then inspects the consumer state.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MarketTickerPushConsumerTest {

    private static final String NATS_IMAGE = "nats:2.15.0";

    private static final String STREAM = "TICKS";

    private static final String SUBJECTS = "ticks.*";

    private static final String AUDITOR = "auditor";

    private final ObjectMapper mapper = new ObjectMapper();

    private final String containerName = "real-use-jnats-push-" + UUID.randomUUID();

    private String serverUrl;

    @BeforeAll
    void startServer() throws Exception {
        int port = freePort();
        serverUrl = "nats://127.0.0.1:" + port;
        Process run = new ProcessBuilder("docker", "run", "-d", "--rm", "--name", containerName,
                "-p", port + ":4222", NATS_IMAGE, "-js")
                .inheritIO()
                .start();
        assertThat(run.waitFor(120, TimeUnit.SECONDS)).isTrue();
        assertThat(run.exitValue()).isZero();
        awaitServer();
    }

    @AfterAll
    void stopServer() throws Exception {
        Process remove = new ProcessBuilder("docker", "rm", "-f", containerName).inheritIO().start();
        remove.waitFor(60, TimeUnit.SECONDS);
    }

    @Test
    void feedsChartDashboardAndAuditorFromPushConsumers() throws Exception {
        try (Connection feed = connect(); Connection client = connect()) {
            JetStreamManagement management = feed.jetStreamManagement();
            management.addStream(StreamConfiguration.builder()
                    .name(STREAM)
                    .subjects(SUBJECTS)
                    .storageType(StorageType.Memory)
                    .build());

            JetStream ticker = feed.jetStream();
            List<Tick> history = List.of(
                    new Tick("ACME", 1000),
                    new Tick("GLOBEX", 2000),
                    new Tick("ACME", 1010),
                    new Tick("INITECH", 500),
                    new Tick("GLOBEX", 2025));
            List<PublishAck> acks = new ArrayList<>();
            for (Tick tick : history) {
                acks.add(publish(ticker, tick));
            }
            assertThat(acks).extracting(PublishAck::getSeqno).containsExactly(1L, 2L, 3L, 4L, 5L);

            JetStream consumers = client.jetStream();
            replayChart(consumers, history);
            buildDashboard(client, consumers);
            auditFromSequence(consumers, ticker, management);

            assertThat(management.deleteStream(STREAM)).isTrue();
        }
    }

    /// Replays every tick in stream order through an ordered consumer, as a price chart does.
    private void replayChart(JetStream consumers, List<Tick> history) throws Exception {
        PushSubscribeOptions ordered = PushSubscribeOptions.builder().ordered(true).build();
        JetStreamSubscription chart = consumers.subscribe(SUBJECTS, ordered);
        List<Tick> replayed = new ArrayList<>();
        long expectedConsumerSequence = 1;
        for (int i = 0; i < history.size(); i++) {
            Message message = chart.nextMessage(Duration.ofSeconds(5));
            assertThat(message).isNotNull();
            assertThat(message.isJetStream()).isTrue();
            assertThat(message.metaData().streamSequence()).isEqualTo(i + 1L);
            assertThat(message.metaData().consumerSequence()).isEqualTo(expectedConsumerSequence++);
            assertThat(message.metaData().pendingCount()).isEqualTo(history.size() - i - 1L);
            replayed.add(readTick(message));
        }
        assertThat(replayed).containsExactlyElementsOf(history);
        assertThat(chart.nextMessage(Duration.ofMillis(500))).isNull();
        chart.unsubscribe();
    }

    /// Builds a latest-price snapshot from a last-per-subject consumer delivered to a dispatcher.
    private void buildDashboard(Connection client, JetStream consumers) throws Exception {
        Map<String, Long> latestPrices = new ConcurrentHashMap<>();
        CountDownLatch symbols = new CountDownLatch(3);
        Dispatcher dispatcher = client.createDispatcher();
        PushSubscribeOptions snapshot = PushSubscribeOptions.builder()
                .configuration(ConsumerConfiguration.builder()
                        .deliverPolicy(DeliverPolicy.LastPerSubject)
                        .ackPolicy(AckPolicy.None)
                        .build())
                .build();
        JetStreamSubscription dashboard = consumers.subscribe(SUBJECTS, dispatcher, message -> {
            Tick tick = readTick(message);
            latestPrices.put(tick.symbol(), tick.priceCents());
            symbols.countDown();
        }, false, snapshot);

        assertThat(symbols.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(latestPrices).containsOnly(
                entry("ACME", 1010L),
                entry("GLOBEX", 2025L),
                entry("INITECH", 500L));
        ConsumerConfiguration configuration = dashboard.getConsumerInfo().getConsumerConfiguration();
        assertThat(configuration.getDeliverPolicy()).isEqualTo(DeliverPolicy.LastPerSubject);
        assertThat(configuration.getDeliverSubject()).isNotBlank();
        dispatcher.unsubscribe(dashboard);
        client.closeDispatcher(dispatcher);
    }

    /// Replays from a start sequence with flow control, then follows live ticks and acks each one.
    private void auditFromSequence(JetStream consumers, JetStream ticker, JetStreamManagement management)
            throws Exception {
        PushSubscribeOptions replay = PushSubscribeOptions.builder()
                .durable(AUDITOR)
                .configuration(ConsumerConfiguration.builder()
                        .deliverPolicy(DeliverPolicy.ByStartSequence)
                        .startSequence(4)
                        .ackPolicy(AckPolicy.Explicit)
                        .flowControl(Duration.ofSeconds(2))
                        .build())
                .build();
        JetStreamSubscription auditor = consumers.subscribe(SUBJECTS, replay);
        ConsumerConfiguration configuration = auditor.getConsumerInfo().getConsumerConfiguration();
        assertThat(configuration.getDurable()).isEqualTo(AUDITOR);
        assertThat(configuration.isFlowControl()).isTrue();
        assertThat(configuration.getIdleHeartbeat()).isEqualTo(Duration.ofSeconds(2));

        List<Tick> audited = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Message message = auditor.nextMessage(Duration.ofSeconds(5));
            assertThat(message).isNotNull();
            audited.add(readTick(message));
            message.ackSync(Duration.ofSeconds(5));
        }
        assertThat(audited).containsExactly(new Tick("INITECH", 500), new Tick("GLOBEX", 2025));

        PublishAck live = publish(ticker, new Tick("ACME", 1042));
        Message liveMessage = auditor.nextMessage(Duration.ofSeconds(5));
        assertThat(liveMessage).isNotNull();
        assertThat(liveMessage.getSubject()).isEqualTo("ticks.ACME");
        assertThat(liveMessage.metaData().streamSequence()).isEqualTo(live.getSeqno());
        assertThat(readTick(liveMessage)).isEqualTo(new Tick("ACME", 1042));
        liveMessage.ackSync(Duration.ofSeconds(5));

        ConsumerInfo state = management.getConsumerInfo(STREAM, AUDITOR);
        assertThat(state.getNumPending()).isZero();
        assertThat(state.getNumAckPending()).isZero();
        assertThat(state.getDelivered().getStreamSequence()).isEqualTo(live.getSeqno());
        assertThat(state.getAckFloor().getConsumerSequence()).isEqualTo(3);

        auditor.unsubscribe();
        assertThat(management.getConsumerNames(STREAM)).contains(AUDITOR);
        assertThat(management.deleteConsumer(STREAM, AUDITOR)).isTrue();
        assertThat(management.getConsumerNames(STREAM)).doesNotContain(AUDITOR);
    }

    private PublishAck publish(JetStream ticker, Tick tick) throws Exception {
        return ticker.publish("ticks." + tick.symbol(), mapper.writeValueAsBytes(tick));
    }

    private Tick readTick(Message message) {
        try {
            return mapper.readValue(message.getData(), Tick.class);
        } catch (IOException e) {
            throw new IllegalStateException("Unreadable tick on " + message.getSubject(), e);
        }
    }

    private Connection connect() throws Exception {
        Options options = new Options.Builder()
                .server(serverUrl)
                .connectionTimeout(Duration.ofSeconds(2))
                .build();
        return Nats.connect(options);
    }

    private void awaitServer() throws Exception {
        long deadline = System.currentTimeMillis() + 120_000L;
        IOException lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try (Connection probe = connect()) {
                return;
            } catch (IOException e) {
                lastFailure = e;
                Thread.sleep(500L);
            }
        }
        throw new IllegalStateException("NATS did not accept connections at " + serverUrl, lastFailure);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /// A price tick the feed publishes as the JSON body of one stream message.
    record Tick(String symbol, long priceCents) {
    }
}
