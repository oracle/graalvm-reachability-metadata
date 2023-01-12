/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_etcd.jetcd_core.impl;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.Watch;
import io.etcd.jetcd.auth.Permission;
import io.etcd.jetcd.test.EtcdClusterExtension;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// `@org.junit.jupiter.api.Timeout(value = 30)` can't be used in the nativeTest GraalVM CE 22.3
@Disabled("https://github.com/etcd-io/jetcd/pull/1092")
public class WatchTokenExpireTest {
    @RegisterExtension
    public static final EtcdClusterExtension cluster = EtcdClusterExtension.builder()
            .withNodes(1)
            .withSsl(true)
            .withAdditionalArgs(Arrays.asList("--auth-token",
                    "jwt,pub-key=/etc/ssl/etcd/server.pem,priv-key=/etc/ssl/etcd/server-key.pem,sign-method=RS256,ttl=1s"))
            .build();
    private static final ByteSequence key = TestUtil.randomByteSequence();
    private static final ByteSequence user = TestUtil.bytesOf("root");
    private static final ByteSequence password = TestUtil.randomByteSequence();

    private void setUpEnvironment() throws Exception {
        final File caFile = new File(Objects.requireNonNull(getClass().getResource("/ssl/cert/ca.pem")).toURI());
        Client client = TestUtil.client(cluster)
                .authority("etcd0")
                .sslContext(b -> b.trustManager(caFile))
                .build();
        ByteSequence role = TestUtil.bytesOf("root");
        client.getAuthClient().roleAdd(role).get();
        client.getAuthClient().userAdd(user, password).get();
        client.getAuthClient().roleGrantPermission(role, key, key, Permission.Type.READWRITE).get();
        client.getAuthClient().userGrantRole(user, role).get();
        client.getAuthClient().authEnable().get();
        client.close();
    }

    private Client createAuthClient() throws Exception {
        final File caFile = new File(Objects.requireNonNull(getClass().getResource("/ssl/cert/ca.pem")).toURI());
        return TestUtil.client(cluster)
                .user(user)
                .password(password)
                .authority("etcd0")
                .sslContext(b -> b.trustManager(caFile)).build();
    }

    @Test
    public void testRefreshExpiredToken() throws Exception {
        setUpEnvironment();
        Client authClient = createAuthClient();
        Watch authWatchClient = authClient.getWatchClient();
        KV authKVClient = authClient.getKVClient();
        authKVClient.put(key, TestUtil.randomByteSequence()).get(1, TimeUnit.SECONDS);
        Thread.sleep(3000);
        AtomicInteger modifications = new AtomicInteger();
        Watch.Watcher watcher = authWatchClient.watch(key, response -> modifications.incrementAndGet());
        ExecutorService executor = Executors.newFixedThreadPool(1);
        List<Future<?>> futures;
        Client anotherClient = createAuthClient();
        futures = IntStream.range(0, 2).mapToObj(i -> executor.submit(() -> {
            try {
                Thread.sleep(3000);
                anotherClient.getKVClient().put(key, TestUtil.randomByteSequence()).get(1, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        })).collect(Collectors.toCollection(() -> new ArrayList<>(2)));
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> assertThat(modifications.get()).isEqualTo(2));
        executor.shutdownNow();
        futures.forEach(f -> assertThat(f).isDone());
        anotherClient.close();
        watcher.close();
        authWatchClient.close();
        authClient.close();
    }
}
