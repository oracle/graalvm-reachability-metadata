/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.curator.framework.imps;

import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.CuratorTempFramework;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.BaseClassForTests;
import org.apache.curator.utils.CloseableUtils;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestTempFramework extends BaseClassForTests {
    @Test
    public void testBasic() throws Exception {
        CuratorTempFramework client = CuratorFrameworkFactory.builder().connectString(server.getConnectString()).retryPolicy(new RetryOneTime(1)).buildTemp();
        try {
            client.inTransaction().create().forPath("/foo", "data".getBytes()).and().commit();
            byte[] bytes = client.getData().forPath("/foo");
            assertArrayEquals(bytes, "data".getBytes());
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testInactivity() throws Exception {
        final CuratorTempFrameworkImpl client = (CuratorTempFrameworkImpl) CuratorFrameworkFactory.builder().connectString(server.getConnectString()).retryPolicy(new RetryOneTime(1)).buildTemp(1, TimeUnit.SECONDS);
        try {
            ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
            Runnable command = client::updateLastAccess;
            service.scheduleAtFixedRate(command, 10, 10, TimeUnit.MILLISECONDS);
            client.inTransaction().create().forPath("/foo", "data".getBytes()).and().commit();
            service.shutdownNow();
            Thread.sleep(2000);
            assertNull(client.getCleanup());
            assertNull(client.getClient());
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }
}
