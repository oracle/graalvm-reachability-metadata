/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.curator.framework.imps;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.WatcherRemoveCuratorFramework;
import org.apache.curator.framework.api.BackgroundCallback;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.Timing;
import org.apache.curator.test.WatchersDebug;
import org.apache.curator.test.compatibility.CuratorTestBase;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class TestWatcherRemovalManager extends CuratorTestBase {
    @Test
    public void testSameWatcherDifferentPaths1Triggered() throws Exception {
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        try {
            client.start();
            WatcherRemovalFacade removerClient = (WatcherRemovalFacade) client.newWatcherRemoveCuratorFramework();
            final CountDownLatch latch = new CountDownLatch(1);
            Watcher watcher = event -> latch.countDown();
            removerClient.checkExists().usingWatcher(watcher).forPath("/a/b/c");
            removerClient.checkExists().usingWatcher(watcher).forPath("/d/e/f");
            removerClient.create().creatingParentsIfNeeded().forPath("/d/e/f");
            Timing timing = new Timing();
            assertTrue(timing.awaitLatch(latch));
            timing.sleepABit();
            removerClient.removeWatchers();
        } finally {
            TestCleanState.closeAndTestClean(client);
        }
    }

    @Test
    public void testSameWatcherDifferentPaths() throws Exception {
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        try {
            client.start();
            WatcherRemovalFacade removerClient = (WatcherRemovalFacade) client.newWatcherRemoveCuratorFramework();
            Watcher watcher = event -> {
            };
            removerClient.checkExists().usingWatcher(watcher).forPath("/a/b/c");
            removerClient.checkExists().usingWatcher(watcher).forPath("/d/e/f");
            assertEquals(removerClient.getWatcherRemovalManager().getEntries().size(), 2);
            removerClient.removeWatchers();
        } finally {
            TestCleanState.closeAndTestClean(client);
        }
    }

    @Test
    public void testSameWatcherDifferentKinds1Triggered() throws Exception {
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        try {
            client.start();
            WatcherRemovalFacade removerClient = (WatcherRemovalFacade) client.newWatcherRemoveCuratorFramework();
            final CountDownLatch latch = new CountDownLatch(1);
            Watcher watcher = event -> latch.countDown();
            removerClient.create().creatingParentsIfNeeded().forPath("/a/b/c");
            removerClient.checkExists().usingWatcher(watcher).forPath("/a/b/c");
            removerClient.getData().usingWatcher(watcher).forPath("/a/b/c");
            removerClient.setData().forPath("/a/b/c", "new".getBytes());
            Timing timing = new Timing();
            assertTrue(timing.awaitLatch(latch));
            timing.sleepABit();
            removerClient.removeWatchers();
        } finally {
            TestCleanState.closeAndTestClean(client);
        }
    }

    @Test
    public void testSameWatcherDifferentKinds() throws Exception {
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        try {
            client.start();
            WatcherRemovalFacade removerClient = (WatcherRemovalFacade) client.newWatcherRemoveCuratorFramework();
            Watcher watcher = event -> {
            };
            removerClient.create().creatingParentsIfNeeded().forPath("/a/b/c");
            removerClient.checkExists().usingWatcher(watcher).forPath("/a/b/c");
            removerClient.getData().usingWatcher(watcher).forPath("/a/b/c");
            removerClient.removeWatchers();
        } finally {
            TestCleanState.closeAndTestClean(client);
        }
    }

    @Test
    public void testWithRetry() throws Exception {
        server.stop();
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        try {
            client.start();
            WatcherRemovalFacade removerClient = (WatcherRemovalFacade) client.newWatcherRemoveCuratorFramework();
            Watcher w = event -> {
            };
            try {
                removerClient.checkExists().usingWatcher(w).forPath("/one/two/three");
                fail("Should have thrown ConnectionLossException");
            } catch (KeeperException.ConnectionLossException expected) {
            }
            assertEquals(removerClient.getWatcherRemovalManager().getEntries().size(), 0);
        } finally {
            TestCleanState.closeAndTestClean(client);
        }
    }

    @Test
    public void testWithRetryInBackground() throws Exception {
        server.stop();
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        try {
            client.start();
            WatcherRemovalFacade removerClient = (WatcherRemovalFacade) client.newWatcherRemoveCuratorFramework();
            Watcher w = event -> {
            };
            final CountDownLatch latch = new CountDownLatch(1);
            BackgroundCallback callback = (client1, event) -> latch.countDown();
            removerClient.checkExists().usingWatcher(w).inBackground(callback).forPath("/one/two/three");
            assertTrue(new Timing().awaitLatch(latch));
            assertEquals(removerClient.getWatcherRemovalManager().getEntries().size(), 0);
        } finally {
            TestCleanState.closeAndTestClean(client);
        }
    }

    @Test
    public void testMissingNode() throws Exception {
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        try {
            client.start();
            WatcherRemovalFacade removerClient = (WatcherRemovalFacade) client.newWatcherRemoveCuratorFramework();
            Watcher w = event -> {
            };
            try {
                removerClient.getData().usingWatcher(w).forPath("/one/two/three");
                fail("Should have thrown NoNodeException");
            } catch (KeeperException.NoNodeException expected) {
            }
            removerClient.removeWatchers();
        } finally {
            TestCleanState.closeAndTestClean(client);
        }
    }

    @Test
    public void testMissingNodeInBackground() throws Exception {
        final CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        Callable<Void> proc = () -> {
            client.start();
            WatcherRemovalFacade removerClient = (WatcherRemovalFacade) client.newWatcherRemoveCuratorFramework();
            Watcher w = event -> {
            };
            final CountDownLatch latch = new CountDownLatch(1);
            BackgroundCallback callback = (client1, event) -> latch.countDown();
            removerClient.getData().usingWatcher(w).inBackground(callback).forPath("/one/two/three");
            assertTrue(new Timing().awaitLatch(latch));
            assertEquals(removerClient.getWatcherRemovalManager().getEntries().size(), 0);
            removerClient.removeWatchers();
            return null;
        };
        TestCleanState.test(client, proc);
    }

    @Test
    public void testBasic() throws Exception {
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        try {
            client.start();
            internalTryBasic(client);
        } finally {
            TestCleanState.closeAndTestClean(client);
        }
    }

    @Test
    public void testBasicNamespace1() throws Exception {
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        try {
            client.start();
            internalTryBasic(client.usingNamespace("foo"));
        } finally {
            TestCleanState.closeAndTestClean(client);
        }
    }

    @Test
    public void testBasicNamespace2() throws Exception {
        CuratorFramework client = CuratorFrameworkFactory.builder()
                .connectString(server.getConnectString())
                .retryPolicy(new RetryOneTime(1))
                .namespace("hey")
                .build();
        try {
            client.start();
            internalTryBasic(client);
        } finally {
            TestCleanState.closeAndTestClean(client);
        }
    }

    @Test
    public void testBasicNamespace3() throws Exception {
        CuratorFramework client = CuratorFrameworkFactory.builder()
                .connectString(server.getConnectString())
                .retryPolicy(new RetryOneTime(1))
                .namespace("hey")
                .build();
        try {
            client.start();
            internalTryBasic(client.usingNamespace("lakjsf"));
        } finally {
            TestCleanState.closeAndTestClean(client);
        }
    }

    @Test
    public void testSameWatcher() throws Exception {
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        try {
            client.start();
            client.create().forPath("/test");
            WatcherRemovalFacade removerClient = (WatcherRemovalFacade) client.newWatcherRemoveCuratorFramework();
            Watcher watcher = event -> {
            };
            removerClient.getData().usingWatcher(watcher).forPath("/test");
            assertEquals(removerClient.getRemovalManager().getEntries().size(), 1);
            removerClient.getData().usingWatcher(watcher).forPath("/test");
            assertEquals(removerClient.getRemovalManager().getEntries().size(), 1);
            removerClient.removeWatchers();
        } finally {
            TestCleanState.closeAndTestClean(client);
        }
    }

    @Test
    public void testTriggered() throws Exception {
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        try {
            client.start();
            WatcherRemovalFacade removerClient = (WatcherRemovalFacade) client.newWatcherRemoveCuratorFramework();
            final CountDownLatch latch = new CountDownLatch(1);
            Watcher watcher = event -> {
                if (event.getType() == Watcher.Event.EventType.NodeCreated) {
                    latch.countDown();
                }
            };
            removerClient.checkExists().usingWatcher(watcher).forPath("/yo");
            assertEquals(removerClient.getRemovalManager().getEntries().size(), 1);
            removerClient.create().forPath("/yo");
            assertTrue(new Timing().awaitLatch(latch));
            assertEquals(removerClient.getRemovalManager().getEntries().size(), 0);
        } finally {
            TestCleanState.closeAndTestClean(client);
        }
    }

    @Test
    public void testResetFromWatcher() throws Exception {
        Timing timing = new Timing();
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        try {
            client.start();
            final WatcherRemovalFacade removerClient = (WatcherRemovalFacade) client.newWatcherRemoveCuratorFramework();
            final CountDownLatch createdLatch = new CountDownLatch(1);
            final CountDownLatch deletedLatch = new CountDownLatch(1);
            Watcher watcher = new Watcher() {
                @Override
                public void process(WatchedEvent event) {
                    if (event.getType() == Event.EventType.NodeCreated) {
                        try {
                            removerClient.checkExists().usingWatcher(this).forPath("/yo");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        createdLatch.countDown();
                    } else if (event.getType() == Event.EventType.NodeDeleted) {
                        deletedLatch.countDown();
                    }
                }
            };
            removerClient.checkExists().usingWatcher(watcher).forPath("/yo");
            assertEquals(removerClient.getRemovalManager().getEntries().size(), 1);
            removerClient.create().forPath("/yo");
            assertTrue(timing.awaitLatch(createdLatch));
            assertEquals(removerClient.getRemovalManager().getEntries().size(), 1);
            removerClient.delete().forPath("/yo");
            assertTrue(timing.awaitLatch(deletedLatch));
            assertEquals(removerClient.getRemovalManager().getEntries().size(), 0);
        } finally {
            TestCleanState.closeAndTestClean(client);
        }
    }

    private void internalTryBasic(CuratorFramework client) throws Exception {
        WatcherRemoveCuratorFramework removerClient = client.newWatcherRemoveCuratorFramework();
        final CountDownLatch latch = new CountDownLatch(1);
        Watcher watcher = event -> {
            if (event.getType() == Watcher.Event.EventType.DataWatchRemoved) {
                latch.countDown();
            }
        };
        removerClient.checkExists().usingWatcher(watcher).forPath("/hey");
        List<String> existWatches = WatchersDebug.getExistWatches(client.getZookeeperClient().getZooKeeper());
        assertEquals(existWatches.size(), 1);
        removerClient.removeWatchers();
        assertTrue(new Timing().awaitLatch(latch));
        existWatches = WatchersDebug.getExistWatches(client.getZookeeperClient().getZooKeeper());
        assertEquals(existWatches.size(), 0);
    }
}
