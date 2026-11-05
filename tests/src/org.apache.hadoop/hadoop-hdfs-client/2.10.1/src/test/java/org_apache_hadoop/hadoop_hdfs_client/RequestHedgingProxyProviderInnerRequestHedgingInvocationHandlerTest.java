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
public class RequestHedgingProxyProviderInnerRequestHedgingInvocationHandlerTest {
    @Test
    public void routesSingleEndpointRequestsWithoutHedging() throws Exception {
        HaCluster cluster = new HaCluster(false);
        try (RequestHedgingProxyProvider<ClientProtocol> provider = new RequestHedgingProxyProvider<>(
                cluster.conf, HaCluster.CLUSTER_URI, ClientProtocol.class, cluster)) {
            ClientProtocol client = provider.getProxy().proxy;
            assertArrayEquals(new long[]{1000, 100, 900}, client.getStats());
            assertArrayEquals(new long[]{1000, 100, 900}, client.getStats());
            assertEquals(2, cluster.first.reads.get());
            assertSame(cluster.first, provider.getProxy().proxy);
        }
        assertTrue(cluster.first.closed);
    }
}
