/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_nats.jnats;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.PublishOptions;
import io.nats.client.PullSubscribeOptions;
import io.nats.client.api.AckPolicy;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.ConsumerInfo;
import io.nats.client.api.MessageInfo;
import io.nats.client.api.PublishAck;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Runs an order-fulfillment work queue on a JetStream stream with a durable pull consumer
 * through the jnats JetStream publish, subscribe, and management API.
 *
 * A storefront connection defines the stream and publishes JSON orders with message IDs and
 * headers, so duplicates are dropped and stale sequence expectations are rejected, while a
 * fulfillment worker creates a durable consumer, fetches batches, acknowledges handled
 * orders, negatively acknowledges one to get it redelivered, and then inspects the consumer
 * and stream state.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderFulfillmentStreamTest {

    private static final String NATS_IMAGE = "nats:2.15.1";

    private static final String STREAM = "ORDERS";

    private static final String SUBJECT = "orders.created";

    private static final String DURABLE = "fulfillment";

    private final ObjectMapper mapper = new ObjectMapper();

    private final String containerName = "real-use-jnats-stream-" + UUID.randomUUID();

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
    void fulfillsOrdersFromDurablePullConsumer() throws Exception {
        try (Connection storefront = connect(); Connection worker = connect()) {
            JetStreamManagement management = storefront.jetStreamManagement();
            StreamInfo created = management.addStream(StreamConfiguration.builder()
                    .name(STREAM)
                    .description("Orders awaiting fulfillment")
                    .subjects("orders.>")
                    .storageType(StorageType.Memory)
                    .duplicateWindow(Duration.ofMinutes(2))
                    .build());
            assertThat(created.getConfiguration().getSubjects()).containsExactly("orders.>");
            assertThat(management.getStreamNames()).contains(STREAM);

            JetStream orders = storefront.jetStream();
            PublishAck first = publishOrder(orders, new Order("order-1", "keyboard", 1), "eu");
            PublishAck second = publishOrder(orders, new Order("order-2", "monitor", 2), "us");
            assertThat(first.getStream()).isEqualTo(STREAM);
            assertThat(first.isDuplicate()).isFalse();
            assertThat(second.getSeqno()).isEqualTo(first.getSeqno() + 1);

            PublishAck retried = publishOrder(orders, new Order("order-2", "monitor", 2), "us");
            assertThat(retried.isDuplicate()).isTrue();
            assertThat(retried.getSeqno()).isEqualTo(second.getSeqno());

            JetStreamApiException stale = catchThrowableOfType(
                    () -> orders.publish(SUBJECT, order(new Order("order-x", "mouse", 1)),
                            PublishOptions.builder().expectedLastSequence(first.getSeqno()).build()),
                    JetStreamApiException.class);
            assertThat(stale.getApiErrorCode()).isEqualTo(10071);

            PublishAck third = publishOrder(orders, new Order("order-3", "dock", 1), "eu");
            assertThat(third.getSeqno()).isEqualTo(second.getSeqno() + 1);

            PullSubscribeOptions pullOptions = PullSubscribeOptions.builder()
                    .stream(STREAM)
                    .durable(DURABLE)
                    .configuration(ConsumerConfiguration.builder()
                            .ackPolicy(AckPolicy.Explicit)
                            .ackWait(Duration.ofSeconds(30))
                            .build())
                    .build();
            JetStreamSubscription subscription = worker.jetStream().subscribe(SUBJECT, pullOptions);
            assertThat(subscription.getConsumerName()).isEqualTo(DURABLE);
            ConsumerInfo consumer = subscription.getConsumerInfo();
            assertThat(consumer.getStreamName()).isEqualTo(STREAM);
            assertThat(consumer.getConsumerConfiguration().getFilterSubject()).isEqualTo(SUBJECT);
            assertThat(consumer.getNumPending()).isEqualTo(3);

            List<Message> batch = subscription.fetch(10, Duration.ofSeconds(3));
            assertThat(batch).extracting(message -> readOrder(message).id())
                    .containsExactly("order-1", "order-2", "order-3");
            assertThat(batch.get(0).getHeaders().getFirst("Order-Region")).isEqualTo("eu");
            assertThat(batch.get(1).metaData().streamSequence()).isEqualTo(second.getSeqno());
            assertThat(batch.get(1).metaData().deliveredCount()).isEqualTo(1);

            batch.get(0).ackSync(Duration.ofSeconds(5));
            batch.get(1).nak();
            batch.get(2).ackSync(Duration.ofSeconds(5));

            List<Message> redelivered = subscription.fetch(10, Duration.ofSeconds(3));
            assertThat(redelivered).hasSize(1);
            Message retry = redelivered.get(0);
            assertThat(readOrder(retry)).isEqualTo(new Order("order-2", "monitor", 2));
            assertThat(retry.metaData().deliveredCount()).isEqualTo(2);
            assertThat(retry.metaData().getConsumer()).isEqualTo(DURABLE);
            retry.ackSync(Duration.ofSeconds(5));

            ConsumerInfo drained = subscription.getConsumerInfo();
            assertThat(drained.getNumPending()).isZero();
            assertThat(drained.getNumAckPending()).isZero();
            assertThat(drained.getAckFloor().getStreamSequence()).isEqualTo(third.getSeqno());
            assertThat(management.getConsumerNames(STREAM)).containsExactly(DURABLE);
            subscription.unsubscribe();

            MessageInfo latest = management.getLastMessage(STREAM, SUBJECT);
            assertThat(latest.getSeq()).isEqualTo(third.getSeqno());
            assertThat(latest.getHeaders().getFirst("Order-Region")).isEqualTo("eu");
            assertThat(mapper.readValue(latest.getData(), Order.class))
                    .isEqualTo(new Order("order-3", "dock", 1));

            StreamInfo info = management.getStreamInfo(STREAM);
            assertThat(info.getConfiguration().getDescription()).isEqualTo("Orders awaiting fulfillment");
            assertThat(info.getStreamState().getMsgCount()).isEqualTo(3);
            assertThat(info.getStreamState().getLastSequence()).isEqualTo(third.getSeqno());
            assertThat(info.getStreamState().getConsumerCount()).isEqualTo(1);

            assertThat(management.deleteConsumer(STREAM, DURABLE)).isTrue();
            assertThat(management.deleteStream(STREAM)).isTrue();
            assertThat(management.getStreamNames()).doesNotContain(STREAM);
        }
    }

    private PublishAck publishOrder(JetStream orders, Order order, String region) throws Exception {
        Headers headers = new Headers().add("Order-Region", region);
        Message message = NatsMessage.builder()
                .subject(SUBJECT)
                .headers(headers)
                .data(order(order))
                .build();
        return orders.publish(message, PublishOptions.builder().messageId(order.id()).build());
    }

    private byte[] order(Order order) throws IOException {
        return mapper.writeValueAsBytes(order);
    }

    private Order readOrder(Message message) {
        try {
            return mapper.readValue(message.getData(), Order.class);
        } catch (IOException e) {
            throw new IllegalStateException("Unreadable order on " + message.getSubject(), e);
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

    /// An order the storefront publishes as the JSON body of one stream message.
    record Order(String id, String sku, int quantity) {
    }
}
