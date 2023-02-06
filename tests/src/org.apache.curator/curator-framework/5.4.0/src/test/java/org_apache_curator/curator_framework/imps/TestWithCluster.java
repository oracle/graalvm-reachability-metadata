/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_framework.imps;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.InstanceSpec;
import org.apache.curator.test.TestingCluster;
import org.apache.curator.test.Timing;
import org.apache.curator.test.compatibility.CuratorTestBase;
import org.apache.curator.utils.CloseableUtils;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestWithCluster extends CuratorTestBase {
    @Test
    public void testSplitBrain() throws Exception {
        Timing timing = new Timing();
        CuratorFramework client = null;
        TestingCluster cluster = createAndStartCluster(3);
        try {
            for (InstanceSpec instanceSpec : cluster.getInstances()) {
                client = CuratorFrameworkFactory.newClient(instanceSpec.getConnectString(), new RetryOneTime(1));
                client.start();
                client.checkExists().forPath("/");
                client.close();
                client = null;
            }
            client = CuratorFrameworkFactory.newClient(cluster.getConnectString(), timing.session(), timing.connection(), new RetryOneTime(1));
            client.start();
            final CountDownLatch latch = new CountDownLatch(2);
            client.getConnectionStateListenable().addListener((client1, newState) -> {
                        if ((newState == ConnectionState.SUSPENDED) || (newState == ConnectionState.LOST)) {
                            latch.countDown();
                        }
                    }
            );
            client.checkExists().forPath("/");
            for (InstanceSpec instanceSpec : cluster.getInstances()) {
                if (!instanceSpec.equals(cluster.findConnectionInstance(client.getZookeeperClient().getZooKeeper()))) {
                    assertTrue(cluster.killServer(instanceSpec));
                }
            }
            assertTrue(timing.awaitLatch(latch));
        } finally {
            CloseableUtils.closeQuietly(client);
            CloseableUtils.closeQuietly(cluster);
        }
    }

    @Override
    protected void createServer() {
    }
}
