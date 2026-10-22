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
import org.apache.zookeeper.CreateMode;
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

/// Exercises ZooKeeper client chroot namespaces for tenant-specific application configuration.
/// Two deployments use the same logical configuration path in different tenant roots;
/// publishing and deleting one tenant's configuration leaves the other's data intact.
public class ChrootTenantConfigurationTest {

    @TempDir
    Path storage;

    @Test
    @Timeout(60)
    public void scopesApplicationPathsAndWatchNotificationsToEachTenant() throws Exception {
        Path snapshots = Files.createDirectory(storage.resolve("snapshots"));
        Path transactions = Files.createDirectory(storage.resolve("transactions"));
        ZooKeeperServer server = new ZooKeeperServer(snapshots.toFile(), transactions.toFile(), 2000);
        NIOServerCnxnFactory listener = new NIOServerCnxnFactory();
        try {
            listener.configure(new InetSocketAddress("127.0.0.1", 0), 10);
            listener.startup(server);
            String address = "127.0.0.1:" + listener.getLocalPort();
            CountDownLatch administratorConnected = new CountDownLatch(1);
            try (ZooKeeper administrator = new ZooKeeper(address, 10000, event -> {
                if (event.getState() == KeeperState.SyncConnected) {
                    administratorConnected.countDown();
                }
            })) {
                assertTrue(administratorConnected.await(10, TimeUnit.SECONDS), "Administrator connects");
                administrator.create("/tenants", new byte[0],
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                administrator.create("/tenants/blue", new byte[0],
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                administrator.create("/tenants/green", new byte[0],
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

                CountDownLatch blueConnected = new CountDownLatch(1);
                CountDownLatch greenConnected = new CountDownLatch(1);
                try (ZooKeeper blue = new ZooKeeper(address + "/tenants/blue", 10000, event -> {
                    if (event.getState() == KeeperState.SyncConnected) {
                        blueConnected.countDown();
                    }
                }); ZooKeeper green = new ZooKeeper(address + "/tenants/green", 10000, event -> {
                    if (event.getState() == KeeperState.SyncConnected) {
                        greenConnected.countDown();
                    }
                })) {
                    assertTrue(blueConnected.await(10, TimeUnit.SECONDS), "Blue deployment connects");
                    assertTrue(greenConnected.await(10, TimeUnit.SECONDS), "Green deployment connects");
                    byte[] blueConfig = bytes("catalog=blue;checkout=enabled");
                    byte[] greenConfig = bytes("catalog=green;checkout=disabled");
                    assertEquals("/config", blue.create("/config", blueConfig,
                            ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT));
                    assertEquals("/config", green.create("/config", greenConfig,
                            ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT));
                    assertEquals(List.of("config"), blue.getChildren("/", false));
                    assertEquals(List.of("config"), green.getChildren("/", false));
                    assertNull(administrator.exists("/config", false),
                            "Application paths are stored beneath the configured tenant root");
                    assertArrayEquals(blueConfig, administrator.getData("/tenants/blue/config", false, null));
                    assertArrayEquals(greenConfig, administrator.getData("/tenants/green/config", false, null));

                    BlockingQueue<WatchedEvent> updates = new LinkedBlockingQueue<>();
                    Stat before = new Stat();
                    assertArrayEquals(blueConfig, blue.getData("/config", event -> {
                        if (event.getType() == EventType.NodeDataChanged) {
                            updates.offer(event);
                        }
                    }, before));
                    byte[] published = bytes("catalog=blue;checkout=maintenance");
                    administrator.setData("/tenants/blue/config", published, before.getVersion());
                    WatchedEvent update = updates.poll(10, TimeUnit.SECONDS);
                    assertNotNull(update, "The blue deployment receives its configuration update");
                    assertEquals("/config", update.getPath(),
                            "Watch notifications use the application's logical path");
                    assertArrayEquals(published, blue.getData("/config", false, null));
                    assertArrayEquals(greenConfig, green.getData("/config", false, null));

                    blue.delete("/config", -1);
                    assertEquals(List.of(), blue.getChildren("/", false));
                    assertNull(administrator.exists("/tenants/blue/config", false));
                    assertEquals(List.of("config"), green.getChildren("/", false));
                    assertArrayEquals(greenConfig, green.getData("/config", false, null));
                }
            }
        } finally {
            listener.shutdown();
            server.shutdown();
            server.getTxnLogFactory().close();
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
