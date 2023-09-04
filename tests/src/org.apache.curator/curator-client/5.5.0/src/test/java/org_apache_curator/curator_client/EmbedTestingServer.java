/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import org.apache.curator.CuratorZookeeperClient;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;
import org.apache.zookeeper.KeeperException.ConnectionLossException;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.awaitility.Awaitility;

import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

public final class EmbedTestingServer {
    private static final int PORT = 3191;
    private static volatile TestingServer testingServer;
    private static final Object INIT_LOCK = new Object();

    private EmbedTestingServer() {
    }

    public static void start() {
        if (null != testingServer) {
            return;
        }
        synchronized (INIT_LOCK) {
            if (null != testingServer) {
                return;
            }
            try {
                testingServer = new TestingServer(PORT, true);
            } catch (final Exception ex) {
                if (!(ex instanceof ConnectionLossException || ex instanceof NoNodeException || ex instanceof NodeExistsException)) {
                    throw new RuntimeException(ex);
                }
            } finally {
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        testingServer.close();
                    } catch (final IOException ignored) {
                    }
                }));
            }
            try (CuratorZookeeperClient client = new CuratorZookeeperClient(getConnectString(),
                    60 * 1000, 500, null,
                    new ExponentialBackoffRetry(500, 3, 500 * 3))) {
                client.start();
                Awaitility.await()
                        .atMost(Duration.ofMillis(500 * 60))
                        .untilAsserted(() -> assertTrue(client.isConnected()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static String getConnectString() {
        return "localhost:" + PORT;
    }
}
