/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_zookeeper.zookeeper;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/// Exercises ZooKeeper's session-scoped ephemeral inventory for worker drain verification.
/// A draining worker releases its own job leases without removing another worker's
/// lease, persistent job configuration, or its own membership registration.
public class WorkerLeaseInventoryTest {

    @TempDir
    Path storage;

    @Test
    @Timeout(60)
    public void drainsOnlyTheCurrentWorkersJobLeases() throws Exception {
        Path snapshots = Files.createDirectory(storage.resolve("snapshots"));
        Path transactions = Files.createDirectory(storage.resolve("transactions"));
        ZooKeeperServer server = new ZooKeeperServer(snapshots.toFile(), transactions.toFile(), 2000);
        NIOServerCnxnFactory listener = new NIOServerCnxnFactory();
        try {
            listener.configure(new InetSocketAddress("127.0.0.1", 0), 10);
            listener.startup(server);
            String address = "127.0.0.1:" + listener.getLocalPort();
            CountDownLatch drainingConnected = new CountDownLatch(1);
            CountDownLatch activeConnected = new CountDownLatch(1);
            try (ZooKeeper draining = new ZooKeeper(address, 10000, event -> {
                if (event.getState() == KeeperState.SyncConnected) {
                    drainingConnected.countDown();
                }
            }); ZooKeeper active = new ZooKeeper(address, 10000, event -> {
                if (event.getState() == KeeperState.SyncConnected) {
                    activeConnected.countDown();
                }
            })) {
                assertTrue(drainingConnected.await(10, TimeUnit.SECONDS), "Draining worker connects");
                assertTrue(activeConnected.await(10, TimeUnit.SECONDS), "Active worker connects");
                draining.create("/jobs", new byte[0],
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                draining.create("/workers", new byte[0],
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                byte[] configuration = bytes("queue=settlements;concurrency=3");
                draining.create("/jobs/config", configuration,
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                draining.create("/workers/draining", bytes("state=draining"),
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
                draining.create("/jobs/settlement-101", bytes("worker=draining"),
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
                draining.create("/jobs/settlement-102", bytes("worker=draining"),
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
                byte[] activeLease = bytes("worker=active");
                active.create("/jobs/settlement-103", activeLease,
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);

                assertEquals(List.of("/jobs/settlement-101", "/jobs/settlement-102", "/workers/draining"),
                        draining.getEphemerals().stream().sorted().toList(),
                        "The inventory contains only ephemerals owned by this worker's session");
                List<String> outstandingJobs = draining.getEphemerals("/jobs").stream().sorted().toList();
                assertEquals(List.of("/jobs/settlement-101", "/jobs/settlement-102"), outstandingJobs,
                        "A prefix-scoped drain excludes membership and persistent configuration");

                // Release only leases returned by this session's inventory, not all children of /jobs.
                for (String lease : outstandingJobs) {
                    draining.delete(lease, -1);
                }

                assertEquals(List.of(), draining.getEphemerals("/jobs"), "The worker has no pending jobs");
                assertEquals(List.of("/workers/draining"), draining.getEphemerals(),
                        "The worker remains registered until its drain has been acknowledged");
                assertEquals(List.of("/jobs/settlement-103"), active.getEphemerals("/jobs"));
                assertArrayEquals(activeLease, active.getData("/jobs/settlement-103", false, null));
                assertArrayEquals(configuration, active.getData("/jobs/config", false, null));
                assertEquals(List.of("config", "settlement-103"),
                        active.getChildren("/jobs", false).stream().sorted().toList());
                assertNull(active.exists("/jobs/settlement-101", false));
                assertNull(active.exists("/jobs/settlement-102", false));

                draining.delete("/workers/draining", -1);
                assertEquals(List.of(), draining.getEphemerals(), "The drained worker can now stop");
                assertArrayEquals(activeLease, active.getData("/jobs/settlement-103", false, null));
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
