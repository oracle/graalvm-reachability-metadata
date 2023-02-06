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
import org.apache.curator.framework.api.CuratorEventType;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.Timing;
import org.apache.curator.test.compatibility.CuratorTestBase;
import org.apache.curator.utils.CloseableUtils;
import org.apache.curator.utils.ZookeeperFactory;
import org.apache.zookeeper.AddWatchMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.Watcher.WatcherType;
import org.apache.zookeeper.ZooKeeper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings({"deprecation", "SameParameterValue"})
public class TestWatchesBuilder extends CuratorTestBase {
    private AtomicReference<ConnectionState> registerConnectionStateListener(CuratorFramework client) {
        final AtomicReference<ConnectionState> state = new AtomicReference<>();
        client.getConnectionStateListenable().addListener((client1, newState) -> {
            state.set(newState);
            synchronized (state) {
                state.notify();
            }
        });

        return state;
    }

    private boolean blockUntilDesiredConnectionState(AtomicReference<ConnectionState> stateRef, Timing timing, final ConnectionState desiredState) {
        if (stateRef.get() == desiredState) {
            return true;
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stateRef) {
            if (stateRef.get() == desiredState) {
                return true;
            }

            try {
                stateRef.wait(timing.milliseconds());
                return stateRef.get() == desiredState;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    @Test
    public void testRemoveCuratorDefaultWatcher() throws Exception {
        Timing timing = new Timing();
        CuratorFramework client = CuratorFrameworkFactory.builder().connectString(server.getConnectString()).retryPolicy(new RetryOneTime(1)).build();
        try {
            client.start();

            final CountDownLatch removedLatch = new CountDownLatch(1);

            final String path = "/";
            client.getCuratorListenable().addListener((client1, event) -> {
                if (event.getType() == CuratorEventType.WATCHED && event.getWatchedEvent().getType() == EventType.DataWatchRemoved) {
                    removedLatch.countDown();
                }
            });
            client.checkExists().watched().forPath(path);
            client.watches().removeAll().forPath(path);
            assertTrue(timing.awaitLatch(removedLatch), "Timed out waiting for watch removal");
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testRemoveCuratorWatch() throws Exception {
        Timing timing = new Timing();
        CuratorFrameworkImpl client = (CuratorFrameworkImpl) CuratorFrameworkFactory.builder().connectString(server.getConnectString()).retryPolicy(new RetryOneTime(1)).build();
        try {
            client.start();
            final CountDownLatch removedLatch = new CountDownLatch(1);
            final String path = "/";
            CuratorWatcher watcher = event -> {
                if (event.getPath().equals(path) && event.getType() == EventType.DataWatchRemoved) {
                    removedLatch.countDown();
                }
            };
            client.checkExists().usingWatcher(watcher).forPath(path);
            client.watches().remove(watcher).forPath(path);
            assertTrue(timing.awaitLatch(removedLatch), "Timed out waiting for watch removal");
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testRemoveWatch() throws Exception {
        Timing timing = new Timing();
        CuratorFrameworkImpl client = (CuratorFrameworkImpl) CuratorFrameworkFactory.builder().connectString(server.getConnectString()).retryPolicy(new RetryOneTime(1)).build();
        try {
            client.start();
            final CountDownLatch removedLatch = new CountDownLatch(1);
            final String path = "/";
            Watcher watcher = new CountDownWatcher(path, removedLatch, EventType.DataWatchRemoved);
            client.checkExists().usingWatcher(watcher).forPath(path);
            client.watches().remove(watcher).forPath(path);
            assertTrue(timing.awaitLatch(removedLatch), "Timed out waiting for watch removal");
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testRemoveWatchInBackgroundWithCallback() throws Exception {
        Timing timing = new Timing();
        CuratorFrameworkImpl client = (CuratorFrameworkImpl) CuratorFrameworkFactory.builder().connectString(server.getConnectString()).retryPolicy(new RetryOneTime(1)).build();
        try {
            client.start();
            final CountDownLatch removedLatch = new CountDownLatch(2);
            final String path = "/";
            Watcher watcher = new CountDownWatcher(path, removedLatch, EventType.DataWatchRemoved);
            BackgroundCallback callback = (client1, event) -> {
                if (event.getType() == CuratorEventType.REMOVE_WATCHES && event.getPath().equals(path)) {
                    removedLatch.countDown();
                }
            };
            client.checkExists().usingWatcher(watcher).forPath(path);
            client.watches().remove(watcher).ofType(WatcherType.Any).inBackground(callback).forPath(path);
            assertTrue(timing.awaitLatch(removedLatch), "Timed out waiting for watch removal");
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testRemoveWatchInBackgroundWithNoCallback() throws Exception {
        Timing timing = new Timing();
        CuratorFrameworkImpl client = (CuratorFrameworkImpl) CuratorFrameworkFactory.builder().connectString(server.getConnectString()).retryPolicy(new RetryOneTime(1)).build();
        try {
            client.start();
            final String path = "/";
            final CountDownLatch removedLatch = new CountDownLatch(1);
            Watcher watcher = new CountDownWatcher(path, removedLatch, EventType.DataWatchRemoved);
            client.checkExists().usingWatcher(watcher).forPath(path);
            client.watches().remove(watcher).inBackground().forPath(path);
            assertTrue(timing.awaitLatch(removedLatch), "Timed out waiting for watch removal");
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testRemoveAllWatches() throws Exception {
        Timing timing = new Timing();
        CuratorFrameworkImpl client = (CuratorFrameworkImpl) CuratorFrameworkFactory.builder().connectString(server.getConnectString()).retryPolicy(new RetryOneTime(1)).build();
        try {
            client.start();
            final String path = "/";
            final CountDownLatch removedLatch = new CountDownLatch(2);
            Watcher watcher1 = new CountDownWatcher(path, removedLatch, EventType.ChildWatchRemoved);
            Watcher watcher2 = new CountDownWatcher(path, removedLatch, EventType.DataWatchRemoved);
            client.getChildren().usingWatcher(watcher1).forPath(path);
            client.checkExists().usingWatcher(watcher2).forPath(path);
            client.watches().removeAll().forPath(path);
            assertTrue(timing.awaitLatch(removedLatch), "Timed out waiting for watch removal");
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testRemoveAllDataWatches() throws Exception {
        Timing timing = new Timing();
        CuratorFramework client = CuratorFrameworkFactory.builder().connectString(server.getConnectString()).retryPolicy(new RetryOneTime(1)).build();
        try {
            client.start();
            final String path = "/";
            final AtomicBoolean removedFlag = new AtomicBoolean(false);
            final CountDownLatch removedLatch = new CountDownLatch(1);
            Watcher watcher1 = new BooleanWatcher(path, removedFlag, EventType.ChildWatchRemoved);
            Watcher watcher2 = new CountDownWatcher(path, removedLatch, EventType.DataWatchRemoved);
            client.getChildren().usingWatcher(watcher1).forPath(path);
            client.checkExists().usingWatcher(watcher2).forPath(path);
            client.watches().removeAll().ofType(WatcherType.Data).forPath(path);
            assertTrue(timing.awaitLatch(removedLatch), "Timed out waiting for watch removal");
            assertFalse(removedFlag.get());
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testRemoveAllChildWatches() throws Exception {
        Timing timing = new Timing();
        CuratorFramework client = CuratorFrameworkFactory.builder().connectString(server.getConnectString()).retryPolicy(new RetryOneTime(1)).build();
        try {
            client.start();
            final String path = "/";
            final AtomicBoolean removedFlag = new AtomicBoolean(false);
            final CountDownLatch removedLatch = new CountDownLatch(1);
            Watcher watcher1 = new BooleanWatcher(path, removedFlag, EventType.DataWatchRemoved);
            Watcher watcher2 = new CountDownWatcher(path, removedLatch, EventType.ChildWatchRemoved);
            client.checkExists().usingWatcher(watcher1).forPath(path);
            client.getChildren().usingWatcher(watcher2).forPath(path);
            client.watches().removeAll().ofType(WatcherType.Children).forPath(path);
            assertTrue(timing.awaitLatch(removedLatch), "Timed out waiting for watch removal");
            assertFalse(removedFlag.get());
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testRemoveLocalWatch() throws Exception {
        Timing timing = new Timing();
        CuratorFrameworkImpl client = (CuratorFrameworkImpl) CuratorFrameworkFactory.builder().connectString(server.getConnectString()).retryPolicy(new RetryOneTime(1)).build();
        try {
            client.start();
            AtomicReference<ConnectionState> stateRef = registerConnectionStateListener(client);
            final String path = "/";
            final CountDownLatch removedLatch = new CountDownLatch(1);
            Watcher watcher = new CountDownWatcher(path, removedLatch, EventType.DataWatchRemoved);
            client.checkExists().usingWatcher(watcher).forPath(path);
            server.stop();
            assertTrue(blockUntilDesiredConnectionState(stateRef, timing, ConnectionState.SUSPENDED));
            client.watches().removeAll().locally().forPath(path);
            assertTrue(timing.awaitLatch(removedLatch), "Timed out waiting for watch removal");
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testRemoveLocalWatchInBackground() throws Exception {
        Timing timing = new Timing();
        CuratorFrameworkImpl client = (CuratorFrameworkImpl) CuratorFrameworkFactory.builder().connectString(server.getConnectString()).retryPolicy(new RetryOneTime(1)).build();
        try {
            client.start();
            AtomicReference<ConnectionState> stateRef = registerConnectionStateListener(client);
            final String path = "/";
            final CountDownLatch removedLatch = new CountDownLatch(1);
            Watcher watcher = new CountDownWatcher(path, removedLatch, EventType.DataWatchRemoved);
            client.checkExists().usingWatcher(watcher).forPath(path);
            server.stop();
            assertTrue(blockUntilDesiredConnectionState(stateRef, timing, ConnectionState.SUSPENDED));
            client.watches().removeAll().locally().inBackground().forPath(path);
            assertTrue(timing.awaitLatch(removedLatch), "Timed out waiting for watch removal");
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testRemoveUnregisteredWatcher() throws Exception {
        CuratorFramework client = CuratorFrameworkFactory.builder().connectString(server.getConnectString()).retryPolicy(new RetryOneTime(1)).build();
        try {
            client.start();
            final String path = "/";
            Watcher watcher = event -> {
            };
            try {
                client.watches().remove(watcher).forPath(path);
                fail("Expected KeeperException.NoWatcherException");
            } catch (KeeperException.NoWatcherException expected) {
            }
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testRemoveUnregisteredWatcherQuietly() throws Exception {
        Timing timing = new Timing();
        CuratorFramework client = CuratorFrameworkFactory.builder().connectString(server.getConnectString()).retryPolicy(new RetryOneTime(1)).build();
        try {
            client.start();
            final AtomicBoolean watcherRemoved = new AtomicBoolean(false);
            final String path = "/";
            Watcher watcher = new BooleanWatcher(path, watcherRemoved, EventType.DataWatchRemoved);
            client.watches().remove(watcher).quietly().forPath(path);
            timing.sleepABit();
            assertFalse(watcherRemoved.get());
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testGuaranteedRemoveWatch() throws Exception {
        Timing timing = new Timing();
        CuratorFramework client = CuratorFrameworkFactory.builder().connectString(server.getConnectString()).retryPolicy(new RetryOneTime(1)).build();
        try {
            client.start();
            AtomicReference<ConnectionState> stateRef = registerConnectionStateListener(client);
            String path = "/";
            CountDownLatch removeLatch = new CountDownLatch(1);
            Watcher watcher = new CountDownWatcher(path, removeLatch, EventType.DataWatchRemoved);
            client.checkExists().usingWatcher(watcher).forPath(path);
            server.stop();
            assertTrue(blockUntilDesiredConnectionState(stateRef, timing, ConnectionState.SUSPENDED));
            try {
                client.watches().remove(watcher).guaranteed().forPath(path);
                fail();
            } catch (KeeperException.ConnectionLossException ignored) {
            }
            server.restart();
            timing.awaitLatch(removeLatch);
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testGuaranteedRemoveWatchInBackground() throws Exception {
        Timing timing = new Timing();
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), timing.session(), timing.connection(), new ExponentialBackoffRetry(100, 3));
        try {
            client.start();
            AtomicReference<ConnectionState> stateRef = registerConnectionStateListener(client);
            final CountDownLatch guaranteeAddedLatch = new CountDownLatch(1);
            ((CuratorFrameworkImpl) client).getFailedRemoveWatcherManager().debugListener = detail -> guaranteeAddedLatch.countDown();
            String path = "/";
            CountDownLatch removeLatch = new CountDownLatch(1);
            Watcher watcher = new CountDownWatcher(path, removeLatch, EventType.DataWatchRemoved);
            client.checkExists().usingWatcher(watcher).forPath(path);
            server.stop();
            assertTrue(blockUntilDesiredConnectionState(stateRef, timing, ConnectionState.SUSPENDED));
            client.watches().remove(watcher).guaranteed().inBackground().forPath(path);
            timing.awaitLatch(guaranteeAddedLatch);
            server.restart();
            timing.awaitLatch(removeLatch);
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    @Tag(CuratorTestBase.zk36Group)
    public void testPersistentWatch() throws Exception {
        try (CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1))) {
            client.start();
            client.blockUntilConnected();
            CountDownLatch latch = new CountDownLatch(3);
            Watcher watcher = event -> latch.countDown();
            client.watchers().add().withMode(AddWatchMode.PERSISTENT).usingWatcher(watcher).forPath("/test/foo");
            client.create().creatingParentsIfNeeded().forPath("/test/foo");
            client.setData().forPath("/test/foo", "hey".getBytes());
            client.delete().forPath("/test/foo");
            assertTrue(timing.awaitLatch(latch));
        }
    }

    @Test
    @Tag(CuratorTestBase.zk36Group)
    public void testPersistentWatchInBackground() throws Exception {
        try (CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1))) {
            client.start();
            client.blockUntilConnected();
            CountDownLatch backgroundLatch = new CountDownLatch(1);
            BackgroundCallback backgroundCallback = (__, ___) -> backgroundLatch.countDown();
            CountDownLatch latch = new CountDownLatch(3);
            Watcher watcher = event -> latch.countDown();
            client.watchers().add().withMode(AddWatchMode.PERSISTENT).inBackground(backgroundCallback).usingWatcher(watcher).forPath("/test/foo");
            client.create().creatingParentsIfNeeded().forPath("/test/foo");
            client.setData().forPath("/test/foo", "hey".getBytes());
            client.delete().forPath("/test/foo");
            assertTrue(timing.awaitLatch(backgroundLatch));
            assertTrue(timing.awaitLatch(latch));
        }
    }

    @Test
    @Tag(CuratorTestBase.zk36Group)
    public void testPersistentRecursiveWatch() throws Exception {
        try (CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1))) {
            client.start();
            client.blockUntilConnected();
            CountDownLatch latch = new CountDownLatch(5);
            Watcher watcher = event -> latch.countDown();
            client.watchers().add().withMode(AddWatchMode.PERSISTENT_RECURSIVE).usingWatcher(watcher).forPath("/test");
            client.create().forPath("/test");
            client.create().forPath("/test/a");
            client.create().forPath("/test/a/b");
            client.create().forPath("/test/a/b/c");
            client.create().forPath("/test/a/b/c/d");
            assertTrue(timing.awaitLatch(latch));
        }
    }

    @Test
    @Tag(CuratorTestBase.zk36Group)
    public void testPersistentRecursiveWatchInBackground() throws Exception {
        try (CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1))) {
            client.start();
            client.blockUntilConnected();
            CountDownLatch backgroundLatch = new CountDownLatch(1);
            BackgroundCallback backgroundCallback = (__, ___) -> backgroundLatch.countDown();
            CountDownLatch latch = new CountDownLatch(5);
            Watcher watcher = event -> latch.countDown();
            client.watchers().add().withMode(AddWatchMode.PERSISTENT_RECURSIVE).inBackground(backgroundCallback).usingWatcher(watcher).forPath("/test");
            client.create().forPath("/test");
            client.create().forPath("/test/a");
            client.create().forPath("/test/a/b");
            client.create().forPath("/test/a/b/c");
            client.create().forPath("/test/a/b/c/d");
            assertTrue(timing.awaitLatch(backgroundLatch));
            assertTrue(timing.awaitLatch(latch));
        }
    }

    @Test
    @Tag(CuratorTestBase.zk36Group)
    public void testPersistentRecursiveDefaultWatch() throws Exception {
        CountDownLatch latch = new CountDownLatch(6);
        ZookeeperFactory zookeeperFactory = (connectString, sessionTimeout, watcher, canBeReadOnly) -> {
            Watcher actualWatcher = event -> {
                watcher.process(event);
                latch.countDown();
            };
            return new ZooKeeper(connectString, sessionTimeout, actualWatcher);
        };
        try (CuratorFramework client = CuratorFrameworkFactory.builder().connectString(server.getConnectString()).retryPolicy(new RetryOneTime(1)).zookeeperFactory(zookeeperFactory).build()) {
            client.start();
            client.blockUntilConnected();
            client.watchers().add().withMode(AddWatchMode.PERSISTENT_RECURSIVE).forPath("/test");
            client.create().forPath("/test");
            client.create().forPath("/test/a");
            client.create().forPath("/test/a/b");
            client.create().forPath("/test/a/b/c");
            client.create().forPath("/test/a/b/c/d");
            assertTrue(timing.awaitLatch(latch));
        }
    }

    @SuppressWarnings("FieldMayBeFinal")
    private static class CountDownWatcher implements Watcher {
        private String path;
        private EventType eventType;
        private CountDownLatch removeLatch;

        CountDownWatcher(String path, CountDownLatch removeLatch, EventType eventType) {
            this.path = path;
            this.eventType = eventType;
            this.removeLatch = removeLatch;
        }

        @Override
        public void process(WatchedEvent event) {
            if (event.getPath() == null || event.getType() == null) {
                return;
            }

            if (event.getPath().equals(path) && event.getType() == eventType) {
                removeLatch.countDown();
            }
        }
    }

    @SuppressWarnings("FieldMayBeFinal")
    private static class BooleanWatcher implements Watcher {
        private String path;
        private EventType eventType;
        private AtomicBoolean removedFlag;

        BooleanWatcher(String path, AtomicBoolean removedFlag, EventType eventType) {
            this.path = path;
            this.eventType = eventType;
            this.removedFlag = removedFlag;
        }

        @Override
        public void process(WatchedEvent event) {
            if (event.getPath() == null || event.getType() == null) {
                return;
            }

            if (event.getPath().equals(path) && event.getType() == eventType) {
                removedFlag.set(true);
            }
        }
    }
}
