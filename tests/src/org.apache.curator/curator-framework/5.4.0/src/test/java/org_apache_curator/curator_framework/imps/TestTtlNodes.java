/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_framework.imps;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.BackgroundCallback;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.Timing;
import org.apache.curator.test.compatibility.CuratorTestBase;
import org.apache.zookeeper.CreateMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestTtlNodes extends CuratorTestBase {
    @BeforeAll
    public static void setUpClass() {
        System.setProperty("zookeeper.extendedTypesEnabled", "true");
    }

    @BeforeEach
    @Override
    public void setup() throws Exception {
        System.setProperty("znode.container.checkIntervalMs", "1");
        super.setup();
    }

    @AfterEach
    @Override
    public void teardown() throws Exception {
        super.teardown();
        System.clearProperty("znode.container.checkIntervalMs");
    }

    @Test
    public void testBasic() throws Exception {
        try (CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1))) {
            client.start();
            client.create().withTtl(10).creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT_WITH_TTL).forPath("/a/b/c");
            Thread.sleep(20);
            assertNull(client.checkExists().forPath("/a/b/c"));
        }
    }

    @Test
    public void testBasicInBackground() throws Exception {
        try (CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1))) {
            client.start();
            final CountDownLatch latch = new CountDownLatch(1);
            BackgroundCallback callback = (client1, event) -> latch.countDown();
            client.create().withTtl(10).creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT_WITH_TTL).inBackground(callback).forPath("/a/b/c");
            assertTrue(new Timing().awaitLatch(latch));
            Thread.sleep(20);
            assertNull(client.checkExists().forPath("/a/b/c"));
        }
    }
}
