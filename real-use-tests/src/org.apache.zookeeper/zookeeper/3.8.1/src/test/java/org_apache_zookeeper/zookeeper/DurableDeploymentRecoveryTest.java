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

import java.io.IOException;
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
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/// Exercises ZooKeeper snapshot and transaction-log recovery for durable deployment records.
/// A deployment controller checkpoints its registry, makes further changes, then
/// reads the committed state and continues versioned updates after a server restart.
public class DurableDeploymentRecoveryTest {

    @TempDir
    Path storage;

    @Test
    @Timeout(60)
    public void restoresCheckpointAndReplaysLaterDeploymentChanges() throws Exception {
        Path snapshots = Files.createDirectory(storage.resolve("snapshots"));
        Path transactions = Files.createDirectory(storage.resolve("transactions"));
        Stat committed;
        try (LocalServer original = new LocalServer(snapshots, transactions)) {
            CountDownLatch connected = new CountDownLatch(1);
            try (ZooKeeper controller = new ZooKeeper(original.address(), 10000, event -> {
                if (event.getState() == KeeperState.SyncConnected) {
                    connected.countDown();
                }
            })) {
                assertTrue(connected.await(10, TimeUnit.SECONDS), "Deployment controller connects");
                controller.create("/deployments", new byte[0],
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                controller.create("/deployments/api", bytes("image=api:blue;replicas=2"),
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                controller.create("/deployments/retired", bytes("image=legacy:stable"),
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                original.server.takeSnapshot();

                // These changes must be recovered from transactions newer than the checkpoint.
                committed = controller.setData("/deployments/api", bytes("image=api:green;replicas=3"), 0);
                controller.delete("/deployments/retired", 0);
                controller.create("/deployments/worker", bytes("image=worker:stable;replicas=1"),
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                assertArrayEquals(bytes("image=api:green;replicas=3"),
                        controller.getData("/deployments/api", false, null));
            }
        }

        // A new server instance uses only the previous instance's on-disk storage.
        try (LocalServer restored = new LocalServer(snapshots, transactions)) {
            CountDownLatch connected = new CountDownLatch(1);
            try (ZooKeeper controller = new ZooKeeper(restored.address(), 10000, event -> {
                if (event.getState() == KeeperState.SyncConnected) {
                    connected.countDown();
                }
            })) {
                assertTrue(connected.await(10, TimeUnit.SECONDS), "Controller connects after restart");
                assertEquals(List.of("api", "worker"),
                        controller.getChildren("/deployments", false).stream().sorted().toList());
                assertNull(controller.exists("/deployments/retired", false),
                        "A deleted deployment must not reappear from the checkpoint");
                assertArrayEquals(bytes("image=worker:stable;replicas=1"),
                        controller.getData("/deployments/worker", false, null));

                Stat recovered = new Stat();
                assertArrayEquals(bytes("image=api:green;replicas=3"),
                        controller.getData("/deployments/api", false, recovered));
                assertEquals(committed.getVersion(), recovered.getVersion());
                assertEquals(committed.getMzxid(), recovered.getMzxid());
                Stat scaled = controller.setData("/deployments/api",
                        bytes("image=api:green;replicas=4"), recovered.getVersion());
                assertEquals(recovered.getVersion() + 1, scaled.getVersion());
                assertArrayEquals(bytes("image=api:green;replicas=4"),
                        controller.getData("/deployments/api", false, null));
            }
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static final class LocalServer implements AutoCloseable {
        private final ZooKeeperServer server;
        private final NIOServerCnxnFactory listener = new NIOServerCnxnFactory();

        private LocalServer(Path snapshots, Path transactions) throws IOException, InterruptedException {
            server = new ZooKeeperServer(snapshots.toFile(), transactions.toFile(), 2000);
            try {
                listener.configure(new InetSocketAddress("127.0.0.1", 0), 10);
                listener.startup(server);
            } catch (IOException | InterruptedException failure) {
                close();
                throw failure;
            }
        }

        private String address() {
            return "127.0.0.1:" + listener.getLocalPort();
        }

        @Override
        public void close() throws IOException {
            listener.shutdown();
            server.shutdown();
            server.getTxnLogFactory().close();
        }
    }
}
