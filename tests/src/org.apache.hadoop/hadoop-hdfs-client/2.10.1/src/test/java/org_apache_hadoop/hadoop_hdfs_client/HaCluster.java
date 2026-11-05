/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_hdfs_client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.ha.HAServiceProtocol.HAServiceState;
import org.apache.hadoop.hdfs.protocol.ClientProtocol;
import org.apache.hadoop.hdfs.protocolPB.ClientNamenodeProtocolTranslatorPB;
import org.apache.hadoop.hdfs.server.namenode.ha.HAProxyFactory;
import org.apache.hadoop.ipc.AlignmentContext;
import org.apache.hadoop.ipc.RemoteException;
import org.apache.hadoop.ipc.StandbyException;
import org.apache.hadoop.security.UserGroupInformation;

/// In-process endpoints supplied through the provider's public factory interface.
final class HaCluster implements HAProxyFactory<ClientProtocol> {
    static final URI CLUSTER_URI = URI.create("hdfs://coverage-cluster");
    final Configuration conf = new Configuration(false);
    final Endpoint first = new Endpoint(HAServiceState.ACTIVE, 1000);
    final Endpoint second = new Endpoint(HAServiceState.OBSERVER, 2000);
    AlignmentContext alignmentContext;

    HaCluster(boolean twoNodes) {
        conf.set("dfs.nameservices", CLUSTER_URI.getHost());
        conf.set("dfs.ha.namenodes.coverage-cluster", twoNodes ? "first,second" : "first");
        conf.set("dfs.namenode.rpc-address.coverage-cluster.first", "127.0.0.1:19001");
        if (twoNodes) {
            conf.set("dfs.namenode.rpc-address.coverage-cluster.second", "127.0.0.1:19002");
        }
        conf.setBoolean("dfs.client.failover.random.order", false);
    }

    void hedgeBothNodes() {
        CountDownLatch calls = new CountDownLatch(2);
        first.concurrentCalls = calls;
        second.concurrentCalls = calls;
        second.state = HAServiceState.STANDBY;
    }

    @Override
    public ClientProtocol createProxy(Configuration config, InetSocketAddress address,
            Class<ClientProtocol> type, UserGroupInformation user, boolean retries,
            AtomicBoolean fallback) {
        return address.getPort() == 19001 ? first : second;
    }

    @Override
    public ClientProtocol createProxy(Configuration config, InetSocketAddress address,
            Class<ClientProtocol> type, UserGroupInformation user, boolean retries) {
        return createProxy(config, address, type, user, retries, null);
    }

    @Override
    public void setAlignmentContext(AlignmentContext context) {
        alignmentContext = context;
    }

    /// Only the protocol operations used by these routing scenarios are local.
    public static class Endpoint extends ClientNamenodeProtocolTranslatorPB {
        final AtomicInteger reads = new AtomicInteger();
        final AtomicInteger syncs = new AtomicInteger();
        final AtomicInteger writes = new AtomicInteger();
        private final long capacity;
        HAServiceState state;
        CountDownLatch concurrentCalls;
        String createdPath;
        boolean closed;

        Endpoint(HAServiceState serviceState, long total) {
            super(null);
            state = serviceState;
            capacity = total;
        }

        @Override
        public long[] getStats() throws IOException {
            reads.incrementAndGet();
            if (concurrentCalls != null) {
                concurrentCalls.countDown();
                try {
                    if (!concurrentCalls.await(10, TimeUnit.SECONDS)) {
                        throw new IOException("The other hedged request did not arrive");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Hedged request interrupted", e);
                }
            }
            if (state == HAServiceState.STANDBY) {
                throw new RemoteException(StandbyException.class.getName(), "Standby cannot serve reads");
            }
            return new long[]{capacity, 100, capacity - 100};
        }

        @Override
        public HAServiceState getHAServiceState() {
            return state;
        }

        @Override
        public void msync() {
            syncs.incrementAndGet();
        }

        @Override
        public boolean mkdirs(String path, FsPermission permission, boolean parents) {
            writes.incrementAndGet();
            createdPath = path;
            return true;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
