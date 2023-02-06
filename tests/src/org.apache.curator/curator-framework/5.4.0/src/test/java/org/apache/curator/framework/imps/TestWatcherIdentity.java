/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.curator.framework.imps;

import com.google.common.collect.Sets;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.BaseClassForTests;
import org.apache.curator.test.Timing;
import org.apache.curator.utils.CloseableUtils;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

public class TestWatcherIdentity extends BaseClassForTests {
    private static final String PATH = "/foo";
    private static final int TIMEOUT_MS = 100000;

    private static class CountZKWatcher implements Watcher {
        private final AtomicInteger count = new AtomicInteger(0);

        @Override
        public void process(WatchedEvent event) {
            count.incrementAndGet();
        }
    }

    @Test
    public void testSameWatcherPerZKDocs() throws Exception {
        CountZKWatcher actualWatcher = new CountZKWatcher();
        Timing timing = new Timing();
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), timing.session(),
                timing.connection(), new RetryOneTime(1));
        try {
            client.start();
            client.create().forPath("/test");
            client.checkExists().usingWatcher(actualWatcher).forPath("/test");
            client.getData().usingWatcher(actualWatcher).forPath("/test");
            client.setData().forPath("/test", "foo".getBytes());
            client.delete().forPath("/test");
            Awaitility.await().untilAsserted(() -> assertEquals(1, actualWatcher.count.getAndSet(0)));
            client.create().forPath("/test");
            client.checkExists().usingWatcher(actualWatcher).forPath("/test");
            client.delete().forPath("/test");
            Awaitility.await()
                    .untilAsserted(() -> assertEquals(1, actualWatcher.count.get()));
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testSameCuratorWatcherPerZKDocs() throws Exception {
        CuratorWatcher actualWatcher = mock(CuratorWatcher.class);
        Timing timing = new Timing();
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), timing.session(),
                timing.connection(), new RetryOneTime(1));
        try {
            client.start();
            client.create().forPath("/test");
            client.checkExists().usingWatcher(actualWatcher).forPath("/test");
            client.getData().usingWatcher(actualWatcher).forPath("/test");
            client.setData().forPath("/test", "foo".getBytes());
            client.delete().forPath("/test");
            Mockito.verify(actualWatcher, Mockito.timeout(TIMEOUT_MS).times(1)).process(any(WatchedEvent.class));
            client.create().forPath("/test");
            client.checkExists().usingWatcher(actualWatcher).forPath("/test");
            client.delete().forPath("/test");
            Mockito.verify(actualWatcher, Mockito.timeout(TIMEOUT_MS).times(2)).process(any(WatchedEvent.class));
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testSetAddition() {
        Watcher watcher = event -> {
        };
        NamespaceWatcher namespaceWatcher1 = new NamespaceWatcher(null, watcher, "/foo");
        NamespaceWatcher namespaceWatcher2 = new NamespaceWatcher(null, watcher, "/foo");
        assertEquals(namespaceWatcher1, namespaceWatcher2);
        assertNotEquals(namespaceWatcher1, watcher);
        assertNotEquals(watcher, namespaceWatcher1);
        Set<Watcher> set = Sets.newHashSet();
        set.add(namespaceWatcher1);
        set.add(namespaceWatcher2);
        assertEquals(set.size(), 1);
    }

    @Test
    public void testCuratorWatcher() throws Exception {
        Timing timing = new Timing();
        CuratorWatcher watcher = mock(CuratorWatcher.class);
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), timing.session(),
                timing.connection(), new RetryOneTime(1));
        try {
            client.start();
            client.create().forPath(PATH);
            client.getData().usingWatcher(watcher).forPath(PATH);
            client.getData().usingWatcher(watcher).forPath(PATH);
            client.setData().forPath(PATH, new byte[]{});
            Mockito.verify(watcher, Mockito.timeout(TIMEOUT_MS).times(1)).process(any(WatchedEvent.class));
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testZKWatcher() throws Exception {
        Timing timing = new Timing();
        CountZKWatcher watcher = new CountZKWatcher();
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), timing.session(),
                timing.connection(), new RetryOneTime(1));
        try {
            client.start();
            client.create().forPath(PATH);
            client.getData().usingWatcher(watcher).forPath(PATH);
            client.getData().usingWatcher(watcher).forPath(PATH);
            client.setData().forPath(PATH, new byte[]{});
            Awaitility.await()
                    .untilAsserted(() -> assertEquals(1, watcher.count.get()));
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }
}
