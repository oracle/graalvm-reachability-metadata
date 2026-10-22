/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_zookeeper.zookeeper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/// Exercises ZooKeeper's asynchronous client callbacks for parallel application bootstrap.
/// A payment service loads required settings without blocking the callback thread,
/// refuses to start with a missing credential, and retries after an operator provisions it.
public class AsyncApplicationBootstrapTest {

    @TempDir
    Path storage;

    @Test
    @Timeout(60)
    public void propagatesMissingSettingsAndCombinesSuccessfulAsyncReads() throws Exception {
        Path snapshots = Files.createDirectory(storage.resolve("snapshots"));
        Path transactions = Files.createDirectory(storage.resolve("transactions"));
        ZooKeeperServer server = new ZooKeeperServer(snapshots.toFile(), transactions.toFile(), 2000);
        NIOServerCnxnFactory listener = new NIOServerCnxnFactory();
        try {
            listener.configure(new InetSocketAddress("127.0.0.1", 0), 10);
            listener.startup(server);
            String address = "127.0.0.1:" + listener.getLocalPort();
            CountDownLatch operatorConnected = new CountDownLatch(1);
            CountDownLatch serviceConnected = new CountDownLatch(1);
            try (ZooKeeper operator = new ZooKeeper(address, 10000, event -> {
                if (event.getState() == KeeperState.SyncConnected) {
                    operatorConnected.countDown();
                }
            }); ZooKeeper service = new ZooKeeper(address, 10000, event -> {
                if (event.getState() == KeeperState.SyncConnected) {
                    serviceConnected.countDown();
                }
            })) {
                assertTrue(operatorConnected.await(10, TimeUnit.SECONDS), "Operator connects");
                assertTrue(serviceConnected.await(10, TimeUnit.SECONDS), "Payment service connects");
                operator.create("/bootstrap", new byte[0],
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                operator.create("/bootstrap/endpoint", bytes("https://payments.internal"),
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                operator.create("/bootstrap/concurrency", bytes("24"),
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

                CompletableFuture<Configuration> incomplete = loadConfiguration(service);
                ExecutionException failure = assertThrows(ExecutionException.class,
                        () -> incomplete.get(10, TimeUnit.SECONDS));
                KeeperException.NoNodeException missing = assertInstanceOf(
                        KeeperException.NoNodeException.class, failure.getCause());
                assertEquals("/bootstrap/credential", missing.getPath(),
                        "Bootstrap reports which required setting the operator must supply");

                operator.create("/bootstrap/credential", bytes("deployment-secret"),
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                Configuration ready = loadConfiguration(service).get(10, TimeUnit.SECONDS);
                assertEquals("https://payments.internal", ready.endpoint());
                assertEquals(24, ready.concurrency());
                assertEquals("deployment-secret", ready.credential());
            }
        } finally {
            listener.shutdown();
            server.shutdown();
            server.getTxnLogFactory().close();
        }
    }

    private static CompletableFuture<Configuration> loadConfiguration(ZooKeeper client) {
        // Submit all required reads before composing their results; callbacks never block.
        CompletableFuture<String> endpoint = readSetting(client, "/bootstrap/endpoint");
        CompletableFuture<String> concurrency = readSetting(client, "/bootstrap/concurrency");
        CompletableFuture<String> credential = readSetting(client, "/bootstrap/credential");
        return endpoint.thenCombine(concurrency,
                (url, limit) -> new Configuration(url, Integer.parseInt(limit), ""))
                .thenCombine(credential,
                        (settings, secret) -> new Configuration(settings.endpoint(), settings.concurrency(), secret));
    }

    private static CompletableFuture<String> readSetting(ZooKeeper client, String path) {
        CompletableFuture<String> result = new CompletableFuture<>();
        client.getData(path, false, (status, responsePath, context, data, stat) -> {
            Code code = Code.get(status);
            if (code == Code.OK) {
                result.complete(new String(data, StandardCharsets.UTF_8));
            } else {
                result.completeExceptionally(KeeperException.create(code, responsePath));
            }
        }, null);
        return result;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private record Configuration(String endpoint, int concurrency, String credential) {
    }
}
