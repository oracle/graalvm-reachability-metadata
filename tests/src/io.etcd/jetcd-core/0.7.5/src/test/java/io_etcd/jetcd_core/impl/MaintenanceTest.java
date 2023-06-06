/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_etcd.jetcd_core.impl;

import io.etcd.jetcd.Client;
import io.etcd.jetcd.Maintenance;
import io.etcd.jetcd.maintenance.SnapshotResponse;
import io.etcd.jetcd.maintenance.StatusResponse;
import io.etcd.jetcd.test.EtcdClusterExtension;
import io.grpc.stub.StreamObserver;
import org.apache.commons.io.output.NullOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

@SuppressWarnings({"FieldCanBeLocal", "deprecation", "ResultOfMethodCallIgnored"})
// `@org.junit.jupiter.api.Timeout(value = 30)` can't be used in the nativeTest GraalVM CE 22.3
public class MaintenanceTest {
    @RegisterExtension
    public static final EtcdClusterExtension cluster = EtcdClusterExtension.builder()
            .withNodes(3)
            .build();
    private static Client client;
    private static Maintenance maintenance;
    private static List<URI> endpoints;

    @TempDir
    static Path tempDir;

    @BeforeEach
    public void setUp() {
        endpoints = cluster.clientEndpoints();
        client = TestUtil.client(cluster).build();
        maintenance = client.getMaintenanceClient();
    }

    @Test
    public void testStatusMember() throws ExecutionException, InterruptedException {
        StatusResponse statusResponse = maintenance.statusMember(endpoints.get(0)).get();
        assertThat(statusResponse.getDbSize()).isGreaterThan(0);
    }

    @Test
    public void testSnapshotToOutputStream() throws ExecutionException, InterruptedException, IOException {
        final Path snapfile = tempDir.resolve("snap");
        try (OutputStream stream = Files.newOutputStream(snapfile)) {
            Long bytes = maintenance.snapshot(stream).get();
            stream.flush();
            Long fsize = Files.size(snapfile);
            assertThat(bytes).isEqualTo(fsize);
        }
    }

    @Test
    public void testSnapshotChunks() throws ExecutionException, InterruptedException {
        final Long bytes = maintenance.snapshot(NullOutputStream.NULL_OUTPUT_STREAM).get();
        final AtomicLong count = new AtomicLong();
        final CountDownLatch latcht = new CountDownLatch(1);
        maintenance.snapshot(new StreamObserver<>() {
            @Override
            public void onNext(SnapshotResponse value) {
                count.addAndGet(value.getBlob().size());
            }

            @Override
            public void onError(Throwable t) {
                fail("Should not throw exception");
            }

            @Override
            public void onCompleted() {
                latcht.countDown();
            }
        });
        latcht.await(10, TimeUnit.SECONDS);
        assertThat(bytes).isEqualTo(count.get());
    }

    @Test
    public void testHashKV() throws ExecutionException, InterruptedException {
        maintenance.hashKV(endpoints.get(0), 0).get();
    }

    @Test
    public void testAlarmList() throws ExecutionException, InterruptedException {
        maintenance.listAlarms().get();
    }

    @Test
    public void testDefragment() throws ExecutionException, InterruptedException {
        maintenance.defragmentMember(endpoints.get(0)).get();
    }
}
