/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_zookeeper.zookeeper;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Op;
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
 * Exercises ZooKeeper atomic multi-operation transactions for inventory reservations.
 * A checkout atomically decrements stock and records an order; a duplicate order
 * must roll back its stock change before another order can reserve the remaining item.
 */
public class AtomicInventoryReservationTest {

    @TempDir
    Path storage;

    @Test
    @Timeout(60)
    public void commitsReservationsWithoutLosingStockOnDuplicateOrders() throws Exception {
        Path snapshots = Files.createDirectory(storage.resolve("snapshots"));
        Path transactions = Files.createDirectory(storage.resolve("transactions"));
        ZooKeeperServer server = new ZooKeeperServer(snapshots.toFile(), transactions.toFile(), 2000);
        NIOServerCnxnFactory listener = new NIOServerCnxnFactory();
        try {
            listener.configure(new InetSocketAddress("127.0.0.1", 0), 10);
            listener.startup(server);
            CountDownLatch connected = new CountDownLatch(1);
            try (ZooKeeper checkout = new ZooKeeper("127.0.0.1:" + listener.getLocalPort(), 10000, event -> {
                if (event.getState() == KeeperState.SyncConnected) {
                    connected.countDown();
                }
            })) {
                assertTrue(connected.await(10, TimeUnit.SECONDS), "Checkout connects");
                checkout.create("/stock", bytes("2"), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                checkout.create("/orders", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

                Stat initialStock = new Stat();
                assertArrayEquals(bytes("2"), checkout.getData("/stock", false, initialStock));
                checkout.multi(List.of(
                        Op.setData("/stock", bytes("1"), initialStock.getVersion()),
                        Op.create("/orders/order-101", bytes("customer=alice;quantity=1"),
                                ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT)));

                Stat reservedStock = new Stat();
                assertArrayEquals(bytes("1"), checkout.getData("/stock", false, reservedStock));
                assertArrayEquals(bytes("customer=alice;quantity=1"),
                        checkout.getData("/orders/order-101", false, null));
                assertEquals(initialStock.getVersion() + 1, reservedStock.getVersion());

                assertThrows(KeeperException.NodeExistsException.class, () -> checkout.multi(List.of(
                        Op.setData("/stock", bytes("0"), reservedStock.getVersion()),
                        Op.create("/orders/order-101", bytes("customer=bob;quantity=1"),
                                ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT))));

                Stat afterDuplicate = new Stat();
                assertArrayEquals(bytes("1"), checkout.getData("/stock", false, afterDuplicate));
                assertEquals(reservedStock.getVersion(), afterDuplicate.getVersion(),
                        "A rejected order must not consume stock or advance its version");
                assertArrayEquals(bytes("customer=alice;quantity=1"),
                        checkout.getData("/orders/order-101", false, null));
                assertEquals(List.of("order-101"), checkout.getChildren("/orders", false));

                checkout.multi(List.of(
                        Op.setData("/stock", bytes("0"), afterDuplicate.getVersion()),
                        Op.create("/orders/order-102", bytes("customer=bob;quantity=1"),
                                ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT)));

                assertArrayEquals(bytes("0"), checkout.getData("/stock", false, null));
                assertArrayEquals(bytes("customer=bob;quantity=1"),
                        checkout.getData("/orders/order-102", false, null));
                assertEquals(List.of("order-101", "order-102"),
                        checkout.getChildren("/orders", false).stream().sorted().toList());
            }
        } finally {
            listener.shutdown();
            server.shutdown();
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
