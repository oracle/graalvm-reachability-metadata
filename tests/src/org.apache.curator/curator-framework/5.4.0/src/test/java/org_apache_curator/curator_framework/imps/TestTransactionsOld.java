/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_framework.imps;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.transaction.CuratorTransactionResult;
import org.apache.curator.framework.api.transaction.OperationType;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.BaseClassForTests;
import org.apache.curator.utils.CloseableUtils;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.Stat;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings({"deprecation", "OptionalGetWithoutIsPresent"})
public class TestTransactionsOld extends BaseClassForTests {
    @Test
    public void testCheckVersion() throws Exception {
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        try {
            client.start();
            client.create().forPath("/foo");
            Stat stat = client.setData().forPath("/foo", "new".getBytes());
            try {
                client.inTransaction()
                        .check().withVersion(stat.getVersion() + 1).forPath("/foo")
                        .and()
                        .create().forPath("/bar")
                        .and()
                        .commit();
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
            Collection<CuratorTransactionResult> results = client.inTransaction()
                    .create().forPath("/foo", "one".getBytes())
                    .and()
                    .create().withMode(CreateMode.PERSISTENT_SEQUENTIAL).forPath("/test-", "one".getBytes())
                    .and()
                    .setData().forPath("/foo", "two".getBytes())
                    .and()
                    .create().forPath("/foo/bar")
                    .and()
                    .delete().forPath("/foo/bar")
                    .and()
                    .commit();
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
    public void testWithCompression() throws Exception {
        CuratorFramework client = CuratorFrameworkFactory.builder().connectString(server.getConnectString()).retryPolicy(new RetryOneTime(1)).namespace("galt").build();
        try (client) {
            client.start();
            Collection<CuratorTransactionResult> results =
                    client.inTransaction()
                            .create().compressed().forPath("/foo", "one".getBytes())
                            .and()
                            .create().compressed().withACL(ZooDefs.Ids.READ_ACL_UNSAFE).forPath("/bar", "two".getBytes())
                            .and()
                            .create().compressed().withMode(CreateMode.PERSISTENT_SEQUENTIAL).forPath("/test-", "three".getBytes())
                            .and()
                            .create().compressed().withMode(CreateMode.PERSISTENT).withACL(ZooDefs.Ids.READ_ACL_UNSAFE).forPath("/baz", "four".getBytes())
                            .and()
                            .setData().compressed().withVersion(0).forPath("/foo", "five".getBytes())
                            .and()
                            .commit();
            assertNotNull(client.checkExists().forPath("/foo"));
            assertArrayEquals(client.getData().decompressed().forPath("/foo"), "five".getBytes());
            assertNotNull(client.checkExists().forPath("/bar"));
            assertArrayEquals(client.getData().decompressed().forPath("/bar"), "two".getBytes());
            assertEquals(client.getACL().forPath("/bar"), ZooDefs.Ids.READ_ACL_UNSAFE);
            CuratorTransactionResult ephemeralResult = results.stream()
                    .filter(CuratorTransactionResult.ofTypeAndPath(OperationType.CREATE, "/test-")::apply).findFirst().get();
            assertNotNull(ephemeralResult);
            assertNotEquals(ephemeralResult.getResultPath(), "/test-");
            assertTrue(ephemeralResult.getResultPath().startsWith("/test-"));
            assertNotNull(client.checkExists().forPath("/baz"));
            assertArrayEquals(client.getData().decompressed().forPath("/baz"), "four".getBytes());
            assertEquals(client.getACL().forPath("/baz"), ZooDefs.Ids.READ_ACL_UNSAFE);
        }
    }

    @Test
    public void testBasic() throws Exception {
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        try {
            client.start();
            Collection<CuratorTransactionResult> results =
                    client.inTransaction()
                            .create().forPath("/foo")
                            .and()
                            .create().forPath("/foo/bar", "snafu".getBytes())
                            .and()
                            .commit();

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
}
