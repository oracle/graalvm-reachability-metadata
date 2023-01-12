/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_etcd.jetcd_core.impl;

import io.etcd.jetcd.Auth;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.ClientBuilder;
import io.etcd.jetcd.auth.AuthDisableResponse;
import io.etcd.jetcd.kv.PutResponse;
import io.etcd.jetcd.test.EtcdClusterExtension;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io_etcd.jetcd_core.impl.TestUtil.bytesOf;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings({"resource", "ResultOfMethodCallIgnored"})
// `@org.junit.jupiter.api.Timeout(value = 30)` can't be used in the nativeTest GraalVM CE 22.3
public class ClientConnectionManagerTest {
    private final String ROOT_STRING = "root";
    private final ByteSequence ROOT = bytesOf(ROOT_STRING);
    private final ByteSequence ROOT_PASS = bytesOf("123");
    @RegisterExtension
    public static final EtcdClusterExtension cluster = EtcdClusterExtension.builder()
            .withNodes(1)
            .build();

    @Test
    public void testEndpoints() throws InterruptedException, ExecutionException, TimeoutException {
        try (Client client = Client.builder().endpoints(cluster.clientEndpoints()).build()) {
            client.getKVClient().put(bytesOf("sample_key"), bytesOf("sample_key")).get(15, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testHeaders() throws InterruptedException, ExecutionException {
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientBuilder builder = TestUtil.client(cluster)
                .header("MyHeader1", "MyHeaderVal1")
                .header("MyHeader2", "MyHeaderVal2")
                .interceptor(new ClientInterceptor() {
                    @Override
                    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                                               CallOptions callOptions, Channel next) {
                        return new ForwardingClientCall.SimpleForwardingClientCall<>(
                                next.newCall(method, callOptions)) {
                            @Override
                            public void start(Listener<RespT> responseListener, Metadata headers) {
                                super.start(responseListener, headers);
                                assertThat(headers.get(Metadata.Key.of("MyHeader1", Metadata.ASCII_STRING_MARSHALLER))).isEqualTo("MyHeaderVal1");
                                assertThat(headers.get(Metadata.Key.of("MyHeader2", Metadata.ASCII_STRING_MARSHALLER))).isEqualTo("MyHeaderVal2");
                                latch.countDown();
                            }
                        };
                    }
                });
        try (Client client = builder.build()) {
            CompletableFuture<PutResponse> future = client.getKVClient().put(bytesOf("sample_key"), bytesOf("sample_key"));
            latch.await(1, TimeUnit.MINUTES);
            future.get();
        }
    }

    @Test
    public void testAuthHeaders() throws InterruptedException, ExecutionException {
        final CountDownLatch latch = new CountDownLatch(1);
        Auth authClient = TestUtil.client(cluster).build().getAuthClient();
        authClient.userAdd(ROOT, ROOT_PASS).get();
        ByteSequence role = bytesOf("root");
        authClient.userGrantRole(ROOT, role).get();
        authClient.authEnable().get();
        final ClientBuilder builder = TestUtil.client(cluster)
                .authHeader("MyAuthHeader", "MyAuthHeaderVal").header("MyHeader2", "MyHeaderVal2")
                .user(ROOT).password(ROOT_PASS);
        assertThat(builder.authHeaders().get(Metadata.Key.of("MyAuthHeader", Metadata.ASCII_STRING_MARSHALLER)))
                .isEqualTo("MyAuthHeaderVal");
        try (Client client = builder.build()) {
            CompletableFuture<AuthDisableResponse> future = client.getAuthClient().authDisable();
            latch.await(10, TimeUnit.SECONDS);
            future.get();
        }
        authClient.userRevokeRole(ROOT, role).get();
        authClient.userDelete(ROOT).get();
    }
}
