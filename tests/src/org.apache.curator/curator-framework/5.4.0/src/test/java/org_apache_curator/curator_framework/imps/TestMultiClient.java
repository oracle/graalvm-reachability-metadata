/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_framework.imps;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.CuratorEventType;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.BaseClassForTests;
import org.apache.curator.utils.CloseableUtils;
import org.apache.zookeeper.Watcher;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestMultiClient extends BaseClassForTests {
    @Test
    public void testNotify() throws Exception {
        CuratorFramework client1 = null;
        CuratorFramework client2 = null;
        try {
            client1 = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
            client2 = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
            client1.start();
            client2.start();
            final CountDownLatch latch = new CountDownLatch(1);
            client1.getCuratorListenable().addListener((client, event) -> {
                        if (event.getType() == CuratorEventType.WATCHED) {
                            if (event.getWatchedEvent().getType() == Watcher.Event.EventType.NodeDataChanged) {
                                if (event.getPath().equals("/test")) {
                                    latch.countDown();
                                }
                            }
                        }
                    }
            );
            client1.create().forPath("/test", new byte[]{1, 2, 3});
            client1.checkExists().watched().forPath("/test");
            client2.getCuratorListenable().addListener((client, event) -> {
                        if (event.getType() == CuratorEventType.SYNC) {
                            client.setData().forPath("/test", new byte[]{10, 20});
                        }
                    }
            );
            client2.sync().forPath("/test");
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } finally {
            CloseableUtils.closeQuietly(client1);
            CloseableUtils.closeQuietly(client2);
        }
    }
}
