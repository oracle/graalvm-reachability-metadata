/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.curator.framework.imps;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.BackgroundCallback;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.BaseClassForTests;
import org.apache.curator.test.Timing;
import org.apache.curator.utils.CloseableUtils;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class TestFailedDeleteManager extends BaseClassForTests {
    @Test
    public void testLostSession() throws Exception {
        Timing timing = new Timing();
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), timing.session(), timing.connection(), new ExponentialBackoffRetry(100, 3));
        try {
            client.start();
            client.create().forPath("/test-me");
            final CountDownLatch latch = new CountDownLatch(1);
            final Semaphore semaphore = new Semaphore(0);
            ConnectionStateListener listener = (client1, newState) -> {
                if ((newState == ConnectionState.LOST) || (newState == ConnectionState.SUSPENDED)) {
                    semaphore.release();
                } else if (newState == ConnectionState.RECONNECTED) {
                    latch.countDown();
                }
            };
            client.getConnectionStateListenable().addListener(listener);
            server.stop();
            assertTrue(timing.acquireSemaphore(semaphore));
            try {
                client.delete().guaranteed().forPath("/test-me");
                fail();
            } catch (KeeperException.ConnectionLossException | KeeperException.SessionExpiredException ignored) {
            }
            assertTrue(timing.acquireSemaphore(semaphore));
            timing.sleepABit();
            server.restart();
            assertTrue(timing.awaitLatch(latch));
            timing.sleepABit();
            assertNull(client.checkExists().forPath("/test-me"));
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testWithNamespaceAndLostSession() throws Exception {
        Timing timing = new Timing();
        CuratorFramework client = CuratorFrameworkFactory.builder().connectString(server.getConnectString())
                .sessionTimeoutMs(timing.session())
                .connectionTimeoutMs(timing.connection())
                .retryPolicy(new ExponentialBackoffRetry(100, 3))
                .namespace("aisa")
                .build();
        try {
            client.start();
            client.create().forPath("/test-me");
            final CountDownLatch latch = new CountDownLatch(1);
            final Semaphore semaphore = new Semaphore(0);
            ConnectionStateListener listener = (client1, newState) -> {
                if ((newState == ConnectionState.LOST) || (newState == ConnectionState.SUSPENDED)) {
                    semaphore.release();
                } else if (newState == ConnectionState.RECONNECTED) {
                    latch.countDown();
                }
            };
            client.getConnectionStateListenable().addListener(listener);
            server.stop();
            assertTrue(timing.acquireSemaphore(semaphore));
            try {
                client.delete().guaranteed().forPath("/test-me");
                fail();
            } catch (KeeperException.ConnectionLossException | KeeperException.SessionExpiredException ignored) {
            }
            assertTrue(timing.acquireSemaphore(semaphore));
            timing.sleepABit();
            server.restart();
            assertTrue(timing.awaitLatch(latch));
            timing.sleepABit();
            assertNull(client.checkExists().forPath("/test-me"));
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testWithNamespaceAndLostSessionAlt() throws Exception {
        Timing timing = new Timing();
        CuratorFramework client = CuratorFrameworkFactory.builder().connectString(server.getConnectString())
                .sessionTimeoutMs(timing.session())
                .connectionTimeoutMs(timing.connection())
                .retryPolicy(new ExponentialBackoffRetry(100, 3))
                .build();
        try {
            client.start();
            CuratorFramework namespaceClient = client.usingNamespace("foo");
            namespaceClient.create().forPath("/test-me");
            final CountDownLatch latch = new CountDownLatch(1);
            final Semaphore semaphore = new Semaphore(0);
            ConnectionStateListener listener = (client1, newState) -> {
                if ((newState == ConnectionState.LOST) || (newState == ConnectionState.SUSPENDED)) {
                    semaphore.release();
                } else if (newState == ConnectionState.RECONNECTED) {
                    latch.countDown();
                }
            };
            namespaceClient.getConnectionStateListenable().addListener(listener);
            server.stop();
            assertTrue(timing.acquireSemaphore(semaphore));
            try {
                namespaceClient.delete().guaranteed().forPath("/test-me");
                fail();
            } catch (KeeperException.ConnectionLossException | KeeperException.SessionExpiredException ignored) {
            }
            assertTrue(timing.acquireSemaphore(semaphore));
            timing.sleepABit();
            server.restart();
            assertTrue(timing.awaitLatch(latch));
            timing.sleepABit();
            assertNull(namespaceClient.checkExists().forPath("/test-me"));
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testBasic() throws Exception {
        final String path = "/one/two/three";
        Timing timing = new Timing();
        CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder();
        builder.connectString(server.getConnectString()).retryPolicy(new RetryOneTime(1)).connectionTimeoutMs(timing.connection()).sessionTimeoutMs(timing.session());
        CuratorFrameworkImpl client = new CuratorFrameworkImpl(builder);
        client.start();
        try {
            client.create().creatingParentsIfNeeded().forPath(path);
            assertNotNull(client.checkExists().forPath(path));
            server.stop();
            try {
                client.delete().forPath(path);
                fail();
            } catch (KeeperException.ConnectionLossException | KeeperException.SessionExpiredException ignored) {
            }
            server.restart();
            assertNotNull(client.checkExists().forPath(path));
            server.stop();
            try {
                client.delete().guaranteed().forPath(path);
                fail();
            } catch (KeeperException.ConnectionLossException | KeeperException.SessionExpiredException ignored) {
            }
            server.restart();
            final int tries = 5;
            for (int i = 0; i < tries; ++i) {
                if (client.checkExists().forPath(path) != null) {
                    timing.sleepABit();
                }
            }
            assertNull(client.checkExists().forPath(path));
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testGuaranteedDeleteOnNonExistentNodeInForeground() throws Exception {
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        client.start();
        final AtomicBoolean pathAdded = new AtomicBoolean(false);
        ((CuratorFrameworkImpl) client).getFailedDeleteManager().debugListener = path -> pathAdded.set(true);
        try {
            client.delete().guaranteed().forPath("/nonexistent");
            fail();
        } catch (NoNodeException e) {
            assertFalse(pathAdded.get());
        } finally {
            client.close();
        }
    }

    @Test
    public void testGuaranteedDeleteOnNonExistentNodeInBackground() throws Exception {
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        client.start();
        final AtomicBoolean pathAdded = new AtomicBoolean(false);
        ((CuratorFrameworkImpl) client).getFailedDeleteManager().debugListener = path -> pathAdded.set(true);
        final CountDownLatch backgroundLatch = new CountDownLatch(1);
        BackgroundCallback background = (client1, event) -> backgroundLatch.countDown();
        try {
            client.delete().guaranteed().inBackground(background).forPath("/nonexistent");
            backgroundLatch.await();
            assertFalse(pathAdded.get());
        } finally {
            client.close();
        }
    }
}
