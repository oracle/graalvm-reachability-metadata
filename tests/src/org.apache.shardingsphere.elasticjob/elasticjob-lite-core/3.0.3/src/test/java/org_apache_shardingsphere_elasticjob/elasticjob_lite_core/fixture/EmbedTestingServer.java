/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_shardingsphere_elasticjob.elasticjob_lite_core.fixture;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;
import org.apache.shardingsphere.elasticjob.reg.exception.RegExceptionHandler;
import org.awaitility.Awaitility;

import java.io.IOException;
import java.time.Duration;

public final class EmbedTestingServer {
    private static final int PORT = 7181;
    private static volatile TestingServer testingServer;

    private EmbedTestingServer() {
    }

    /**
     * Get the connection string.
     *
     * @return connection string
     */
    public static String getConnectionString() {
        return "localhost:" + PORT;
    }

    /**
     * Start the server.
     */
    public static void start() {
        if (null != testingServer) {
            return;
        }
        try {
            testingServer = new TestingServer(PORT, true);
            Awaitility.await().atMost(Duration.ofMinutes(1)).ignoreExceptions().until(() -> {
                CuratorFramework client = CuratorFrameworkFactory.builder()
                        .connectString(getConnectionString())
                        .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                        .build();
                client.start();
                client.close();
                return true;
            });
        } catch (final Exception ex) {
            RegExceptionHandler.handleException(ex);
        } finally {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    testingServer.close();
                } catch (final IOException ex) {
                    RegExceptionHandler.handleException(ex);
                }
            }));
        }
    }
}
