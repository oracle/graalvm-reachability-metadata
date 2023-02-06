/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_framework.state;

import com.google.common.collect.Queues;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.framework.state.SessionConnectionStateErrorPolicy;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.BaseClassForTests;
import org.apache.curator.test.compatibility.CuratorTestBase;
import org.apache.curator.test.compatibility.Timing2;
import org.apache.curator.utils.CloseableUtils;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestConnectionStateManager extends BaseClassForTests {
    @Test
    @Tag(CuratorTestBase.zk35TestCompatibilityGroup)
    public void testSessionConnectionStateErrorPolicyWithExpirationPercent30() throws Exception {
        Timing2 timing = new Timing2();
        CuratorFramework client = CuratorFrameworkFactory.builder()
                .connectString(server.getConnectString())
                .connectionTimeoutMs(1000)
                .sessionTimeoutMs(timing.session())
                .retryPolicy(new RetryOneTime(1))
                .connectionStateErrorPolicy(new SessionConnectionStateErrorPolicy())
                .simulatedSessionExpirationPercent(30)
                .build();
        final int lostStateExpectedMs = (timing.session() / 3) + timing.forSleepingABit().milliseconds();
        try {
            CountDownLatch connectedLatch = new CountDownLatch(1);
            CountDownLatch lostLatch = new CountDownLatch(1);
            ConnectionStateListener stateListener = (client1, newState) -> {
                if (newState == ConnectionState.CONNECTED) {
                    connectedLatch.countDown();
                }
                if (newState == ConnectionState.LOST) {
                    lostLatch.countDown();
                }
            };
            timing.sleepABit();
            client.getConnectionStateListenable().addListener(stateListener);
            client.start();
            assertTrue(timing.awaitLatch(connectedLatch));
            server.close();
            assertTrue(lostLatch.await(lostStateExpectedMs, TimeUnit.MILLISECONDS));
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    @Tag(CuratorTestBase.zk36Group)
    public void testConnectionStateRecoversFromUnexpectedExpiredConnection() throws Exception {
        Timing2 timing = new Timing2();
        CuratorFramework client = CuratorFrameworkFactory.builder()
                .connectString(server.getConnectString())
                .connectionTimeoutMs(1_000)
                .sessionTimeoutMs(250)
                .retryPolicy(new RetryOneTime(1))
                .connectionStateErrorPolicy(new SessionConnectionStateErrorPolicy())
                .build();
        final BlockingQueue<ConnectionState> queue = Queues.newLinkedBlockingQueue();
        ConnectionStateListener listener = (client1, state) -> queue.add(state);
        client.getConnectionStateListenable().addListener(listener);
        client.start();
        try {
            ConnectionState polled = queue.poll(timing.forWaiting().seconds(), TimeUnit.SECONDS);
            assertEquals(polled, ConnectionState.CONNECTED);
            client.getZookeeperClient().getZooKeeper().getTestable().queueEvent(new WatchedEvent(
                    Watcher.Event.EventType.None, Watcher.Event.KeeperState.Disconnected, null));
            polled = queue.poll(timing.forWaiting().seconds(), TimeUnit.SECONDS);
            assertEquals(polled, ConnectionState.SUSPENDED);
            assertThrows(RuntimeException.class, () -> client.getZookeeperClient()
                    .getZooKeeper().getTestable().queueEvent(new WatchedEvent(
                            Watcher.Event.EventType.None, Watcher.Event.KeeperState.Expired, null) {
                        @Override
                        public String getPath() {
                            throw new RuntimeException("Path doesn't exist!");
                        }
                    }));
            polled = queue.poll(timing.forWaiting().seconds(), TimeUnit.SECONDS);
            assertEquals(polled, ConnectionState.LOST);
            polled = queue.poll(timing.forWaiting().seconds(), TimeUnit.SECONDS);
            assertEquals(polled, ConnectionState.RECONNECTED);
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }
}
