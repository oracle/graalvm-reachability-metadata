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
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.Watcher.WatcherType;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/// Exercises ZooKeeper's watch deregistration subsystem for dashboard subscription cleanup.
/// Closing a retired deployment panel cancels only that panel's watch, preserving
/// its active deployment subscription and an independent observer of the retired record.
public class DashboardWatchCancellationTest {

    @TempDir
    Path storage;

    @Test
    @Timeout(60)
    public void cancelsOneSubscriptionWithoutRemovingOtherObservers() throws Exception {
        Path snapshots = Files.createDirectory(storage.resolve("snapshots"));
        Path transactions = Files.createDirectory(storage.resolve("transactions"));
        ZooKeeperServer server = new ZooKeeperServer(snapshots.toFile(), transactions.toFile(), 2000);
        NIOServerCnxnFactory listener = new NIOServerCnxnFactory();
        try {
            listener.configure(new InetSocketAddress("127.0.0.1", 0), 10);
            listener.startup(server);
            String address = "127.0.0.1:" + listener.getLocalPort();
            CountDownLatch publisherConnected = new CountDownLatch(1);
            CountDownLatch dashboardConnected = new CountDownLatch(1);
            try (ZooKeeper publisher = new ZooKeeper(address, 10000, event -> {
                if (event.getState() == KeeperState.SyncConnected) {
                    publisherConnected.countDown();
                }
            }); ZooKeeper dashboard = new ZooKeeper(address, 10000, event -> {
                if (event.getState() == KeeperState.SyncConnected) {
                    dashboardConnected.countDown();
                }
            })) {
                assertTrue(publisherConnected.await(10, TimeUnit.SECONDS), "Publisher connects");
                assertTrue(dashboardConnected.await(10, TimeUnit.SECONDS), "Dashboard connects");
                String retired = "/deployments/retired";
                String active = "/deployments/active";
                publisher.create("/deployments", new byte[0],
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                publisher.create(retired, bytes("state=draining"),
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                publisher.create(active, bytes("state=ready"),
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

                BlockingQueue<WatchedEvent> panelEvents = new LinkedBlockingQueue<>();
                BlockingQueue<WatchedEvent> auditEvents = new LinkedBlockingQueue<>();
                Watcher panels = event -> {
                    if (event.getType() != EventType.None) {
                        panelEvents.offer(event);
                    }
                };
                Watcher audit = event -> {
                    if (event.getType() != EventType.None) {
                        auditEvents.offer(event);
                    }
                };
                assertArrayEquals(bytes("state=draining"), dashboard.getData(retired, panels, null));
                assertArrayEquals(bytes("state=ready"), dashboard.getData(active, panels, null));
                assertArrayEquals(bytes("state=draining"), dashboard.getData(retired, audit, null));

                // Remove this watcher on just the closed panel, including its server-side registration.
                dashboard.removeWatches(retired, panels, WatcherType.Data, false);
                awaitEvent(panelEvents, EventType.DataWatchRemoved, retired);

                publisher.setData(retired, bytes("state=archived"), 0);
                awaitEvent(auditEvents, EventType.NodeDataChanged, retired);
                assertArrayEquals(bytes("state=archived"), dashboard.getData(retired, false, null));

                // This later event is an ordering barrier, not a timed wait for an absent notification.
                publisher.setData(active, bytes("state=serving"), 0);
                awaitEvent(panelEvents, EventType.NodeDataChanged, active);
                assertTrue(panelEvents.isEmpty(), "The closed panel receives no retired-record update");
                assertArrayEquals(bytes("state=serving"), dashboard.getData(active, false, null));
            }
        } finally {
            listener.shutdown();
            server.shutdown();
            server.getTxnLogFactory().close();
        }
    }

    private static void awaitEvent(BlockingQueue<WatchedEvent> events, EventType type, String path)
            throws InterruptedException {
        WatchedEvent event = events.poll(10, TimeUnit.SECONDS);
        assertNotNull(event, "The subscriber receives " + type + " for " + path);
        assertEquals(type, event.getType());
        assertEquals(path, event.getPath());
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
