/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_framework.imps;

import com.google.common.collect.Queues;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.BaseClassForTests;
import org.apache.curator.test.compatibility.Timing2;
import org.apache.curator.utils.CloseableUtils;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Watcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestEnabledSessionExpiredState extends BaseClassForTests {
    private final Timing2 timing = new Timing2();
    private CuratorFramework client;
    private BlockingQueue<ConnectionState> states;

    @BeforeEach
    @Override
    public void setup() throws Exception {
        super.setup();
        client = CuratorFrameworkFactory.builder()
                .connectString(server.getConnectString())
                .connectionTimeoutMs(timing.connection())
                .sessionTimeoutMs(timing.session())
                .retryPolicy(new RetryOneTime(1))
                .build();
        client.start();
        states = Queues.newLinkedBlockingQueue();
        ConnectionStateListener listener = (client, newState) -> states.add(newState);
        client.getConnectionStateListenable().addListener(listener);
    }

    @AfterEach
    @Override
    public void teardown() throws Exception {
        try {
            CloseableUtils.closeQuietly(client);
        } finally {
            super.teardown();
        }
    }

    @Test
    public void testResetCausesLost() throws Exception {
        assertEquals(states.poll(timing.milliseconds(), TimeUnit.MILLISECONDS), ConnectionState.CONNECTED);
        client.checkExists().forPath("/");
        client.getZookeeperClient().reset();
        assertEquals(states.poll(timing.milliseconds(), TimeUnit.MILLISECONDS), ConnectionState.LOST);
        assertEquals(states.poll(timing.milliseconds(), TimeUnit.MILLISECONDS), ConnectionState.RECONNECTED);
    }

    @Test
    public void testInjectedWatchedEvent() throws Exception {
        assertEquals(states.poll(timing.milliseconds(), TimeUnit.MILLISECONDS), ConnectionState.CONNECTED);
        final CountDownLatch latch = new CountDownLatch(1);
        Watcher watcher = event -> {
            if (event.getType() == Watcher.Event.EventType.None) {
                if (event.getState() == Watcher.Event.KeeperState.Expired) {
                    latch.countDown();
                }
            }
        };
        client.checkExists().usingWatcher(watcher).forPath("/");
        server.stop();
        assertTrue(timing.forSessionSleep().awaitLatch(latch));
    }

    @Test
    public void testKillSession() throws Exception {
        assertEquals(states.poll(timing.milliseconds(), TimeUnit.MILLISECONDS), ConnectionState.CONNECTED);
        client.getZookeeperClient().getZooKeeper().getTestable().injectSessionExpiration();
        assertEquals(states.poll(timing.forSessionSleep().milliseconds(), TimeUnit.MILLISECONDS), ConnectionState.LOST);
        assertEquals(states.poll(timing.milliseconds(), TimeUnit.MILLISECONDS), ConnectionState.RECONNECTED);
    }

    @Test
    public void testReconnectWithoutExpiration() throws Exception {
        assertEquals(states.poll(timing.milliseconds(), TimeUnit.MILLISECONDS), ConnectionState.CONNECTED);
        server.stop();
        try {
            client.checkExists().forPath("/");
        } catch (KeeperException.ConnectionLossException ignore) {
        }
        assertEquals(states.poll(timing.milliseconds(), TimeUnit.MILLISECONDS), ConnectionState.SUSPENDED);
        server.restart();
        client.checkExists().forPath("/");
        assertEquals(states.poll(timing.milliseconds(), TimeUnit.MILLISECONDS), ConnectionState.RECONNECTED);
    }

    @Test
    public void testSessionExpirationFromTimeout() throws Exception {
        assertEquals(states.poll(timing.milliseconds(), TimeUnit.MILLISECONDS), ConnectionState.CONNECTED);
        server.stop();
        assertEquals(states.poll(timing.milliseconds(), TimeUnit.MILLISECONDS), ConnectionState.SUSPENDED);
        assertEquals(states.poll(timing.forSessionSleep().milliseconds(), TimeUnit.MILLISECONDS), ConnectionState.LOST);
    }

    @Test
    public void testSessionExpirationFromTimeoutWithRestart() throws Exception {
        assertEquals(states.poll(timing.milliseconds(), TimeUnit.MILLISECONDS), ConnectionState.CONNECTED);
        server.stop();
        timing.forSessionSleep().sleep();
        assertEquals(states.poll(timing.milliseconds(), TimeUnit.MILLISECONDS), ConnectionState.SUSPENDED);
        assertEquals(states.poll(timing.forSessionSleep().milliseconds(), TimeUnit.MILLISECONDS), ConnectionState.LOST);
        server.restart();
        client.checkExists().forPath("/");
        assertEquals(states.poll(timing.milliseconds(), TimeUnit.MILLISECONDS), ConnectionState.RECONNECTED);
        assertNull(states.poll(timing.multiple(.5).milliseconds(), TimeUnit.MILLISECONDS));
    }
}
