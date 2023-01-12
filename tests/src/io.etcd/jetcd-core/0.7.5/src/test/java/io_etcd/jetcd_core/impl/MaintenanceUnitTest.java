/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_etcd.jetcd_core.impl;

import com.google.protobuf.ByteString;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.Maintenance;
import io.etcd.jetcd.api.MaintenanceGrpc.MaintenanceImplBase;
import io.etcd.jetcd.api.SnapshotRequest;
import io.etcd.jetcd.api.SnapshotResponse;
import io.etcd.jetcd.common.exception.EtcdException;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.util.MutableHandlerRegistry;
import org.apache.commons.io.output.NullOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Fail.fail;

@SuppressWarnings("FieldCanBeLocal")
// `@org.junit.jupiter.api.Timeout(value = 30)` can't be used in the nativeTest GraalVM CE 22.3
public class MaintenanceUnitTest {
    private MutableHandlerRegistry serviceRegistry;
    private BlockingQueue<StreamObserver<SnapshotResponse>> observerQueue;
    private Server fakeServer;
    private ExecutorService executor;
    private Client client;
    private Maintenance maintenance;

    @BeforeEach
    public void setUp() throws IOException, URISyntaxException {
        observerQueue = new LinkedBlockingQueue<>();
        executor = Executors.newFixedThreadPool(2);
        serviceRegistry = new MutableHandlerRegistry();
        serviceRegistry.addService(new MaintenanceImplBase() {
            @Override
            public void snapshot(SnapshotRequest request, StreamObserver<SnapshotResponse> observer) {
                try {
                    observerQueue.put(observer);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        fakeServer = NettyServerBuilder.forPort(TestUtil.findNextAvailablePort()).fallbackHandlerRegistry(serviceRegistry).directExecutor().build().start();
        client = Client.builder().endpoints(new URI("http://127.0.0.1:" + fakeServer.getPort())).build();
        maintenance = client.getMaintenanceClient();
    }

    @AfterEach
    public void tearDown() {
        maintenance.close();
        client.close();
        fakeServer.shutdownNow();
    }

    @Test
    public void testConnectionError() {
        executor.execute(() -> {
            try {
                Thread.sleep(50);
                observerQueue.take().onError(Status.ABORTED.asRuntimeException());
            } catch (InterruptedException e) {
                fail("expect no exception, but got InterruptedException", e);
            }
        });
        assertThatThrownBy(() -> maintenance.snapshot(NullOutputStream.NULL_OUTPUT_STREAM).get())
                .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(EtcdException.class);
    }

    @Test
    public void testWrite() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteString blob = ByteString.copyFromUtf8("blob");
        final CompletableFuture<Long> answer = maintenance.snapshot(out);
        StreamObserver<SnapshotResponse> observer = observerQueue.take();
        observer.onNext(SnapshotResponse.newBuilder().setBlob(blob).setRemainingBytes(0).build());
        observer.onCompleted();
        answer.get();
        assertThat(out.toByteArray()).isEqualTo(blob.toByteArray());
    }
}
