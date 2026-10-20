/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_nats.jnats;

import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.Nats;
import io.nats.client.ObjectStore;
import io.nats.client.ObjectStoreManagement;
import io.nats.client.Options;
import io.nats.client.api.ObjectInfo;
import io.nats.client.api.ObjectMeta;
import io.nats.client.api.ObjectStoreConfiguration;
import io.nats.client.api.ObjectStoreStatus;
import io.nats.client.api.ObjectStoreWatchOption;
import io.nats.client.api.ObjectStoreWatcher;
import io.nats.client.api.StorageType;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsObjectStoreWatchSubscription;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Distributes device firmware images through a JetStream Object Store bucket with the jnats
 * Object Store API.
 *
 * A release connection uploads a multi-chunk firmware image with headers, publishes a
 * "latest" link to it, and renames it, while a device connection watches the bucket, downloads
 * the image through the link, and verifies its digest; the release is then retired, the bucket
 * sealed against further uploads, and finally removed.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FirmwareObjectStoreTest {

    private static final String NATS_IMAGE = "nats:2.15.0";

    private static final String BUCKET = "firmware";

    private static final int CHUNK_SIZE = 64 * 1024;

    private static final int IMAGE_SIZE = 300_000;

    private final String containerName = "real-use-jnats-os-" + UUID.randomUUID();

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
    void publishesDownloadsAndRetiresFirmwareImages() throws Exception {
        byte[] image = new byte[IMAGE_SIZE];
        new Random(42).nextBytes(image);

        try (Connection releaseConnection = connect(); Connection deviceConnection = connect()) {
            ObjectStoreManagement management = releaseConnection.objectStoreManagement();
            ObjectStoreStatus created = management.create(ObjectStoreConfiguration.builder(BUCKET)
                    .description("Thermostat firmware releases")
                    .storageType(StorageType.Memory)
                    .build());
            assertThat(created.getBucketName()).isEqualTo(BUCKET);
            assertThat(created.isSealed()).isFalse();
            assertThat(management.getBucketNames()).contains(BUCKET);

            ObjectStore releases = releaseConnection.objectStore(BUCKET);
            ObjectStore deviceView = deviceConnection.objectStore(BUCKET);
            RecordingWatcher watcher = new RecordingWatcher();
            try (NatsObjectStoreWatchSubscription subscription =
                         deviceView.watch(watcher, ObjectStoreWatchOption.UPDATES_ONLY)) {
                assertThat(watcher.awaitEndOfData()).isTrue();

                Headers headers = new Headers()
                        .put("Content-Type", "application/octet-stream")
                        .put("X-Hardware-Revision", "rev-c");
                ObjectInfo uploaded = releases.put(ObjectMeta.builder("thermostat-1.4.0.bin")
                        .description("Thermostat firmware 1.4.0")
                        .headers(headers)
                        .chunkSize(CHUNK_SIZE)
                        .build(), new ByteArrayInputStream(image));
                assertThat(uploaded.getSize()).isEqualTo(IMAGE_SIZE);
                assertThat(uploaded.getChunks()).isEqualTo(5);
                assertThat(uploaded.getDigest()).isEqualTo(sha256Digest(image));
                assertThat(uploaded.isLink()).isFalse();

                ObjectInfo latest = releases.addLink("thermostat-latest.bin", uploaded);
                assertThat(latest.isLink()).isTrue();
                assertThat(latest.getLink().getObjectName()).isEqualTo("thermostat-1.4.0.bin");

                ObjectInfo announced = watcher.next();
                assertThat(announced.getObjectName()).isEqualTo("thermostat-1.4.0.bin");
                assertThat(announced.getHeaders().getFirst("X-Hardware-Revision")).isEqualTo("rev-c");
                assertThat(watcher.next().getObjectName()).isEqualTo("thermostat-latest.bin");
            }

            ByteArrayOutputStream download = new ByteArrayOutputStream();
            ObjectInfo downloaded = deviceView.get("thermostat-latest.bin", download);
            assertThat(downloaded.getObjectName()).isEqualTo("thermostat-1.4.0.bin");
            assertThat(download.toByteArray()).isEqualTo(image);

            ObjectInfo renamed = releases.updateMeta("thermostat-1.4.0.bin",
                    ObjectMeta.builder("thermostat-1.4.0-stable.bin")
                            .description("Thermostat firmware 1.4.0, promoted to stable")
                            .build());
            assertThat(renamed.getObjectName()).isEqualTo("thermostat-1.4.0-stable.bin");
            assertThat(renamed.getDigest()).isEqualTo(sha256Digest(image));
            assertThat(deviceView.getInfo("thermostat-1.4.0.bin")).isNull();
            assertThat(deviceView.getInfo("thermostat-1.4.0-stable.bin").getDescription())
                    .isEqualTo("Thermostat firmware 1.4.0, promoted to stable");

            List<ObjectInfo> listing = deviceView.getList();
            assertThat(listing).extracting(ObjectInfo::getObjectName)
                    .containsExactlyInAnyOrder("thermostat-1.4.0-stable.bin", "thermostat-latest.bin");

            ObjectInfo retired = releases.delete("thermostat-1.4.0-stable.bin");
            assertThat(retired.isDeleted()).isTrue();
            assertThat(deviceView.getInfo("thermostat-1.4.0-stable.bin")).isNull();
            assertThat(deviceView.getInfo("thermostat-1.4.0-stable.bin", true).isDeleted()).isTrue();

            ObjectStoreStatus sealed = releases.seal();
            assertThat(sealed.isSealed()).isTrue();
            assertThat(sealed.getDescription()).isEqualTo("Thermostat firmware releases");
            assertThat(sealed.getStorageType()).isEqualTo(StorageType.Memory);
            JetStreamApiException rejected = catchThrowableOfType(
                    () -> releases.put("thermostat-1.5.0.bin", new byte[] {1, 2, 3}),
                    JetStreamApiException.class);
            assertThat(rejected).isNotNull();

            management.delete(BUCKET);
            assertThat(management.getBucketNames()).doesNotContain(BUCKET);
        }
    }

    private static String sha256Digest(byte[] data) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
        return "SHA-256=" + Base64.getUrlEncoder().encodeToString(hash);
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

    /// Collects the object announcements a bucket watcher delivers so the test can read them in order.
    static final class RecordingWatcher implements ObjectStoreWatcher {

        private final BlockingQueue<ObjectInfo> objects = new LinkedBlockingQueue<>();

        private final CountDownLatch endOfData = new CountDownLatch(1);

        @Override
        public void watch(ObjectInfo object) {
            objects.add(object);
        }

        @Override
        public void endOfData() {
            endOfData.countDown();
        }

        boolean awaitEndOfData() throws InterruptedException {
            return endOfData.await(10, TimeUnit.SECONDS);
        }

        ObjectInfo next() throws InterruptedException {
            ObjectInfo object = objects.poll(10, TimeUnit.SECONDS);
            assertThat(object).as("watched object").isNotNull();
            return object;
        }
    }
}
