/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_framework.imps;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.BaseClassForTests;
import org.apache.curator.test.TestingServer;
import org.apache.curator.test.Timing;
import org.apache.curator.utils.CloseableUtils;
import org.junit.jupiter.api.Test;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings("NonAtomicOperationOnVolatileField")
public class TestBlockUntilConnected extends BaseClassForTests {
    @Test
    public void testBlockUntilConnectedCurrentlyConnected() {
        Timing timing = new Timing();
        CuratorFramework client = CuratorFrameworkFactory.builder().
                connectString(server.getConnectString()).
                retryPolicy(new RetryOneTime(1)).
                build();
        try {
            final CountDownLatch connectedLatch = new CountDownLatch(1);
            client.getConnectionStateListenable().addListener((client1, newState) -> {
                if (newState.isConnected()) {
                    connectedLatch.countDown();
                }
            });
            client.start();
            assertTrue(timing.awaitLatch(connectedLatch), "Timed out awaiting latch");
            assertTrue(client.blockUntilConnected(1, TimeUnit.SECONDS), "Not connected");
        } catch (InterruptedException e) {
            fail("Unexpected interruption");
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testBlockUntilConnectedCurrentlyNeverConnected() {
        CuratorFramework client = CuratorFrameworkFactory.builder().
                connectString(server.getConnectString()).
                retryPolicy(new RetryOneTime(1)).
                build();
        try {
            client.start();
            assertTrue(client.blockUntilConnected(5, TimeUnit.SECONDS), "Not connected");
        } catch (InterruptedException e) {
            fail("Unexpected interruption");
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testBlockUntilConnectedCurrentlyAwaitingReconnect() {
        Timing timing = new Timing();
        CuratorFramework client = CuratorFrameworkFactory.builder().
                connectString(server.getConnectString()).
                sessionTimeoutMs(timing.session()).
                retryPolicy(new RetryOneTime(1)).
                build();
        final CountDownLatch lostLatch = new CountDownLatch(1);
        client.getConnectionStateListenable().addListener((client1, newState) -> {
            if (newState == ConnectionState.LOST) {
                lostLatch.countDown();
            }
        });
        try {
            client.start();
            assertTrue(client.blockUntilConnected(5, TimeUnit.SECONDS), "Failed to connect");
            CloseableUtils.closeQuietly(server);
            assertTrue(timing.awaitLatch(lostLatch), "Failed to reach LOST state");
            server = new TestingServer(server.getPort(), server.getTempDirectory());
            assertTrue(client.blockUntilConnected(5, TimeUnit.SECONDS), "Not connected");
        } catch (Exception e) {
            fail("Unexpected exception " + e);
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testBlockUntilConnectedConnectTimeout() {
        CloseableUtils.closeQuietly(server);
        CuratorFramework client = CuratorFrameworkFactory.builder().
                connectString(server.getConnectString()).
                retryPolicy(new RetryOneTime(1)).
                build();
        try {
            client.start();
            assertFalse(client.blockUntilConnected(5, TimeUnit.SECONDS), "Connected");
        } catch (InterruptedException e) {
            fail("Unexpected interruption");
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testBlockUntilConnectedInterrupt() {
        CloseableUtils.closeQuietly(server);
        final CuratorFramework client = CuratorFrameworkFactory.builder().
                connectString(server.getConnectString()).
                retryPolicy(new RetryOneTime(1)).
                build();
        try {
            client.start();
            final Thread threadToInterrupt = Thread.currentThread();
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {

                @Override
                public void run() {
                    threadToInterrupt.interrupt();
                }
            }, 3000);
            client.blockUntilConnected(5, TimeUnit.SECONDS);
            fail("Expected interruption did not occur");
        } catch (InterruptedException ignored) {
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testBlockUntilConnectedTightLoop() throws InterruptedException {
        CuratorFramework client;
        for (int i = 0; i < 50; i++) {
            client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(100));
            try {
                client.start();
                client.blockUntilConnected();

                assertTrue(client.getZookeeperClient().isConnected(), "Not connected after blocking for connection #" + i);
            } finally {
                client.close();
            }
        }
    }
}
