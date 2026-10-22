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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
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
 * Exercises ZooKeeper digest authentication and ACL enforcement for application secrets.
 * A deployment publisher and a payment worker share credentials, while an unrelated
 * client cannot read or overwrite their secret, even using the same username.
 */
public class DigestProtectedSecretTest {

    @TempDir
    Path storage;

    @Test
    @Timeout(60)
    public void sharesSecretOnlyWithClientsHavingTheCorrectCredentials() throws Exception {
        Path snapshots = Files.createDirectory(storage.resolve("snapshots"));
        Path transactions = Files.createDirectory(storage.resolve("transactions"));
        ZooKeeperServer server = new ZooKeeperServer(snapshots.toFile(), transactions.toFile(), 2000);
        NIOServerCnxnFactory listener = new NIOServerCnxnFactory();
        try {
            listener.configure(new InetSocketAddress("127.0.0.1", 0), 10);
            listener.startup(server);
            String address = "127.0.0.1:" + listener.getLocalPort();
            CountDownLatch publisherConnected = new CountDownLatch(1);
            CountDownLatch workerConnected = new CountDownLatch(1);
            CountDownLatch outsiderConnected = new CountDownLatch(1);
            try (ZooKeeper publisher = new ZooKeeper(address, 10000, event -> {
                if (event.getState() == KeeperState.SyncConnected) {
                    publisherConnected.countDown();
                }
            }); ZooKeeper worker = new ZooKeeper(address, 10000, event -> {
                if (event.getState() == KeeperState.SyncConnected) {
                    workerConnected.countDown();
                }
            }); ZooKeeper outsider = new ZooKeeper(address, 10000, event -> {
                if (event.getState() == KeeperState.SyncConnected) {
                    outsiderConnected.countDown();
                }
            })) {
                assertTrue(publisherConnected.await(10, TimeUnit.SECONDS), "Publisher connects");
                assertTrue(workerConnected.await(10, TimeUnit.SECONDS), "Payment worker connects");
                assertTrue(outsiderConnected.await(10, TimeUnit.SECONDS), "Unrelated client connects");

                byte[] credentials = "payments:shared-deployment-password".getBytes(StandardCharsets.UTF_8);
                publisher.addAuthInfo("digest", credentials);
                publisher.create("/payments", new byte[0], ZooDefs.Ids.CREATOR_ALL_ACL, CreateMode.PERSISTENT);
                String secretPath = "/payments/api-key";
                byte[] original = "api-key=initial-key".getBytes(StandardCharsets.UTF_8);
                byte[] rotated = "api-key=rotated-key".getBytes(StandardCharsets.UTF_8);
                publisher.create(secretPath, original, ZooDefs.Ids.CREATOR_ALL_ACL, CreateMode.PERSISTENT);

                assertThrows(KeeperException.NoAuthException.class,
                        () -> outsider.getData(secretPath, false, null));
                assertThrows(KeeperException.NoAuthException.class,
                        () -> outsider.setData(secretPath, rotated, -1));

                outsider.addAuthInfo("digest", "payments:wrong-password".getBytes(StandardCharsets.UTF_8));
                assertThrows(KeeperException.NoAuthException.class,
                        () -> outsider.getData(secretPath, false, null));
                assertThrows(KeeperException.NoAuthException.class,
                        () -> outsider.setData(secretPath, rotated, -1));
                assertThrows(KeeperException.NoAuthException.class,
                        () -> outsider.delete(secretPath, -1));

                worker.addAuthInfo("digest", credentials);
                Stat initial = new Stat();
                assertArrayEquals(original, worker.getData(secretPath, false, initial));
                Stat updated = publisher.setData(secretPath, rotated, initial.getVersion());
                Stat received = new Stat();
                assertArrayEquals(rotated, worker.getData(secretPath, false, received));
                assertEquals(initial.getVersion() + 1, updated.getVersion());
                assertEquals(updated.getVersion(), received.getVersion());

                publisher.delete(secretPath, updated.getVersion());
                assertThrows(KeeperException.NoNodeException.class,
                        () -> worker.getData(secretPath, false, null));
                publisher.delete("/payments", -1);
            }
        } finally {
            listener.shutdown();
            server.shutdown();
        }
    }
}
