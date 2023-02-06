/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_framework.imps;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.BaseClassForTests;
import org.apache.curator.test.InstanceSpec;
import org.apache.curator.test.TestingCluster;
import org.apache.curator.test.Timing;
import org.apache.curator.utils.CloseableUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestReadOnly extends BaseClassForTests {
    @BeforeEach
    public void setup() {
        System.setProperty("readonlymode.enabled", "true");
    }

    @AfterEach
    public void tearDown() {
        System.setProperty("readonlymode.enabled", "false");
    }

    @Test
    public void testReadOnly() throws Exception {
        Timing timing = new Timing();
        CuratorFramework client = null;
        TestingCluster cluster = createAndStartCluster(2);
        try {
            client = CuratorFrameworkFactory.builder().connectString(cluster.getConnectString()).canBeReadOnly(true).connectionTimeoutMs(timing.connection()).sessionTimeoutMs(timing.session()).retryPolicy(new ExponentialBackoffRetry(100, 3)).build();
            client.start();
            client.create().forPath("/test");
            final CountDownLatch readOnlyLatch = new CountDownLatch(1);
            final CountDownLatch reconnectedLatch = new CountDownLatch(1);
            ConnectionStateListener listener = (client1, newState) -> {
                switch (newState) {
                    case READ_ONLY -> readOnlyLatch.countDown();
                    case RECONNECTED -> reconnectedLatch.countDown();
                }
            };
            client.getConnectionStateListenable().addListener(listener);
            InstanceSpec ourInstance = cluster.findConnectionInstance(client.getZookeeperClient().getZooKeeper());
            Iterator<InstanceSpec> iterator = cluster.getInstances().iterator();
            InstanceSpec killInstance = iterator.next();
            if (killInstance.equals(ourInstance)) {
                killInstance = iterator.next();
            }
            cluster.killServer(killInstance);
            assertEquals(reconnectedLatch.getCount(), 1);
            assertTrue(timing.awaitLatch(readOnlyLatch));
            assertEquals(reconnectedLatch.getCount(), 1);
            cluster.restartServer(killInstance);
            assertTrue(timing.awaitLatch(reconnectedLatch));
        } finally {
            CloseableUtils.closeQuietly(client);
            CloseableUtils.closeQuietly(cluster);
        }
    }

    @Override
    protected void createServer() {
    }
}
