/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import org.apache.curator.CuratorZookeeperClient;
import org.apache.curator.RetryLoop;
import org.apache.curator.RetryPolicy;
import org.apache.curator.RetrySleeper;
import org.apache.curator.SessionFailedRetryPolicy;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.retry.RetryForever;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.BaseClassForTests;
import org.apache.curator.test.Timing;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TestRetryLoop extends BaseClassForTests {
    @Test
    public void testExponentialBackoffRetryLimit() {
        RetrySleeper sleeper = (time, unit) -> assertTrue(unit.toMillis(time) <= 100);
        ExponentialBackoffRetry retry = new ExponentialBackoffRetry(1, Integer.MAX_VALUE, 100);
        IntStream.iterate(0, i -> i >= 0, i -> i + 1).forEach(i -> retry.allowRetry(i, 0, sleeper));
    }

    @Test
    public void testRetryLoopWithFailure() throws Exception {
        CuratorZookeeperClient client = new CuratorZookeeperClient(server.getConnectString(), 5000, 5000,
                null, new RetryOneTime(1));
        try (client) {
            client.start();
            int loopCount = 0;
            RetryLoop retryLoop = client.newRetryLoop();
            outer:
            while (retryLoop.shouldContinue()) {
                ++loopCount;
                switch (loopCount) {
                    case 1 -> server.stop();
                    case 2 -> server.restart();
                    case 3, 4 -> {
                    }
                    default -> {
                        fail();
                        break outer;
                    }
                }
                try {
                    client.blockUntilConnectedOrTimedOut();
                    client.getZooKeeper().create("/test", new byte[]{1, 2, 3}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                    retryLoop.markComplete();
                } catch (Exception e) {
                    retryLoop.takeException(e);
                }
            }
            assertThat(loopCount).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    public void testRetryLoop() throws Exception {
        CuratorZookeeperClient client = new CuratorZookeeperClient(server.getConnectString(), 10000, 10000, null, new RetryOneTime(1));
        try (client) {
            client.start();
            int loopCount = 0;
            RetryLoop retryLoop = client.newRetryLoop();
            while (retryLoop.shouldContinue()) {
                if (++loopCount > 2) {
                    fail();
                    break;
                }
                try {
                    client.getZooKeeper().create("/test", new byte[]{1, 2, 3}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                    retryLoop.markComplete();
                } catch (Exception e) {
                    retryLoop.takeException(e);
                }
            }
            assertTrue(loopCount > 0);
        }
    }

    @Test
    public void testRetryForever() throws Exception {
        int retryIntervalMs = 1;
        RetrySleeper sleeper = mock(RetrySleeper.class);
        RetryForever retryForever = new RetryForever(retryIntervalMs);
        for (int i = 0; i < 10; i++) {
            boolean allowed = retryForever.allowRetry(i, 0, sleeper);
            assertTrue(allowed);
            verify(sleeper, times(i + 1)).sleepFor(retryIntervalMs, TimeUnit.MILLISECONDS);
        }
    }

    @Test
    public void testRetryForeverWithSessionFailed() throws Exception {
        final Timing timing = new Timing();
        final RetryPolicy retryPolicy = new SessionFailedRetryPolicy(new RetryForever(1000));
        final CuratorZookeeperClient client = new CuratorZookeeperClient(server.getConnectString(),
                timing.session(), timing.connection(), null, retryPolicy);
        try (client) {
            client.start();
            int loopCount = 0;
            final RetryLoop retryLoop = client.newRetryLoop();
            while (retryLoop.shouldContinue()) {
                if (++loopCount > 1) {
                    break;
                }
                try {
                    client.getZooKeeper().getTestable().injectSessionExpiration();
                    client.getZooKeeper().create("/test", new byte[]{1, 2, 3}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                    retryLoop.markComplete();
                } catch (Exception e) {
                    retryLoop.takeException(e);
                }
            }
            fail("Should failed with SessionExpiredException.");
        } catch (Exception e) {
            if (e instanceof KeeperException) {
                int rc = ((KeeperException) e).code().intValue();
                assertEquals(rc, KeeperException.Code.SESSIONEXPIRED.intValue());
            } else {
                throw e;
            }
        }
    }
}
