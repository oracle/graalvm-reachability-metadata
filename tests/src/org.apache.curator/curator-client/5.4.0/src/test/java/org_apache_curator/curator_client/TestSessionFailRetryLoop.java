/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import org.apache.curator.CuratorZookeeperClient;
import org.apache.curator.RetryLoop;
import org.apache.curator.SessionFailRetryLoop;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.BaseClassForTests;
import org.apache.curator.test.Timing;
import org.apache.curator.utils.CloseableUtils;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class TestSessionFailRetryLoop extends BaseClassForTests {
    @Test
    public void testRetry() throws Exception {
        Timing timing = new Timing();
        final CuratorZookeeperClient client = new CuratorZookeeperClient(server.getConnectString(),
                timing.session(), timing.connection(), null, new ExponentialBackoffRetry(100, 3));
        SessionFailRetryLoop retryLoop = client.newSessionFailRetryLoop(SessionFailRetryLoop.Mode.RETRY);
        try (retryLoop) {
            retryLoop.start();
            client.start();
            final AtomicBoolean secondWasDone = new AtomicBoolean(false);
            final AtomicBoolean firstTime = new AtomicBoolean(true);
            while (retryLoop.shouldContinue()) {
                try {
                    RetryLoop.callWithRetry(client, (Callable<Void>) () -> {
                                if (firstTime.compareAndSet(true, false)) {
                                    assertNull(client.getZooKeeper().exists("/foo/bar", false));
                                    client.getZooKeeper().getTestable().injectSessionExpiration();
                                    client.getZooKeeper();
                                    client.blockUntilConnectedOrTimedOut();
                                }
                                assertNull(client.getZooKeeper().exists("/foo/bar", false));
                                return null;
                            }
                    );
                    RetryLoop.callWithRetry(client, (Callable<Void>) () -> {
                                assertFalse(firstTime.get());
                                assertNull(client.getZooKeeper().exists("/foo/bar", false));
                                secondWasDone.set(true);
                                return null;
                            }
                    );
                } catch (Exception e) {
                    retryLoop.takeException(e);
                }
            }
            assertTrue(secondWasDone.get());
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testRetryStatic() throws Exception {
        Timing timing = new Timing();
        final CuratorZookeeperClient client = new CuratorZookeeperClient(server.getConnectString(), timing.session(), timing.connection(), null, new ExponentialBackoffRetry(100, 3));
        SessionFailRetryLoop retryLoop = client.newSessionFailRetryLoop(SessionFailRetryLoop.Mode.RETRY);
        try (retryLoop) {
            retryLoop.start();
            client.start();
            final AtomicBoolean secondWasDone = new AtomicBoolean(false);
            final AtomicBoolean firstTime = new AtomicBoolean(true);
            SessionFailRetryLoop.callWithRetry(client, SessionFailRetryLoop.Mode.RETRY, () -> {
                        RetryLoop.callWithRetry(client, (Callable<Void>) () -> {
                                    if (firstTime.compareAndSet(true, false)) {
                                        assertNull(client.getZooKeeper().exists("/foo/bar", false));
                                        client.getZooKeeper().getTestable().injectSessionExpiration();
                                        client.getZooKeeper();
                                        client.blockUntilConnectedOrTimedOut();
                                    }
                                    assertNull(client.getZooKeeper().exists("/foo/bar", false));
                                    return null;
                                }
                        );
                        RetryLoop.callWithRetry(client, (Callable<Void>) () -> {
                            assertFalse(firstTime.get());
                            assertNull(client.getZooKeeper().exists("/foo/bar", false));
                            secondWasDone.set(true);
                            return null;
                        });
                        return null;
                    }
            );
            assertTrue(secondWasDone.get());
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testBasic() throws Exception {
        final Timing timing = new Timing();
        final CuratorZookeeperClient client = new CuratorZookeeperClient(server.getConnectString(),
                timing.session(), timing.connection(), null, new ExponentialBackoffRetry(100, 3));
        SessionFailRetryLoop retryLoop = client.newSessionFailRetryLoop(SessionFailRetryLoop.Mode.FAIL);
        try (retryLoop) {
            retryLoop.start();
            client.start();
            try {
                while (retryLoop.shouldContinue()) {
                    try {
                        RetryLoop.callWithRetry(client, (Callable<Void>) () -> {
                            assertNull(client.getZooKeeper().exists("/foo/bar", false));
                            client.getZooKeeper().getTestable().injectSessionExpiration();
                            timing.sleepABit();
                            client.getZooKeeper();
                            client.blockUntilConnectedOrTimedOut();
                            assertNull(client.getZooKeeper().exists("/foo/bar", false));
                            return null;
                        });
                    } catch (Exception e) {
                        retryLoop.takeException(e);
                    }
                }
                fail();
            } catch (SessionFailRetryLoop.SessionFailedException ignored) {
            }
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testBasicStatic() throws Exception {
        Timing timing = new Timing();
        final CuratorZookeeperClient client = new CuratorZookeeperClient(server.getConnectString(),
                timing.session(), timing.connection(), null, new ExponentialBackoffRetry(100, 3));
        SessionFailRetryLoop retryLoop = client.newSessionFailRetryLoop(SessionFailRetryLoop.Mode.FAIL);
        try (retryLoop) {
            retryLoop.start();
            client.start();
            try {
                SessionFailRetryLoop.callWithRetry(client, SessionFailRetryLoop.Mode.FAIL, () -> {
                            RetryLoop.callWithRetry(client, (Callable<Void>) () -> {
                                        assertNull(client.getZooKeeper().exists("/foo/bar", false));
                                        client.getZooKeeper().getTestable().injectSessionExpiration();
                                        client.getZooKeeper();
                                        client.blockUntilConnectedOrTimedOut();
                                        assertNull(client.getZooKeeper().exists("/foo/bar", false));
                                        return null;
                                    }
                            );
                            return null;
                        }
                );
            } catch (SessionFailRetryLoop.SessionFailedException ignored) {
            }
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }
}
