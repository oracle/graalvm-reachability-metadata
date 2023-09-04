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
import org.apache.curator.ensemble.fixed.FixedEnsembleProvider;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.retry.RetryNTimes;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.retry.RetryUntilElapsed;
import org.apache.curator.utils.DefaultZookeeperFactory;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class CuratorClientTest {

    @BeforeAll
    static void beforeAll() {
        EmbedTestingServer.start();
    }

    @Test
    void testConstructor() throws Exception {
        try (CuratorZookeeperClient client = getCuratorZookeeperClient()) {
            client.start();
            client.blockUntilConnectedOrTimedOut();
            String path = client.getZooKeeper().create("/testConstructor", new byte[]{1, 2, 3}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            assertEquals(path, "/testConstructor");
        }
    }

    @Test
    void testGetZooKeeper() throws Exception {
        try (CuratorZookeeperClient client = getCuratorZookeeperClient()) {
            client.start();
            assertThat(client.getZooKeeper()).isNotNull();
        }
    }

    @Test
    void testIsConnected() throws Exception {
        try (CuratorZookeeperClient client = getCuratorZookeeperClient()) {
            client.start();
            Awaitility.await()
                    .atMost(Duration.ofMillis(4000))
                    .untilAsserted(() -> assertTrue(client.isConnected()));
        }
    }

    @Test
    void testBlockUntilConnectedOrTimedOut() throws Exception {
        try (CuratorZookeeperClient client = getCuratorZookeeperClient()) {
            client.start();
            client.blockUntilConnectedOrTimedOut();
            String path = client.getZooKeeper().create("/testBlockUntilConnectedOrTimedOut", new byte[]{1, 2, 3}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            assertEquals(path, "/testBlockUntilConnectedOrTimedOut");
        }
    }

    @Test
    void testClose() {
        assertDoesNotThrow(() -> {
            CuratorZookeeperClient client = getCuratorZookeeperClient();
            client.start();
            client.blockUntilConnectedOrTimedOut();
            client.close();
        });
    }

    @Test
    void testSetRetryPolicy() throws Exception {
        try (CuratorZookeeperClient client = getCuratorZookeeperClient()) {
            client.start();
            client.blockUntilConnectedOrTimedOut();
            RetryPolicy originRetryPolicy = client.getRetryPolicy();
            client.setRetryPolicy(new ExponentialBackoffRetry(1, 1));
            client.setRetryPolicy(new RetryNTimes(1, 1));
            client.setRetryPolicy(new RetryOneTime(1));
            client.setRetryPolicy(new RetryUntilElapsed(1, 1));
            client.setRetryPolicy(originRetryPolicy);
            String path = client.getZooKeeper().create("/testSetRetryPolicy", new byte[]{1, 2, 3}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            assertEquals(path, "/testSetRetryPolicy");
        }
    }

    @Test
    void testNewRetryLoop() throws Exception {
        try (CuratorZookeeperClient client = getCuratorZookeeperClient()) {
            client.start();
            int loopCount = 0;
            RetryLoop retryLoop = client.newRetryLoop();
            while (retryLoop.shouldContinue()) {
                ++loopCount;
                if (loopCount > 2) {
                    fail();
                    break;
                }
                try {
                    client.getZooKeeper().create("/testNewRetryLoop", new byte[]{1, 2, 3}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                    retryLoop.markComplete();
                } catch (Exception e) {
                    retryLoop.takeException(e);
                }
            }
            assertTrue(loopCount > 0);
        }
    }

    private CuratorZookeeperClient getCuratorZookeeperClient() {
        return new CuratorZookeeperClient(new DefaultZookeeperFactory(),
                new FixedEnsembleProvider(EmbedTestingServer.getConnectString()),
                10000, 10000, null, new RetryOneTime(1), false);
    }
}
