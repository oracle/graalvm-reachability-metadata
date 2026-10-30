/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_zookeeper.zookeeper;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.server.ServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class ServerCnxnFactoryTest {

    @TempDir
    Path directory;

    @Test
    @Timeout(120)
    void defaultFactoryServesClientRequests() throws Exception {
        Path snapshots = Files.createDirectory(directory.resolve("snapshots"));
        Path transactions = Files.createDirectory(directory.resolve("transactions"));
        ZooKeeperServer server = new ZooKeeperServer(snapshots.toFile(), transactions.toFile(), 2000);
        ServerCnxnFactory factory = ServerCnxnFactory.createFactory();
        try {
            factory.configure(new InetSocketAddress("127.0.0.1", 0), 10);
            factory.startup(server);
            assertThat(factory.getLocalPort()).isPositive();
            assertThat(factory.getMaxClientCnxnsPerHost()).isEqualTo(10);

            CountDownLatch connected = new CountDownLatch(1);
            String address = "127.0.0.1:" + factory.getLocalPort();
            try (ZooKeeper client = new ZooKeeper(address, 30000, event -> {
                if (event.getState() == Watcher.Event.KeeperState.SyncConnected) {
                    connected.countDown();
                }
            })) {
                assertThat(connected.await(30, TimeUnit.SECONDS)).isTrue();
                byte[] payload = "factory-created-server".getBytes(UTF_8);
                assertThat(client.create("/factory", payload, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT))
                        .isEqualTo("/factory");
                assertThat(client.getData("/factory", false, null)).isEqualTo(payload);
                client.delete("/factory", -1);
                assertThat(client.exists("/factory", false)).isNull();
            }
        } finally {
            factory.shutdown();
            server.shutdown();
        }
    }
}
