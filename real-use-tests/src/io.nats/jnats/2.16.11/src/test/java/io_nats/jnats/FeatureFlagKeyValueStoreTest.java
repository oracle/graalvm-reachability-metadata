/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_nats.jnats;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.KeyValue;
import io.nats.client.KeyValueManagement;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.api.KeyValueConfiguration;
import io.nats.client.api.KeyValueEntry;
import io.nats.client.api.KeyValueOperation;
import io.nats.client.api.KeyValueStatus;
import io.nats.client.api.KeyValueWatchOption;
import io.nats.client.api.KeyValueWatcher;
import io.nats.client.api.StorageType;
import io.nats.client.impl.NatsKeyValueWatchSubscription;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Keeps an application's feature flags in a JetStream Key-Value bucket through the jnats
 * Key-Value API.
 *
 * An admin connection creates the bucket, publishes JSON flag definitions, and changes them
 * with optimistic revision checks, while a service connection watches the bucket and sees
 * each change, then reads the per-key history, the remaining keys, and the bucket status.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FeatureFlagKeyValueStoreTest {

    private static final String NATS_IMAGE = "nats:2.15.1";

    private static final String BUCKET = "feature-flags";

    private final ObjectMapper mapper = new ObjectMapper();

    private final String containerName = "real-use-jnats-kv-" + UUID.randomUUID();

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
    void storesWatchesAndVersionsFeatureFlags() throws Exception {
        try (Connection adminConnection = connect(); Connection serviceConnection = connect()) {
            KeyValueManagement management = adminConnection.keyValueManagement();
            KeyValueStatus created = management.create(KeyValueConfiguration.builder()
                    .name(BUCKET)
                    .description("Checkout feature flags")
                    .maxHistoryPerKey(5)
                    .storageType(StorageType.Memory)
                    .build());
            assertThat(created.getBucketName()).isEqualTo(BUCKET);
            assertThat(created.getMaxHistoryPerKey()).isEqualTo(5);
            assertThat(management.getBucketNames()).contains(BUCKET);

            KeyValue adminFlags = adminConnection.keyValue(BUCKET);
            long darkModeRevision = adminFlags.create("dark-mode", flag(new FeatureFlag(false, 0)));
            adminFlags.put("one-click-checkout", flag(new FeatureFlag(true, 10)));

            KeyValue serviceFlags = serviceConnection.keyValue(BUCKET);
            RecordingWatcher watcher = new RecordingWatcher();
            try (NatsKeyValueWatchSubscription subscription =
                         serviceFlags.watchAll(watcher, KeyValueWatchOption.UPDATES_ONLY)) {
                assertThat(watcher.awaitEndOfData()).isTrue();

                long rolledOutRevision = adminFlags.update("dark-mode",
                        flag(new FeatureFlag(true, 50)), darkModeRevision);
                assertThat(rolledOutRevision).isGreaterThan(darkModeRevision);

                JetStreamApiException conflict = catchThrowableOfType(
                        () -> adminFlags.update("dark-mode", flag(new FeatureFlag(false, 0)), darkModeRevision),
                        JetStreamApiException.class);
                assertThat(conflict.getApiErrorCode()).isEqualTo(10071);

                adminFlags.delete("one-click-checkout");

                KeyValueEntry rolledOut = watcher.next();
                assertThat(rolledOut.getKey()).isEqualTo("dark-mode");
                assertThat(rolledOut.getOperation()).isEqualTo(KeyValueOperation.PUT);
                assertThat(rolledOut.getRevision()).isEqualTo(rolledOutRevision);
                assertThat(mapper.readValue(rolledOut.getValue(), FeatureFlag.class))
                        .isEqualTo(new FeatureFlag(true, 50));

                KeyValueEntry removed = watcher.next();
                assertThat(removed.getKey()).isEqualTo("one-click-checkout");
                assertThat(removed.getOperation()).isEqualTo(KeyValueOperation.DELETE);
            }

            KeyValueEntry current = serviceFlags.get("dark-mode");
            assertThat(mapper.readValue(current.getValue(), FeatureFlag.class))
                    .isEqualTo(new FeatureFlag(true, 50));
            assertThat(serviceFlags.get("dark-mode", darkModeRevision).getValueAsString())
                    .contains("\"enabled\":false");
            assertThat(serviceFlags.get("one-click-checkout")).isNull();

            List<KeyValueEntry> history = serviceFlags.history("dark-mode");
            assertThat(history).extracting(KeyValueEntry::getRevision)
                    .containsExactly(darkModeRevision, current.getRevision());
            assertThat(serviceFlags.keys()).containsExactly("dark-mode");

            KeyValueStatus status = serviceFlags.getStatus();
            assertThat(status.getDescription()).isEqualTo("Checkout feature flags");
            assertThat(status.getStorageType()).isEqualTo(StorageType.Memory);
            assertThat(status.getEntryCount()).isEqualTo(4);

            management.delete(BUCKET);
            assertThat(management.getBucketNames()).doesNotContain(BUCKET);
        }
    }

    private byte[] flag(FeatureFlag flag) throws IOException {
        return mapper.writeValueAsBytes(flag);
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

    /// A feature flag definition stored as the JSON value of one key.
    record FeatureFlag(boolean enabled, int rolloutPercent) {
    }

    /// Collects the entries a bucket watcher delivers so the test can read them in order.
    static final class RecordingWatcher implements KeyValueWatcher {

        private final BlockingQueue<KeyValueEntry> entries = new LinkedBlockingQueue<>();

        private final CountDownLatch endOfData = new CountDownLatch(1);

        @Override
        public void watch(KeyValueEntry entry) {
            entries.add(entry);
        }

        @Override
        public void endOfData() {
            endOfData.countDown();
        }

        boolean awaitEndOfData() throws InterruptedException {
            return endOfData.await(10, TimeUnit.SECONDS);
        }

        KeyValueEntry next() throws InterruptedException {
            KeyValueEntry entry = entries.poll(10, TimeUnit.SECONDS);
            assertThat(entry).as("watched entry").isNotNull();
            return entry;
        }
    }
}
