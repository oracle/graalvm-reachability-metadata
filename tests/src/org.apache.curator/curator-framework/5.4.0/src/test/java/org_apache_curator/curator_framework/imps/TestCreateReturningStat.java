/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_framework.imps;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.BackgroundCallback;
import org.apache.curator.framework.api.CuratorEventType;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.Timing;
import org.apache.curator.test.compatibility.CuratorTestBase;
import org.apache.curator.utils.CloseableUtils;
import org.apache.zookeeper.data.Stat;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class TestCreateReturningStat extends CuratorTestBase {
    private CuratorFramework createClient() {
        return CuratorFrameworkFactory.builder().
                connectString(server.getConnectString()).
                retryPolicy(new RetryOneTime(1)).
                build();
    }

    private void compare(CuratorFramework client, String path,
                         Stat expected) throws Exception {
        Stat queriedStat = client.checkExists().forPath(path);
        assertEquals(queriedStat, expected);
    }

    @Test
    public void testOrSetDataStoringStatIn() throws Exception {
        try (CuratorFramework client = createClient()) {
            client.start();
            client.getZookeeperClient().blockUntilConnectedOrTimedOut();
            final String path = "/test";
            final Stat versionZeroStat = new Stat();
            client.create().orSetData().storingStatIn(versionZeroStat).forPath(path);
            assertEquals(0, versionZeroStat.getVersion());
            final Stat versionOneStat = new Stat();
            client.create().orSetData().storingStatIn(versionOneStat).forPath(path);
            assertEquals(versionZeroStat.getAversion(), versionOneStat.getAversion());
            assertEquals(versionZeroStat.getCtime(), versionOneStat.getCtime());
            assertEquals(versionZeroStat.getCversion(), versionOneStat.getCversion());
            assertEquals(versionZeroStat.getCzxid(), versionOneStat.getCzxid());
            assertEquals(versionZeroStat.getDataLength(), versionOneStat.getDataLength());
            assertEquals(versionZeroStat.getEphemeralOwner(), versionOneStat.getEphemeralOwner());
            assertTrue(versionZeroStat.getMtime() <= versionOneStat.getMtime());
            assertNotEquals(versionZeroStat.getMzxid(), versionOneStat.getMzxid());
            assertEquals(versionZeroStat.getNumChildren(), versionOneStat.getNumChildren());
            assertEquals(versionZeroStat.getPzxid(), versionOneStat.getPzxid());
            assertEquals(1, versionOneStat.getVersion());
        }
    }

    @Test
    public void testCreateReturningStat() throws Exception {
        CuratorFramework client = createClient();
        try {
            client.start();
            String path = "/bla";
            Stat stat = new Stat();
            client.create().storingStatIn(stat).forPath(path);
            compare(client, path, stat);
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testCreateReturningStatIncludingParents() throws Exception {
        CuratorFramework client = createClient();
        try {
            client.start();
            String path = "/bla/bla";
            Stat stat = new Stat();
            client.create().creatingParentsIfNeeded().storingStatIn(stat).forPath(path);
            compare(client, path, stat);
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testCreateReturningStatIncludingParentsReverse() throws Exception {
        CuratorFramework client = createClient();
        try {
            client.start();
            String path = "/bla/bla";
            Stat stat = new Stat();
            client.create().storingStatIn(stat).creatingParentsIfNeeded().forPath(path);
            compare(client, path, stat);
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testCreateReturningStatCompressed() throws Exception {
        CuratorFramework client = createClient();
        try {
            client.start();
            String path = "/bla";
            Stat stat = new Stat();
            client.create().compressed().storingStatIn(stat).forPath(path);
            compare(client, path, stat);
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testCreateReturningStatWithProtected() throws Exception {
        CuratorFramework client = createClient();
        try {
            client.start();
            String path = "/bla";
            Stat stat = new Stat();
            path = client.create().withProtection().storingStatIn(stat).forPath(path);
            compare(client, path, stat);
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testCreateReturningStatInBackground() throws Exception {
        Timing timing = new Timing();
        CuratorFramework client = createClient();
        try {
            client.start();
            String path = "/bla";
            Stat stat = new Stat();
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Stat> statRef = new AtomicReference<>();
            BackgroundCallback callback = (client1, event) -> {
                if (event.getType() == CuratorEventType.CREATE) {
                    statRef.set(event.getStat());
                    latch.countDown();
                }
            };
            client.create().storingStatIn(stat).inBackground(callback).forPath(path);
            if (!timing.awaitLatch(latch)) {
                fail("Timed out awaing latch");
            }
            compare(client, path, statRef.get());
            compare(client, path, stat);
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testIdempotentCreateReturningStat() throws Exception {
        CuratorFramework client = createClient();
        try {
            client.start();
            String path = "/testidp";
            client.create().forPath(path);
            Stat stat = new Stat();
            client.create().idempotent().storingStatIn(stat).forPath(path);
            compare(client, path, stat);
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }
}
