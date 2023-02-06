/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_framework.imps;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.transaction.CuratorOp;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.BaseClassForTests;
import org.apache.curator.utils.CloseableUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestCompressionInTransactionNew extends BaseClassForTests {
    @Test
    public void testSetData() throws Exception {
        final String path = "/a";
        final byte[] data = "here's a string".getBytes();
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        try {
            client.start();
            CuratorOp op = client.transactionOp().create().forPath(path, data);
            client.transaction().forOperations(op);
            assertArrayEquals(data, client.getData().forPath(path));
            op = client.transactionOp().setData().compressed().forPath(path, data);
            client.transaction().forOperations(op);
            assertArrayEquals(data, client.getData().decompressed().forPath(path));
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testSetCompressedAndUncompressed() throws Exception {
        final String path1 = "/a";
        final String path2 = "/b";
        final byte[] data1 = "here's a string".getBytes();
        final byte[] data2 = "here's another string".getBytes();
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        try {
            client.start();
            CuratorOp op1 = client.transactionOp().create().compressed().forPath(path1);
            CuratorOp op2 = client.transactionOp().create().forPath(path2);
            client.transaction().forOperations(op1, op2);
            assertNotNull(client.checkExists().forPath(path1));
            assertNotNull(client.checkExists().forPath(path2));
            op1 = client.transactionOp().setData().compressed().forPath(path1, data1);
            op2 = client.transactionOp().setData().forPath(path2, data2);
            client.transaction().forOperations(op1, op2);
            assertNotEquals(data1, client.getData().forPath(path1));
            assertArrayEquals(data1, client.getData().decompressed().forPath(path1));
            assertArrayEquals(data2, client.getData().forPath(path2));
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testSimple() throws Exception {
        final String path1 = "/a";
        final String path2 = "/a/b";
        final byte[] data1 = "here's a string".getBytes();
        final byte[] data2 = "here's another string".getBytes();
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        try {
            client.start();
            CuratorOp op1 = client.transactionOp().create().compressed().forPath(path1, data1);
            CuratorOp op2 = client.transactionOp().create().compressed().forPath(path2, data2);
            client.transaction().forOperations(op1, op2);
            assertNotEquals(data1, client.getData().forPath(path1));
            assertArrayEquals(data1, client.getData().decompressed().forPath(path1));
            assertNotEquals(data2, client.getData().forPath(path2));
            assertArrayEquals(data2, client.getData().decompressed().forPath(path2));
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testCreateCompressedAndUncompressed() throws Exception {
        final String path1 = "/a";
        final String path2 = "/b";
        final byte[] data1 = "here's a string".getBytes();
        final byte[] data2 = "here's another string".getBytes();
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        try {
            client.start();
            CuratorOp op1 = client.transactionOp().create().compressed().forPath(path1, data1);
            CuratorOp op2 = client.transactionOp().create().forPath(path2, data2);
            client.transaction().forOperations(op1, op2);
            assertNotEquals(data1, client.getData().forPath(path1));
            assertArrayEquals(data1, client.getData().decompressed().forPath(path1));
            assertArrayEquals(data2, client.getData().forPath(path2));
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }
}
