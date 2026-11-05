/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_hdfs_client;

import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.hdfs.protocol.ClientProtocol;
import org.apache.hadoop.hdfs.server.namenode.ha.ObserverReadProxyProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class ObserverReadProxyProviderInnerObserverReadInvocationHandlerTest {
    @Test
    public void synchronizesWithActiveAndReadsFromObserver() throws Exception {
        HaCluster cluster = new HaCluster(true);
        try (ObserverReadProxyProvider<ClientProtocol> provider = new ObserverReadProxyProvider<>(
                cluster.conf, HaCluster.CLUSTER_URI, ClientProtocol.class, cluster)) {
            ClientProtocol client = provider.getProxy().proxy;
            assertArrayEquals(new long[]{2000, 100, 1900}, client.getStats());
            assertArrayEquals(new long[]{2000, 100, 1900}, client.getStats());
            assertEquals(1, cluster.first.syncs.get());
            assertEquals(0, cluster.first.reads.get());
            assertEquals(2, cluster.second.reads.get());
            assertTrue(client.mkdirs("/observer-results", FsPermission.getDirDefault(), true));
            assertEquals("/observer-results", cluster.first.createdPath);
            assertEquals(0, cluster.second.writes.get());
        }
        assertTrue(cluster.first.closed);
        assertTrue(cluster.second.closed);
    }
}
