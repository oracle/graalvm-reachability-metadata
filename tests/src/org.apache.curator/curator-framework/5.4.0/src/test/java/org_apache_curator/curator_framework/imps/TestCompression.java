/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_framework.imps;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.CompressionProvider;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.BaseClassForTests;
import org.apache.curator.utils.CloseableUtils;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class TestCompression extends BaseClassForTests {
    @Test
    public void testCompressionProvider() throws Exception {
        final byte[] data = "here's a string".getBytes();
        final AtomicInteger compressCounter = new AtomicInteger();
        final AtomicInteger decompressCounter = new AtomicInteger();
        CompressionProvider compressionProvider = new CompressionProvider() {
            @Override
            public byte[] compress(String path, byte[] data) {
                compressCounter.incrementAndGet();
                byte[] bytes = new byte[data.length * 2];
                System.arraycopy(data, 0, bytes, 0, data.length);
                System.arraycopy(data, 0, bytes, data.length, data.length);
                return bytes;
            }

            @Override
            public byte[] decompress(String path, byte[] compressedData) {
                decompressCounter.incrementAndGet();
                byte[] bytes = new byte[compressedData.length / 2];
                System.arraycopy(compressedData, 0, bytes, 0, bytes.length);
                return bytes;
            }
        };
        CuratorFramework client = CuratorFrameworkFactory.builder().
                compressionProvider(compressionProvider).
                connectString(server.getConnectString()).
                retryPolicy(new RetryOneTime(1)).
                build();
        try {
            client.start();
            client.create().compressed().creatingParentsIfNeeded().forPath("/a/b/c", data);
            assertNotEquals(data, client.getData().forPath("/a/b/c"));
            assertEquals(data.length, client.getData().decompressed().forPath("/a/b/c").length);
        } finally {
            CloseableUtils.closeQuietly(client);
        }
        assertEquals(compressCounter.get(), 1);
        assertEquals(decompressCounter.get(), 1);
    }

    @Test
    public void testSetData() throws Exception {
        final byte[] data = "here's a string".getBytes();
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        try {
            client.start();
            client.create().creatingParentsIfNeeded().forPath("/a/b/c", data);
            assertArrayEquals(data, client.getData().forPath("/a/b/c"));
            client.setData().compressed().forPath("/a/b/c", data);
            assertEquals(data.length, client.getData().decompressed().forPath("/a/b/c").length);
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testSimple() throws Exception {
        final byte[] data = "here's a string".getBytes();
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        try {
            client.start();
            client.create().compressed().creatingParentsIfNeeded().forPath("/a/b/c", data);
            assertNotEquals(data, client.getData().forPath("/a/b/c"));
            assertEquals(data.length, client.getData().decompressed().forPath("/a/b/c").length);
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }
}
