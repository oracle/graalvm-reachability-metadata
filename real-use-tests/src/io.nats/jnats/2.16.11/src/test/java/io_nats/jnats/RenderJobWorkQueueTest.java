/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_nats.jnats;

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
import io.nats.client.Subscription;
import io.nats.client.api.AckPolicy;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.ConsumerInfo;
import io.nats.client.api.MessageInfo;
import io.nats.client.api.PublishAck;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.support.JsonParser;
import io.nats.client.support.JsonValue;
import io.nats.client.support.JsonValueUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Runs a render-job work queue on a JetStream work-queue stream through asynchronous publishing,
 * iterated pull delivery, per-message delivery control, and max-delivery advisories.
 *
 * A render farm frontend publishes jobs with `publishAsync` and gets a failed future back for a
 * stale subject-sequence expectation. A renderer pulls the jobs through an iterator and handles
 * each one differently: it extends one with `inProgress` and acknowledges it, terminates a
 * malformed one, delays another with `nakWithDelay`, and abandons one until the server gives up
 * on it. The scenario then reads the max-deliveries advisory with the jnats JSON parser and
 * checks which jobs the work queue removed.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RenderJobWorkQueueTest {

    private static final String NATS_IMAGE = "nats:2.15.1";

    private static final String STREAM = "RENDER_JOBS";

    private static final String SUBJECTS = "render.jobs.>";

    private static final String DURABLE = "renderer";

    private final String containerName = "real-use-jnats-workqueue-" + UUID.randomUUID();

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
    void rendersJobsFromWorkQueueWithDeliveryControl() throws Exception {
        try (Connection frontend = connect(); Connection renderer = connect()) {
            JetStreamManagement management = frontend.jetStreamManagement();
            management.addStream(StreamConfiguration.builder()
                    .name(STREAM)
                    .subjects(SUBJECTS)
                    .retentionPolicy(RetentionPolicy.WorkQueue)
                    .storageType(StorageType.Memory)
                    .build());

            JetStream jobs = frontend.jetStream();
            List<String> jobIds = List.of("thumb-1", "poster-2", "thumb-3", "poster-4", "thumb-5");
            List<CompletableFuture<PublishAck>> pending = new ArrayList<>();
            for (String jobId : jobIds) {
                pending.add(jobs.publishAsync(subjectOf(jobId), jobId.getBytes(StandardCharsets.UTF_8)));
            }
            CompletableFuture.allOf(pending.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);
            Map<String, Long> sequences = new HashMap<>();
            for (int index = 0; index < jobIds.size(); index++) {
                PublishAck ack = pending.get(index).get();
                assertThat(ack.getStream()).isEqualTo(STREAM);
                sequences.put(jobIds.get(index), ack.getSeqno());
            }
            assertThat(sequences.values()).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L);

            CompletableFuture<PublishAck> stale = jobs.publishAsync("render.jobs.poster",
                    "poster-x".getBytes(StandardCharsets.UTF_8),
                    PublishOptions.builder().expectedLastSubjectSequence(sequences.get("poster-2")).build());
            ExecutionException rejected = catchThrowableOfType(
                    () -> stale.get(10, TimeUnit.SECONDS), ExecutionException.class);
            assertThat(rejected).hasRootCauseInstanceOf(JetStreamApiException.class);
            JetStreamApiException wrongSequence = (JetStreamApiException) rejected.getCause().getCause();
            assertThat(wrongSequence.getApiErrorCode()).isEqualTo(10071);

            Subscription advisories = frontend.subscribe(
                    "$JS.EVENT.ADVISORY.CONSUMER.MAX_DELIVERIES." + STREAM + "." + DURABLE);
            frontend.flush(Duration.ofSeconds(5));

            JetStreamSubscription work = renderer.jetStream().subscribe(SUBJECTS, PullSubscribeOptions.builder()
                    .stream(STREAM)
                    .durable(DURABLE)
                    .configuration(ConsumerConfiguration.builder()
                            .ackPolicy(AckPolicy.Explicit)
                            .ackWait(Duration.ofSeconds(1))
                            .maxDeliver(2)
                            .build())
                    .build());
            assertThat(work.getConsumerInfo().getNumPending()).isEqualTo(jobIds.size());

            JetStreamApiException secondConsumer = catchThrowableOfType(
                    () -> renderer.jetStream().subscribe(SUBJECTS, PullSubscribeOptions.builder()
                            .stream(STREAM)
                            .durable("auditor")
                            .build()),
                    JetStreamApiException.class);
            assertThat(secondConsumer.getApiErrorCode()).isEqualTo(10100);

            List<String> firstRound = new ArrayList<>();
            Iterator<Message> batch = work.iterate(jobIds.size(), Duration.ofSeconds(5));
            while (batch.hasNext()) {
                Message message = batch.next();
                String jobId = jobIdOf(message);
                firstRound.add(jobId);
                assertThat(message.metaData().deliveredCount()).isEqualTo(1);
                switch (jobId) {
                    case "thumb-1" -> {
                        message.inProgress();
                        message.ackSync(Duration.ofSeconds(5));
                    }
                    case "poster-2" -> message.term();
                    case "thumb-3" -> message.nakWithDelay(Duration.ofMillis(300));
                    case "poster-4" -> assertThat(message.getSubject()).isEqualTo("render.jobs.poster");
                    default -> message.ackSync(Duration.ofSeconds(5));
                }
            }
            assertThat(firstRound).containsExactlyElementsOf(jobIds);

            Map<String, Long> redeliveries = new HashMap<>();
            Iterator<Message> retries = work.iterate(10, Duration.ofSeconds(4));
            while (retries.hasNext()) {
                Message message = retries.next();
                String jobId = jobIdOf(message);
                redeliveries.put(jobId, message.metaData().deliveredCount());
                if (jobId.equals("thumb-3")) {
                    message.ackSync(Duration.ofSeconds(5));
                }
            }
            assertThat(redeliveries).containsOnly(Map.entry("thumb-3", 2L), Map.entry("poster-4", 2L));

            Message advisory = advisories.nextMessage(Duration.ofSeconds(10));
            assertThat(advisory).isNotNull();
            JsonValue event = JsonParser.parse(advisory.getData());
            assertThat(JsonValueUtils.readString(event, "type"))
                    .isEqualTo("io.nats.jetstream.advisory.v1.max_deliver");
            assertThat(JsonValueUtils.readString(event, "stream")).isEqualTo(STREAM);
            assertThat(JsonValueUtils.readString(event, "consumer")).isEqualTo(DURABLE);
            assertThat(JsonValueUtils.readLong(event, "stream_seq")).isEqualTo(sequences.get("poster-4"));
            assertThat(JsonValueUtils.readLong(event, "deliveries")).isEqualTo(2L);

            ConsumerInfo consumer = work.getConsumerInfo();
            assertThat(consumer.getNumPending()).isZero();
            assertThat(consumer.getNumAckPending()).isZero();

            MessageInfo abandoned = management.getMessage(STREAM, sequences.get("poster-4"));
            assertThat(new String(abandoned.getData(), StandardCharsets.UTF_8)).isEqualTo("poster-4");
            for (String done : List.of("thumb-1", "thumb-3", "thumb-5")) {
                JetStreamApiException removed = catchThrowableOfType(
                        () -> management.getMessage(STREAM, sequences.get(done)), JetStreamApiException.class);
                assertThat(removed.getApiErrorCode()).isEqualTo(10037);
            }

            work.unsubscribe();
            assertThat(management.deleteStream(STREAM)).isTrue();
        }
    }

    private static String subjectOf(String jobId) {
        return "render.jobs." + (jobId.startsWith("thumb") ? "thumbnail" : "poster");
    }

    private static String jobIdOf(Message message) {
        return new String(message.getData(), StandardCharsets.UTF_8);
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
}
