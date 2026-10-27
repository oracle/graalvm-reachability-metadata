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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.zookeeper.AddWatchMode;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/// Exercises ZooKeeper persistent-recursive watches for a live service-discovery catalog.
/// A gateway watches one root before a service exists, then discovers its endpoint,
/// reloads an endpoint change, and removes the endpoint when the provider disconnects.
public class RecursiveServiceDiscoveryTest {

    @TempDir
    Path storage;

    @Test
    @Timeout(60)
    public void tracksNewServicesAndEndpointChangesWithoutRearmingTheWatch() throws Exception {
        Path snapshots = Files.createDirectory(storage.resolve("snapshots"));
        Path transactions = Files.createDirectory(storage.resolve("transactions"));
        ZooKeeperServer server = new ZooKeeperServer(snapshots.toFile(), transactions.toFile(), 2000);
        NIOServerCnxnFactory listener = new NIOServerCnxnFactory();
        try {
            listener.configure(new InetSocketAddress("127.0.0.1", 0), 10);
            listener.startup(server);
            String address = "127.0.0.1:" + listener.getLocalPort();
            CountDownLatch gatewayConnected = new CountDownLatch(1);
            try (ZooKeeper gateway = new ZooKeeper(address, 10000, event -> {
                if (event.getState() == KeeperState.SyncConnected) {
                    gatewayConnected.countDown();
                }
            })) {
                assertTrue(gatewayConnected.await(10, TimeUnit.SECONDS), "Gateway connects");
                gateway.create("/discovery", new byte[0],
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                BlockingQueue<WatchedEvent> changes = new LinkedBlockingQueue<>();
                gateway.addWatch("/discovery", event -> {
                    if (event.getType() != EventType.None) {
                        changes.offer(event);
                    }
                }, AddWatchMode.PERSISTENT_RECURSIVE);
                assertEquals(List.of(), gateway.getChildren("/discovery", false));

                String servicePath = "/discovery/payments";
                String endpointPath = servicePath + "/east-1";
                CountDownLatch providerConnected = new CountDownLatch(1);
                try (ZooKeeper provider = new ZooKeeper(address, 10000, event -> {
                    if (event.getState() == KeeperState.SyncConnected) {
                        providerConnected.countDown();
                    }
                })) {
                    assertTrue(providerConnected.await(10, TimeUnit.SECONDS), "Payment provider connects");
                    provider.create(servicePath, new byte[0],
                            ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                    awaitChange(changes, EventType.NodeCreated, servicePath);
                    assertEquals(List.of("payments"), gateway.getChildren("/discovery", false));

                    byte[] initial = bytes("https://payments-east.internal:8443");
                    provider.create(endpointPath, initial,
                            ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
                    awaitChange(changes, EventType.NodeCreated, endpointPath);
                    assertEquals(List.of("east-1"), gateway.getChildren(servicePath, false));
                    assertArrayEquals(initial, gateway.getData(endpointPath, false, null));

                    // The original recursive registration also reports changes two levels below it.
                    byte[] replacement = bytes("https://payments-east.internal:9443");
                    provider.setData(endpointPath, replacement, 0);
                    awaitChange(changes, EventType.NodeDataChanged, endpointPath);
                    assertArrayEquals(replacement, gateway.getData(endpointPath, false, null));
                }

                // Graceful session close removes the endpoint without waiting for session expiry.
                awaitChange(changes, EventType.NodeDeleted, endpointPath);
                assertNull(gateway.exists(endpointPath, false));
                assertEquals(List.of(), gateway.getChildren(servicePath, false),
                        "The gateway no longer routes requests to the stopped provider");
                assertEquals(List.of("payments"), gateway.getChildren("/discovery", false),
                        "The service namespace remains available for a replacement provider");
            }
        } finally {
            listener.shutdown();
            server.shutdown();
            server.getTxnLogFactory().close();
        }
    }

    private static void awaitChange(BlockingQueue<WatchedEvent> changes, EventType type, String path)
            throws InterruptedException {
        WatchedEvent change = changes.poll(10, TimeUnit.SECONDS);
        assertNotNull(change, "The gateway receives " + type + " for " + path);
        assertEquals(type, change.getType());
        assertEquals(path, change.getPath());
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
