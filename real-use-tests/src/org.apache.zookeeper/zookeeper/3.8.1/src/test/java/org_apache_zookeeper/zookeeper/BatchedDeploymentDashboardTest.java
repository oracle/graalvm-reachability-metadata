/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_zookeeper.zookeeper;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.Op;
import org.apache.zookeeper.OpResult;
import org.apache.zookeeper.OpResult.ErrorResult;
import org.apache.zookeeper.OpResult.GetChildrenResult;
import org.apache.zookeeper.OpResult.GetDataResult;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/// Exercises ZooKeeper's read-only multi-operation batching for a deployment dashboard.
/// A dashboard reads the deployment catalog and individual releases in one batch,
/// handles an undeployed service independently, and refreshes after a release is published.
public class BatchedDeploymentDashboardTest {

    @TempDir
    Path storage;

    @Test
    @Timeout(60)
    public void keepsAvailableReleaseRowsWhenAnotherServiceHasNotBeenDeployed() throws Exception {
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
                assertTrue(publisherConnected.await(10, TimeUnit.SECONDS), "Release publisher connects");
                assertTrue(dashboardConnected.await(10, TimeUnit.SECONDS), "Dashboard connects");
                publisher.create("/deployments", new byte[0],
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                publisher.create("/deployments/checkout", bytes("image=checkout:blue"),
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                publisher.create("/deployments/worker", bytes("image=worker:stable"),
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

                List<Op> refresh = List.of(
                        Op.getChildren("/deployments"),
                        Op.getData("/deployments/checkout"),
                        Op.getData("/deployments/search"),
                        Op.getData("/deployments/worker"));
                List<OpResult> first = dashboard.multi(refresh);
                assertEquals(4, first.size(), "Each requested dashboard row has its own result");
                GetChildrenResult catalog = assertInstanceOf(GetChildrenResult.class, first.get(0));
                assertEquals(List.of("checkout", "worker"), catalog.getChildren().stream().sorted().toList());
                GetDataResult checkout = assertInstanceOf(GetDataResult.class, first.get(1));
                assertArrayEquals(bytes("image=checkout:blue"), checkout.getData());
                ErrorResult undeployed = assertInstanceOf(ErrorResult.class, first.get(2));
                assertEquals(Code.NONODE.intValue(), undeployed.getErr(),
                        "The dashboard can label search as not deployed without discarding other rows");
                GetDataResult worker = assertInstanceOf(GetDataResult.class, first.get(3));
                assertArrayEquals(bytes("image=worker:stable"), worker.getData(),
                        "A successful read after the missing service is still returned");

                publisher.setData("/deployments/checkout", bytes("image=checkout:green"),
                        checkout.getStat().getVersion());
                publisher.create("/deployments/search", bytes("image=search:initial"),
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

                // The same read-only batch can be reused; it is not an atomic write transaction.
                List<OpResult> second = dashboard.multi(refresh);
                assertEquals(4, second.size());
                GetChildrenResult updatedCatalog = assertInstanceOf(GetChildrenResult.class, second.get(0));
                assertEquals(List.of("checkout", "search", "worker"),
                        updatedCatalog.getChildren().stream().sorted().toList());
                GetDataResult releasedCheckout = assertInstanceOf(GetDataResult.class, second.get(1));
                assertArrayEquals(bytes("image=checkout:green"), releasedCheckout.getData());
                assertEquals(checkout.getStat().getVersion() + 1, releasedCheckout.getStat().getVersion());
                GetDataResult search = assertInstanceOf(GetDataResult.class, second.get(2));
                assertArrayEquals(bytes("image=search:initial"), search.getData());
                GetDataResult unchangedWorker = assertInstanceOf(GetDataResult.class, second.get(3));
                assertArrayEquals(worker.getData(), unchangedWorker.getData());
                assertEquals(worker.getStat().getVersion(), unchangedWorker.getStat().getVersion(),
                        "Refreshing the dashboard never changes a deployment");
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
