/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_framework.ensemble;

import org.apache.curator.ensemble.EnsembleProvider;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.BaseClassForTests;
import org.apache.curator.test.TestingServer;
import org.apache.curator.test.Timing;
import org.apache.curator.utils.CloseableUtils;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestEnsembleProvider extends BaseClassForTests {
    private final Timing timing = new Timing();

    @Test
    public void testBasic() {
        Semaphore counter = new Semaphore(0);
        final CuratorFramework client = newClient(counter);
        try {
            client.start();
            assertTrue(timing.acquireSemaphore(counter));
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testAfterSessionExpiration() throws Exception {
        TestingServer oldServer = server;
        Semaphore counter = new Semaphore(0);
        final CuratorFramework client = newClient(counter);
        try {
            final CountDownLatch connectedLatch = new CountDownLatch(1);
            final CountDownLatch lostLatch = new CountDownLatch(1);
            final CountDownLatch reconnectedLatch = new CountDownLatch(1);
            ConnectionStateListener listener = (client1, newState) -> {
                if (newState == ConnectionState.CONNECTED) {
                    connectedLatch.countDown();
                }
                if (newState == ConnectionState.LOST) {
                    lostLatch.countDown();
                }
                if (newState == ConnectionState.RECONNECTED) {
                    reconnectedLatch.countDown();
                }
            };
            client.getConnectionStateListenable().addListener(listener);
            client.start();
            assertTrue(timing.awaitLatch(connectedLatch));
            server.stop();
            assertTrue(timing.awaitLatch(lostLatch));
            counter.drainPermits();
            IntStream.range(0, 5).forEach(i -> assertTrue(timing.acquireSemaphore(counter), "Failed when i is: " + i));
            server = new TestingServer();
            assertTrue(timing.awaitLatch(reconnectedLatch));
        } finally {
            CloseableUtils.closeQuietly(client);
            CloseableUtils.closeQuietly(oldServer);
        }
    }

    private CuratorFramework newClient(Semaphore counter) {
        return CuratorFrameworkFactory.builder()
                .ensembleProvider(new CountingEnsembleProvider(counter))
                .sessionTimeoutMs(timing.session())
                .connectionTimeoutMs(timing.connection())
                .retryPolicy(new RetryOneTime(1))
                .build();
    }

    private class CountingEnsembleProvider implements EnsembleProvider {
        private final Semaphore getConnectionStringCounter;

        CountingEnsembleProvider(Semaphore getConnectionStringCounter) {
            this.getConnectionStringCounter = getConnectionStringCounter;
        }

        @Override
        public void start() {
        }

        @Override
        public String getConnectionString() {
            getConnectionStringCounter.release();
            return server.getConnectString();
        }

        @Override
        public void close() {
        }

        @Override
        public void setConnectionString(String connectionString) {
        }

        @Override
        public boolean updateServerListEnabled() {
            return false;
        }
    }
}
