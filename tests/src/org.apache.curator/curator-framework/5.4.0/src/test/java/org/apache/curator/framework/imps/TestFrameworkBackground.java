/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.curator.framework.imps;

import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.ACLProvider;
import org.apache.curator.framework.api.BackgroundCallback;
import org.apache.curator.framework.api.UnhandledErrorListener;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.RetryNTimes;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.BaseClassForTests;
import org.apache.curator.test.Timing;
import org.apache.curator.utils.CloseableUtils;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.data.ACL;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("unused")
public class TestFrameworkBackground extends BaseClassForTests {
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Test
    public void testErrorListener() throws Exception {
        final AtomicBoolean aclProviderCalled = new AtomicBoolean(false);
        ACLProvider badAclProvider = new ACLProvider() {
            @Override
            public List<ACL> getDefaultAcl() {
                if (aclProviderCalled.getAndSet(true)) {
                    throw new UnsupportedOperationException();
                } else {
                    return new ArrayList<>();
                }
            }

            @Override
            public List<ACL> getAclForPath(String path) {
                if (aclProviderCalled.getAndSet(true)) {
                    throw new UnsupportedOperationException();
                } else {
                    return new ArrayList<>();
                }
            }
        };
        CuratorFramework client = CuratorFrameworkFactory.builder()
                .connectString(server.getConnectString())
                .retryPolicy(new RetryOneTime(1))
                .aclProvider(badAclProvider)
                .build();
        try {
            client.start();
            final CountDownLatch errorLatch = new CountDownLatch(1);
            UnhandledErrorListener listener = (message, e) -> {
                if (e instanceof UnsupportedOperationException) {
                    errorLatch.countDown();
                }
            };
            client.create().inBackground().withUnhandledErrorListener(listener).forPath("/foo");
            assertTrue(new Timing().awaitLatch(errorLatch));
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testListenerConnectedAtStart() throws Exception {
        server.stop();
        Timing timing = new Timing(2);
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), timing.session(), timing.connection(), new RetryNTimes(0, 0));
        try {
            client.start();
            final CountDownLatch connectedLatch = new CountDownLatch(1);
            final AtomicBoolean firstListenerAction = new AtomicBoolean(true);
            final AtomicReference<ConnectionState> firstListenerState = new AtomicReference<>();
            ConnectionStateListener listener = (client1, newState) -> {
                if (firstListenerAction.compareAndSet(true, false)) {
                    firstListenerState.set(newState);
                    System.out.println("First listener state is " + newState);
                }
                if (newState == ConnectionState.CONNECTED) {
                    connectedLatch.countDown();
                }
            };
            client.getConnectionStateListenable().addListener(listener);
            client.create().inBackground().forPath("/foo");
            server.restart();
            assertTrue(timing.awaitLatch(connectedLatch));
            assertFalse(firstListenerAction.get());
            ConnectionState firstconnectionState = firstListenerState.get();
            assertEquals(firstconnectionState, ConnectionState.CONNECTED, "First listener state MUST BE CONNECTED but is " + firstconnectionState);
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testRetries() throws Exception {
        final int sleep = 1000;
        final int timesFirst = 5;
        Timing timing = new Timing();
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), timing.session(), timing.connection(), new RetryNTimes(timesFirst, sleep));
        try {
            client.start();
            client.getZookeeperClient().blockUntilConnectedOrTimedOut();
            final CountDownLatch latch = new CountDownLatch(timesFirst);
            final List<Long> times = Lists.newArrayList();
            final AtomicLong start = new AtomicLong(System.currentTimeMillis());
            ((CuratorFrameworkImpl) client).debugListener = data -> {
                if (data.getOperation().getClass().getName().contains("CreateBuilderImpl")) {
                    long now = System.currentTimeMillis();
                    times.add(now - start.get());
                    start.set(now);
                    latch.countDown();
                }
            };
            server.stop();
            client.create().inBackground().forPath("/one");
            latch.await();
            for (long elapsed : times.subList(1, times.size())) {
                assertTrue(elapsed >= sleep, elapsed + ": " + times);
            }
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testBasic() throws Exception {
        Timing timing = new Timing();
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), timing.session(), timing.connection(), new RetryOneTime(1));
        try {
            client.start();
            final BlockingQueue<String> paths = Queues.newLinkedBlockingQueue();
            BackgroundCallback callback = (client1, event) -> paths.add(event.getPath());
            client.create().inBackground(callback).forPath("/one");
            assertEquals(paths.poll(timing.milliseconds(), TimeUnit.MILLISECONDS), "/one");
            client.create().inBackground(callback).forPath("/one/two");
            assertEquals(paths.poll(timing.milliseconds(), TimeUnit.MILLISECONDS), "/one/two");
            client.create().inBackground(callback).forPath("/one/two/three");
            assertEquals(paths.poll(timing.milliseconds(), TimeUnit.MILLISECONDS), "/one/two/three");
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testCuratorCallbackOnError() throws Exception {
        Timing timing = new Timing();
        try (CuratorFramework client = CuratorFrameworkFactory.builder()
                .connectString(server.getConnectString())
                .sessionTimeoutMs(timing.session())
                .connectionTimeoutMs(timing.connection())
                .retryPolicy(new RetryOneTime(1000)).build()) {
            final CountDownLatch latch = new CountDownLatch(1);
            client.start();
            BackgroundCallback curatorCallback = (client1, event) -> {
                if (event.getResultCode() == Code.CONNECTIONLOSS.intValue()) {
                    latch.countDown();
                }
            };
            server.stop();
            client.getChildren().inBackground(curatorCallback).forPath("/");
            assertTrue(timing.awaitLatch(latch), "Callback has not been called by curator !");
        }
    }

    @Test
    public void testShutdown() throws Exception {
        Timing timing = new Timing();
        CuratorFramework client = CuratorFrameworkFactory
                .builder()
                .connectString(server.getConnectString())
                .sessionTimeoutMs(timing.session())
                .connectionTimeoutMs(timing.connection()).retryPolicy(new RetryOneTime(1))
                .maxCloseWaitMs(timing.forWaiting().milliseconds())
                .build();
        try {
            final AtomicBoolean hadIllegalStateException = new AtomicBoolean(false);
            ((CuratorFrameworkImpl) client).debugUnhandledErrorListener = (message, e) -> {
                if (e instanceof IllegalStateException) {
                    hadIllegalStateException.set(true);
                }
            };
            client.start();
            final CountDownLatch operationReadyLatch = new CountDownLatch(1);
            ((CuratorFrameworkImpl) client).debugListener = data -> {
                try {
                    operationReadyLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            };
            client.create().inBackground().forPath("/hey");
            timing.sleepABit();
            client.close();
            operationReadyLatch.countDown();
            timing.sleepABit();
            assertFalse(hadIllegalStateException.get());
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }


}
