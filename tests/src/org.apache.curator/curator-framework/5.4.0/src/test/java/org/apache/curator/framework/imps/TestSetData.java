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
import org.apache.curator.framework.api.BackgroundPathAndBytesable;
import org.apache.curator.framework.api.SetDataBuilder;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.BaseClassForTests;
import org.apache.curator.utils.CloseableUtils;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.apache.zookeeper.KeeperException.Code.BADVERSION;
import static org.apache.zookeeper.KeeperException.Code.NONODE;
import static org.apache.zookeeper.KeeperException.Code.OK;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({"FieldMayBeFinal", "deprecation", "unused"})
public class TestSetData extends BaseClassForTests {
    private static byte[] createData = new byte[]{5, 6, 7, 8};

    private CuratorFramework createClient() {
        return CuratorFrameworkFactory.builder().
                connectString(server.getConnectString()).
                retryPolicy(new RetryOneTime(1)).
                build();
    }

    @Test
    public void testNormal() throws Exception {
        CuratorFramework client = createClient();
        try {
            client.start();
            Stat stat = new Stat();
            String path = "/test";
            byte[] data = new byte[]{1};
            byte[] data2 = new byte[]{1, 2};
            check(client, client.setData(), path, data, NONODE.intValue(), -1);
            client.create().forPath(path, createData);
            check(client, client.setData(), path, data, OK.intValue(), 1);
            check(client, client.setData().withVersion(1), path, data2, OK.intValue(), 2);
            check(client, client.setData().withVersion(1), path, data, BADVERSION.intValue(), 2);
            assertArrayEquals(data2, client.getData().forPath(path));
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testBackground() throws Exception {
        CuratorFramework client = createClient();
        try {
            client.start();
            Stat stat = new Stat();
            String path = "/test";
            byte[] data = new byte[]{1};
            byte[] data2 = new byte[]{1, 2};
            checkBackground(client, client.setData(), path, data, NONODE.intValue(), -1);
            client.create().forPath(path, createData);
            checkBackground(client, client.setData(), path, data, OK.intValue(), 1);
            checkBackground(client, client.setData().withVersion(1), path, data2, OK.intValue(), 2);
            checkBackground(client, client.setData().withVersion(1), path, data, BADVERSION.intValue(), 3);
            assertArrayEquals(data2, client.getData().forPath(path));

        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    private void check(CuratorFramework client, BackgroundPathAndBytesable<Stat> builder, String path, byte[] data, int expectedCode, int expectedVersionAfter) throws Exception {
        try {
            builder.forPath(path, data);
            assertEquals(expectedCode, OK.intValue());
            Stat stat = new Stat();
            byte[] actualData = client.getData().storingStatIn(stat).forPath(path);
            assertTrue(IdempotentUtils.matches(expectedVersionAfter, data, stat.getVersion(), actualData));
        } catch (KeeperException e) {
            assertEquals(expectedCode, e.getCode());
        }
    }

    private void checkBackground(CuratorFramework client, BackgroundPathAndBytesable<Stat> builder, String path, byte[] data, int expectedCode, int expectedVersionAfter) throws Exception {
        AtomicInteger actualCode = new AtomicInteger(-1);
        CountDownLatch latch = new CountDownLatch(1);
        BackgroundCallback callback = (client1, event) -> {
            actualCode.set(event.getResultCode());
            latch.countDown();
        };
        builder.inBackground(callback).forPath(path, data);
        assertTrue(latch.await(5000, TimeUnit.MILLISECONDS), "Callback not invoked");
        assertEquals(expectedCode, actualCode.get());
        if (expectedCode == OK.intValue()) {
            Stat stat = new Stat();
            byte[] actualData = client.getData().storingStatIn(stat).forPath(path);
            assertTrue(IdempotentUtils.matches(expectedVersionAfter, data, stat.getVersion(), actualData));
        }
    }

    @Test
    public void testIdempotentSet() throws Exception {
        CuratorFramework client = createClient();
        try {
            client.start();
            Stat stat = new Stat();
            String path = "/idpset";
            String pathBack = "/idpsetback";
            byte[] data1 = new byte[]{1, 2, 3};
            byte[] data2 = new byte[]{4, 5, 6};
            byte[] data3 = new byte[]{7, 8, 9};
            check(client, client.setData().idempotent(), path, data1, NONODE.intValue(), -1);
            checkBackground(client, client.setData().idempotent(), pathBack, data1, NONODE.intValue(), -1);
            client.create().forPath(path, createData);
            client.create().forPath(pathBack, createData);
            check(client, client.setData().idempotent(), path, data1, OK.intValue(), 1);
            checkBackground(client, client.setData().idempotent(), pathBack, data1, OK.intValue(), 1);
            check(client, client.setData().idempotent().withVersion(1), path, data2, OK.intValue(), 2);
            checkBackground(client, client.setData().idempotent().withVersion(1), pathBack, data2, OK.intValue(), 2);
            check(client, client.setData().idempotent().withVersion(1), path, data2, OK.intValue(), 2);
            checkBackground(client, client.setData().idempotent().withVersion(1), pathBack, data2, OK.intValue(), 2);
            check(client, client.setData().idempotent().withVersion(1), path, data3, BADVERSION.intValue(), 1);
            checkBackground(client, client.setData().idempotent().withVersion(1), pathBack, data3, BADVERSION.intValue(), 1);
            check(client, client.setData().idempotent().withVersion(0), path, data2, BADVERSION.intValue(), 2);
            checkBackground(client, client.setData().idempotent().withVersion(0), pathBack, data2, BADVERSION.intValue(), 2);
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    private SetDataBuilder clBefore(SetDataBuilder builder) {
        ((SetDataBuilderImpl) builder).failBeforeNextSetForTesting = true;
        return builder;
    }

    private SetDataBuilder clAfter(SetDataBuilder builder) {
        ((SetDataBuilderImpl) builder).failNextSetForTesting = true;
        return builder;
    }

    private SetDataBuilder clCheck(SetDataBuilder builder) {
        ((SetDataBuilderImpl) builder).failNextIdempotentCheckForTesting = true;
        return builder;
    }

    @Test
    public void testIdempotentSetConnectionLoss() throws Exception {
        CuratorFramework client = createClient();
        try {
            client.start();
            String idpPath = "/idpset";
            String idpPathBack = "/idpsetback";
            String path = "/set";
            String pathBack = "/setBack";
            byte[] data = new byte[]{1};
            byte[] data2 = new byte[]{1, 2};
            byte[] data3 = new byte[]{1, 2, 3};
            byte[] data4 = new byte[]{1, 2, 3, 4};
            check(client, clBefore(client.setData().idempotent()), idpPath, data, NONODE.intValue(), -1);
            checkBackground(client, clBefore(client.setData().idempotent()), idpPathBack, data, NONODE.intValue(), -1);
            check(client, clAfter(client.setData().idempotent()), idpPath, data, NONODE.intValue(), -1);
            checkBackground(client, clAfter(client.setData().idempotent()), idpPathBack, data, NONODE.intValue(), -1);
            check(client, clCheck(client.setData().idempotent()), idpPath, data, NONODE.intValue(), -1);
            checkBackground(client, clCheck(client.setData().idempotent()), idpPathBack, data, NONODE.intValue(), -1);
            client.create().forPath(idpPath, createData);
            client.create().forPath(idpPathBack, createData);
            check(client, clBefore(client.setData().idempotent()).withVersion(0), idpPath, data, OK.intValue(), 1);
            checkBackground(client, clBefore(client.setData().idempotent()).withVersion(0), idpPathBack, data, OK.intValue(), 1);
            check(client, clAfter(client.setData().idempotent()).withVersion(1), idpPath, data2, OK.intValue(), 2);
            checkBackground(client, clAfter(client.setData().idempotent()).withVersion(1), idpPathBack, data2, OK.intValue(), 2);
            check(client, clBefore(client.setData().idempotent()), idpPath, data3, OK.intValue(), 3);
            checkBackground(client, clBefore(client.setData().idempotent()), idpPathBack, data3, OK.intValue(), 3);
            check(client, clAfter(client.setData().idempotent()), idpPath, data4, OK.intValue(), 5);
            checkBackground(client, clAfter(client.setData().idempotent()), idpPathBack, data4, OK.intValue(), 5);
            check(client, clBefore(client.setData().idempotent()).withVersion(4), idpPath, data4, OK.intValue(), 5);
            checkBackground(client, clBefore(client.setData().idempotent()).withVersion(4), idpPathBack, data4, OK.intValue(), 5);
            check(client, clAfter(client.setData().idempotent()).withVersion(4), idpPath, data4, OK.intValue(), 5);
            checkBackground(client, clAfter(client.setData().idempotent()).withVersion(4), idpPathBack, data4, OK.intValue(), 5);
            check(client, clCheck(client.setData().idempotent()).withVersion(4), idpPath, data4, OK.intValue(), 5);
            checkBackground(client, clCheck(client.setData().idempotent()).withVersion(4), idpPathBack, data4, OK.intValue(), 5);
            check(client, clBefore(client.setData().idempotent()).withVersion(0), idpPath, data4, BADVERSION.intValue(), -1);
            checkBackground(client, clBefore(client.setData().idempotent()).withVersion(0), idpPathBack, data4, BADVERSION.intValue(), -1);
            check(client, clAfter(client.setData().idempotent()).withVersion(0), idpPath, data4, BADVERSION.intValue(), -1);
            checkBackground(client, clAfter(client.setData().idempotent()).withVersion(0), idpPathBack, data4, BADVERSION.intValue(), -1);
            check(client, clCheck(client.setData().idempotent()).withVersion(0), idpPath, data4, BADVERSION.intValue(), -1);
            checkBackground(client, clCheck(client.setData().idempotent()).withVersion(0), idpPathBack, data4, BADVERSION.intValue(), -1);
            check(client, clBefore(client.setData().idempotent()).withVersion(4), idpPath, data, BADVERSION.intValue(), -1);
            checkBackground(client, clBefore(client.setData().idempotent()).withVersion(4), idpPathBack, data, BADVERSION.intValue(), -1);
            check(client, clAfter(client.setData().idempotent()).withVersion(4), idpPath, data, BADVERSION.intValue(), -1);
            checkBackground(client, clAfter(client.setData().idempotent()).withVersion(4), idpPathBack, data, BADVERSION.intValue(), -1);
            check(client, clCheck(client.setData().idempotent()).withVersion(4), idpPath, data, BADVERSION.intValue(), -1);
            checkBackground(client, clCheck(client.setData().idempotent()).withVersion(4), idpPathBack, data, BADVERSION.intValue(), -1);
            client.create().forPath(path, createData);
            client.create().forPath(pathBack, createData);
            check(client, clBefore(client.setData()).withVersion(0), path, data, OK.intValue(), 1);
            checkBackground(client, clBefore(client.setData()).withVersion(0), pathBack, data, OK.intValue(), 1);
            check(client, clAfter(client.setData()).withVersion(1), path, data2, BADVERSION.intValue(), -1);
            checkBackground(client, clAfter(client.setData()).withVersion(1), pathBack, data2, BADVERSION.intValue(), -1);
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }
}
