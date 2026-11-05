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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class ObserverReadProxyProviderTest {
    @Test
    public void constructsLogicalProxyAndRoutesWritesToActive() throws Exception {
        HaCluster cluster = new HaCluster(true);
        try (ObserverReadProxyProvider<ClientProtocol> provider = new ObserverReadProxyProvider<>(
                cluster.conf, HaCluster.CLUSTER_URI, ClientProtocol.class, cluster)) {
            assertTrue(provider.useLogicalURI());
            assertSame(provider.getAlignmentContext(), cluster.alignmentContext);
            assertTrue(provider.getProxy().proxy.mkdirs("/reports", FsPermission.getDirDefault(), true));
            assertEquals("/reports", cluster.first.createdPath);
            assertEquals(1, cluster.first.writes.get());
            assertEquals(0, cluster.second.writes.get());
        }
        assertTrue(cluster.first.closed);
    }
}
