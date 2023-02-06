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
import org.apache.curator.framework.api.BackgroundCallback;
import org.apache.curator.framework.api.CuratorEvent;
import org.apache.curator.framework.api.transaction.CuratorOp;
import org.apache.curator.framework.api.transaction.CuratorTransactionResult;
import org.apache.curator.framework.api.transaction.OperationType;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.BaseClassForTests;
import org.apache.curator.test.Timing;
import org.apache.curator.utils.CloseableUtils;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings("OptionalGetWithoutIsPresent")
public class TestTransactionsNew extends BaseClassForTests {
    @Test
    public void testErrors() throws Exception {
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        try {
            client.start();
            CuratorOp createOp1 = client.transactionOp().create().forPath("/bar");
            CuratorOp createOp2 = client.transactionOp().create().forPath("/z/blue");
            final BlockingQueue<CuratorEvent> callbackQueue = new LinkedBlockingQueue<>();
            BackgroundCallback callback = (client1, event) -> callbackQueue.add(event);
            client.transaction().inBackground(callback).forOperations(createOp1, createOp2);
            CuratorEvent event = callbackQueue.poll(new Timing().milliseconds(), TimeUnit.MILLISECONDS);
            assertNotNull(event);
            assertNotNull(event.getOpResults());
            assertEquals(event.getOpResults().size(), 2);
            assertEquals(event.getOpResults().get(0).getError(), KeeperException.Code.OK.intValue());
            assertEquals(event.getOpResults().get(1).getError(), KeeperException.Code.NONODE.intValue());
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testCheckVersion() throws Exception {
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        try {
            client.start();
            client.create().forPath("/foo");
            Stat stat = client.setData().forPath("/foo", "new".getBytes());
            CuratorOp statOp = client.transactionOp().check().withVersion(stat.getVersion() + 1).forPath("/foo");
            CuratorOp createOp = client.transactionOp().create().forPath("/bar");
            try {
                client.transaction().forOperations(statOp, createOp);
                fail();
            } catch (KeeperException.BadVersionException ignored) {
            }
            assertNull(client.checkExists().forPath("/bar"));
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testWithNamespace() throws Exception {
        CuratorFramework client = CuratorFrameworkFactory.builder().connectString(server.getConnectString()).retryPolicy(new RetryOneTime(1)).namespace("galt").build();
        try {
            client.start();
            CuratorOp createOp1 = client.transactionOp().create().forPath("/foo", "one".getBytes());
            CuratorOp createOp2 = client.transactionOp().create().withMode(CreateMode.PERSISTENT_SEQUENTIAL).forPath("/test-", "one".getBytes());
            CuratorOp setDataOp = client.transactionOp().setData().forPath("/foo", "two".getBytes());
            CuratorOp createOp3 = client.transactionOp().create().forPath("/foo/bar");
            CuratorOp deleteOp = client.transactionOp().delete().forPath("/foo/bar");
            Collection<CuratorTransactionResult> results = client.transaction().forOperations(createOp1, createOp2, setDataOp, createOp3, deleteOp);
            assertNotNull(client.checkExists().forPath("/foo"));
            assertNotNull(client.usingNamespace(null).checkExists().forPath("/galt/foo"));
            assertArrayEquals(client.getData().forPath("/foo"), "two".getBytes());
            assertNull(client.checkExists().forPath("/foo/bar"));
            CuratorTransactionResult ephemeralResult = results.stream()
                    .filter(CuratorTransactionResult.ofTypeAndPath(OperationType.CREATE, "/test-")::apply).findFirst().get();
            assertNotNull(ephemeralResult);
            assertNotEquals(ephemeralResult.getResultPath(), "/test-");
            assertTrue(ephemeralResult.getResultPath().startsWith("/test-"));
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testBasic() throws Exception {
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        try {
            client.start();
            CuratorOp createOp1 = client.transactionOp().create().forPath("/foo");
            CuratorOp createOp2 = client.transactionOp().create().forPath("/foo/bar", "snafu".getBytes());
            Collection<CuratorTransactionResult> results = client.transaction().forOperations(createOp1, createOp2);
            assertNotNull(client.checkExists().forPath("/foo/bar"));
            assertArrayEquals(client.getData().forPath("/foo/bar"), "snafu".getBytes());
            CuratorTransactionResult fooResult = results.stream()
                    .filter(CuratorTransactionResult.ofTypeAndPath(OperationType.CREATE, "/foo")::apply).findFirst().get();
            CuratorTransactionResult fooBarResult = results.stream()
                    .filter(CuratorTransactionResult.ofTypeAndPath(OperationType.CREATE, "/foo/bar")::apply).findFirst().get();
            assertNotNull(fooResult);
            assertNotNull(fooBarResult);
            assertNotSame(fooResult, fooBarResult);
            assertEquals(fooResult.getResultPath(), "/foo");
            assertEquals(fooBarResult.getResultPath(), "/foo/bar");
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testBackground() throws Exception {
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        try {
            client.start();
            CuratorOp createOp1 = client.transactionOp().create().forPath("/foo");
            CuratorOp createOp2 = client.transactionOp().create().forPath("/foo/bar", "snafu".getBytes());
            final BlockingQueue<List<CuratorTransactionResult>> queue = Queues.newLinkedBlockingQueue();
            BackgroundCallback callback = (client1, event) -> queue.add(event.getOpResults());
            client.transaction().inBackground(callback).forOperations(createOp1, createOp2);
            Collection<CuratorTransactionResult> results = queue.poll(5, TimeUnit.SECONDS);
            assertNotNull(results);
            assertNotNull(client.checkExists().forPath("/foo/bar"));
            assertArrayEquals(client.getData().forPath("/foo/bar"), "snafu".getBytes());
            CuratorTransactionResult fooResult = results.stream()
                    .filter(CuratorTransactionResult.ofTypeAndPath(OperationType.CREATE, "/foo")::apply).findFirst().get();
            CuratorTransactionResult fooBarResult = results.stream()
                    .filter(CuratorTransactionResult.ofTypeAndPath(OperationType.CREATE, "/foo/bar")::apply).findFirst().get();
            assertNotNull(fooResult);
            assertNotNull(fooBarResult);
            assertNotSame(fooResult, fooBarResult);
            assertEquals(fooResult.getResultPath(), "/foo");
            assertEquals(fooBarResult.getResultPath(), "/foo/bar");
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testBackgroundWithNamespace() throws Exception {
        CuratorFramework client = CuratorFrameworkFactory.builder().connectString(server.getConnectString()).retryPolicy(new RetryOneTime(1)).namespace("galt").build();
        try {
            client.start();
            CuratorOp createOp1 = client.transactionOp().create().forPath("/foo", "one".getBytes());
            CuratorOp createOp2 = client.transactionOp().create().withMode(CreateMode.PERSISTENT_SEQUENTIAL).forPath("/test-", "one".getBytes());
            CuratorOp setDataOp = client.transactionOp().setData().forPath("/foo", "two".getBytes());
            CuratorOp createOp3 = client.transactionOp().create().forPath("/foo/bar");
            CuratorOp deleteOp = client.transactionOp().delete().forPath("/foo/bar");
            final BlockingQueue<List<CuratorTransactionResult>> queue = Queues.newLinkedBlockingQueue();
            BackgroundCallback callback = (client1, event) -> queue.add(event.getOpResults());
            client.transaction().inBackground(callback).forOperations(createOp1, createOp2, setDataOp, createOp3, deleteOp);
            Collection<CuratorTransactionResult> results = queue.poll(5, TimeUnit.SECONDS);
            assertNotNull(results);
            assertNotNull(client.checkExists().forPath("/foo"));
            assertNotNull(client.usingNamespace(null).checkExists().forPath("/galt/foo"));
            assertArrayEquals(client.getData().forPath("/foo"), "two".getBytes());
            assertNull(client.checkExists().forPath("/foo/bar"));
            CuratorTransactionResult ephemeralResult = results.stream()
                    .filter(CuratorTransactionResult.ofTypeAndPath(OperationType.CREATE, "/test-")::apply).findFirst().get();
            assertNotNull(ephemeralResult);
            assertNotEquals(ephemeralResult.getResultPath(), "/test-");
            assertTrue(ephemeralResult.getResultPath().startsWith("/test-"));
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }
}
