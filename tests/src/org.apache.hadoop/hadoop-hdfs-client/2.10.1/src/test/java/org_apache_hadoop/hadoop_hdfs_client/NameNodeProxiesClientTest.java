/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_hdfs_client;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.NameNodeProxiesClient;
import org.apache.hadoop.hdfs.NameNodeProxiesClient.ProxyAndInfo;
import org.apache.hadoop.hdfs.server.namenode.ha.AbstractNNFailoverProxyProvider;
import org.apache.hadoop.hdfs.server.namenode.ha.HAProxyFactory;
import org.apache.hadoop.io.retry.FailoverProxyProvider;
import org.apache.hadoop.io.retry.Idempotent;
import org.apache.hadoop.ipc.RPC;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class NameNodeProxiesClientTest {
    private static final URI CLUSTER = URI.create("hdfs://test-cluster");

    private static Configuration configuration() {
        Configuration conf = new Configuration(false);
        conf.setClass("dfs.client.failover.proxy.provider.test-cluster",
                InProcessProvider.class, FailoverProxyProvider.class);
        conf.setInt("dfs.client.failover.sleep.base.millis", 0);
        conf.setInt("dfs.client.failover.sleep.max.millis", 0);
        return conf;
    }

    @Test
    public void constructsConfiguredProviderAndRoutesRequests() throws IOException {
        AtomicBoolean fallback = new AtomicBoolean();
        InProcessProvider provider;
        try (AbstractNNFailoverProxyProvider<StorageProtocol> created =
                NameNodeProxiesClient.createFailoverProxyProvider(
                        configuration(), CLUSTER, StorageProtocol.class, true, fallback)) {
            provider = (InProcessProvider) created;
            assertSame(StorageProtocol.class, provider.getInterface());
            assertSame(fallback, provider.getFallbackToSimpleAuth());
            assertTrue(provider.useLogicalURI());
            assertArrayEquals(new long[]{300, 20, 280}, provider.getProxy().proxy.getStats());
            assertEquals(1, provider.endpoint.reads.get());
        }
        assertTrue(provider.endpoint.closed);
    }

    @Test
    public void retriesDroppedResponseThroughLossyProxy() throws IOException {
        ProxyAndInfo<StorageProtocol> info = NameNodeProxiesClient.createProxyWithLossyRetryHandler(
                configuration(), CLUSTER, StorageProtocol.class, 1, new AtomicBoolean());
        try {
            assertEquals("ha-hdfs:test-cluster", info.getDelegationTokenService().toString());
            assertArrayEquals(new long[]{300, 20, 280}, info.getProxy().getStats());
        } finally {
            RPC.stopProxy(info.getProxy());
        }
    }

    public interface StorageProtocol {
        @Idempotent
        long[] getStats();
    }

    /// An application-supplied provider for an in-process logical service.
    public static class InProcessProvider extends AbstractNNFailoverProxyProvider<StorageProtocol> {
        private final Endpoint endpoint = new Endpoint();
        private final ProxyInfo<StorageProtocol> proxy = new ProxyInfo<>(endpoint, "in-process");

        public InProcessProvider(Configuration conf, URI uri, Class<StorageProtocol> type,
                HAProxyFactory<StorageProtocol> factory) {
            xface = type;
        }

        @Override
        public ProxyInfo<StorageProtocol> getProxy() {
            return proxy;
        }

        @Override
        public void performFailover(StorageProtocol current) {
            // The in-process service remains available across retry attempts.
        }

        @Override
        public boolean useLogicalURI() {
            return true;
        }

        @Override
        public void close() {
            endpoint.close();
        }
    }

    public static class Endpoint implements StorageProtocol, Closeable {
        private final AtomicInteger reads = new AtomicInteger();
        private boolean closed;

        @Override
        public long[] getStats() {
            reads.incrementAndGet();
            return new long[]{300, 20, 280};
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
