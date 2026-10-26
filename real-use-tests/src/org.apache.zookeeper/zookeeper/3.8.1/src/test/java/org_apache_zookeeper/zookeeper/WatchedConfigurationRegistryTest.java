/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_zookeeper.zookeeper;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises ZooKeeper data watches and versioned writes as a shared configuration registry.
 * A checkout service reads and watches its configuration while a publisher updates it;
 * a stale publisher cannot overwrite the newly published configuration.
 */
public class WatchedConfigurationRegistryTest {

    @TempDir
    Path storage;

    @Test
    @Timeout(60)
    public void reloadsPublishedConfigurationAndRejectsStaleWrites() throws Exception {
        Path snapshots = Files.createDirectory(storage.resolve("snapshots"));
        Path transactions = Files.createDirectory(storage.resolve("transactions"));
        ZooKeeperServer server = new ZooKeeperServer(snapshots.toFile(), transactions.toFile(), 2000);
        NIOServerCnxnFactory listener = new NIOServerCnxnFactory();
        try {
            listener.configure(new InetSocketAddress("127.0.0.1", 0), 10);
            listener.startup(server);
            String address = "127.0.0.1:" + listener.getLocalPort();
            CountDownLatch publisherConnected = new CountDownLatch(1);
            CountDownLatch serviceConnected = new CountDownLatch(1);
            try (ZooKeeper publisher = new ZooKeeper(address, 10000, event -> {
                if (event.getState() == KeeperState.SyncConnected) {
                    publisherConnected.countDown();
                }
            }); ZooKeeper service = new ZooKeeper(address, 10000, event -> {
                if (event.getState() == KeeperState.SyncConnected) {
                    serviceConnected.countDown();
                }
            })) {
                assertTrue(publisherConnected.await(10, TimeUnit.SECONDS), "Publisher connects");
                assertTrue(serviceConnected.await(10, TimeUnit.SECONDS), "Checkout service connects");
                publisher.create("/services", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                String configPath = "/services/checkout";
                byte[] original = "paymentEndpoint=https://payments.internal/v1".getBytes(StandardCharsets.UTF_8);
                byte[] replacement = "paymentEndpoint=https://payments.internal/v2".getBytes(StandardCharsets.UTF_8);
                publisher.create(configPath, original, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

                BlockingQueue<WatchedEvent> changes = new LinkedBlockingQueue<>();
                Stat observed = new Stat();
                assertArrayEquals(original, service.getData(configPath, event -> {
                    if (event.getType() == EventType.NodeDataChanged) {
                        changes.offer(event);
                    }
                }, observed));

                Stat published = publisher.setData(configPath, replacement, observed.getVersion());
                WatchedEvent change = changes.poll(10, TimeUnit.SECONDS);
                assertNotNull(change, "The service receives a configuration-change notification");
                assertEquals(configPath, change.getPath());
                assertEquals(EventType.NodeDataChanged, change.getType());
                assertEquals(observed.getVersion() + 1, published.getVersion());

                Stat reloaded = new Stat();
                assertArrayEquals(replacement, service.getData(configPath, false, reloaded));
                assertEquals(published.getVersion(), reloaded.getVersion());
                assertThrows(KeeperException.BadVersionException.class,
                        () -> publisher.setData(configPath, original, observed.getVersion()));
                assertArrayEquals(replacement, service.getData(configPath, false, null));
            }
        } finally {
            listener.shutdown();
            server.shutdown();
        }
    }
}
