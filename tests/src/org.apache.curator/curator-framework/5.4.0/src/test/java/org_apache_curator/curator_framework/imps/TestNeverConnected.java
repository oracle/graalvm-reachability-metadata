/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_framework.imps;

import com.google.common.collect.Queues;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.Timing;
import org.apache.curator.utils.CloseableUtils;
import org.junit.jupiter.api.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestNeverConnected {
    @Test
    public void testNeverConnected() throws Exception {
        Timing timing = new Timing();
        CuratorFramework client = CuratorFrameworkFactory.newClient("localhost:1111", 100, 100, new RetryOneTime(1));
        try {
            final BlockingQueue<ConnectionState> queue = Queues.newLinkedBlockingQueue();
            ConnectionStateListener listener = (client1, state) -> queue.add(state);
            client.getConnectionStateListenable().addListener(listener);
            client.start();
            client.create().inBackground().forPath("/");
            ConnectionState polled = queue.poll(timing.forWaiting().seconds(), TimeUnit.SECONDS);
            assertEquals(polled, ConnectionState.SUSPENDED);
            polled = queue.poll(timing.forWaiting().seconds(), TimeUnit.SECONDS);
            assertEquals(polled, ConnectionState.LOST);
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }
}
