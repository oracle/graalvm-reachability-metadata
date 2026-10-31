/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_hdfs_client;

import org.apache.hadoop.hdfs.protocol.ClientProtocol;
import org.apache.hadoop.hdfs.server.namenode.ha.RequestHedgingProxyProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class RequestHedgingProxyProviderTest {
    @Test
    public void createsProxyAndRetainsSuccessfulEndpoint() throws Exception {
        HaCluster cluster = new HaCluster(false);
        try (RequestHedgingProxyProvider<ClientProtocol> provider = new RequestHedgingProxyProvider<>(
                cluster.conf, HaCluster.CLUSTER_URI, ClientProtocol.class, cluster)) {
            assertTrue(provider.useLogicalURI());
            assertArrayEquals(new long[]{1000, 100, 900}, provider.getProxy().proxy.getStats());
            assertSame(cluster.first, provider.getProxy().proxy);
            assertEquals(1, cluster.first.reads.get());
        }
        assertTrue(cluster.first.closed);
    }
}
