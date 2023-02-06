/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import org.apache.curator.CuratorZookeeperClient;
import org.apache.curator.RetryLoop;
import org.apache.curator.ensemble.fixed.FixedEnsembleProvider;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.BaseClassForTests;
import org.apache.curator.test.Timing;
import org.apache.curator.utils.ZookeeperFactory;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BasicTests extends BaseClassForTests {
    @Test
    @Disabled("https://github.com/mockito/mockito/issues/2435")
    public void testFactory() throws Exception {
        final ZooKeeper mockZookeeper = Mockito.mock(ZooKeeper.class);
        ZookeeperFactory zookeeperFactory = (connectString, sessionTimeout, watcher, canBeReadOnly) -> mockZookeeper;
        CuratorZookeeperClient client = new CuratorZookeeperClient(zookeeperFactory, new FixedEnsembleProvider(server.getConnectString()),
                10000, 10000, null, new RetryOneTime(1), false);
        client.start();
        assertEquals(client.getZooKeeper(), mockZookeeper);
    }

    @Test
    public void testExpiredSession() throws Exception {
        final Timing timing = new Timing();
        final CountDownLatch latch = new CountDownLatch(1);
        Watcher watcher = event -> {
            if (event.getState() == Watcher.Event.KeeperState.Expired) {
                latch.countDown();
            }
        };
        final CuratorZookeeperClient client = new CuratorZookeeperClient(server.getConnectString(),
                timing.session(), timing.connection(), watcher, new RetryOneTime(2));
        try (client) {
            client.start();
            final AtomicBoolean firstTime = new AtomicBoolean(true);
            RetryLoop.callWithRetry(client, () -> {
                        if (firstTime.compareAndSet(true, false)) {
                            try {
                                client.getZooKeeper().create("/foo", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                            } catch (KeeperException.NodeExistsException ignore) {
                            }
                            client.getZooKeeper().getTestable().injectSessionExpiration();
                            assertTrue(timing.awaitLatch(latch));
                        }
                        ZooKeeper zooKeeper = client.getZooKeeper();
                        client.blockUntilConnectedOrTimedOut();
                        assertNotNull(zooKeeper.exists("/foo", false));
                        return null;
                    }
            );
        }
    }

    @Test
    public void testReconnect() throws Exception {
        CuratorZookeeperClient client = new CuratorZookeeperClient(server.getConnectString(),
                10000, 10000, null, new RetryOneTime(1));
        try (client) {
            client.start();
            client.blockUntilConnectedOrTimedOut();
            byte[] writtenData = {1, 2, 3};
            client.getZooKeeper().create("/test", writtenData, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            Thread.sleep(1000);
            server.stop();
            Thread.sleep(1000);
            server.restart();
            assertTrue(client.blockUntilConnectedOrTimedOut());
            byte[] readData = client.getZooKeeper().getData("/test", false, null);
            assertArrayEquals(readData, writtenData);
        }
    }

    @Test
    public void testSimple() throws Exception {
        CuratorZookeeperClient client = new CuratorZookeeperClient(server.getConnectString(), 10000,
                10000, null, new RetryOneTime(1));
        try (client) {
            client.start();
            client.blockUntilConnectedOrTimedOut();
            String path = client.getZooKeeper().create("/test", new byte[]{1, 2, 3}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            assertEquals(path, "/test");
        }
    }

    @Test
    public void testBackgroundConnect() throws Exception {
        final int connectionTimeoutMs = 4000;
        try (CuratorZookeeperClient client = new CuratorZookeeperClient(server.getConnectString(), 10000,
                connectionTimeoutMs, null, new RetryOneTime(1))) {
            assertFalse(client.isConnected());
            client.start();
            Awaitility.await()
                    .atMost(Duration.ofMillis(connectionTimeoutMs))
                    .untilAsserted(() -> Assertions.assertTrue(client.isConnected()));
        }
    }
}
