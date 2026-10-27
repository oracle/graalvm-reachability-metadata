/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_zookeeper.zookeeper;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.client.FourLetterWordMain;
import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/// Exercises ZooKeeper's four-letter-word TCP diagnostics for operator capacity monitoring.
/// An operator uses the client listener's srvr command to track a service catalog's
/// growth and retirement without enabling an HTTP administration endpoint.
public class TcpOperationalDiagnosticsTest {

    @TempDir
    Path storage;

    @Test
    @Timeout(60)
    public void observesCatalogGrowthAndRetirementThroughTheDiagnosticProtocol() throws Exception {
        Path snapshots = Files.createDirectory(storage.resolve("snapshots"));
        Path transactions = Files.createDirectory(storage.resolve("transactions"));
        ZooKeeperServer server = new ZooKeeperServer(snapshots.toFile(), transactions.toFile(), 2000);
        NIOServerCnxnFactory listener = new NIOServerCnxnFactory();
        try {
            listener.configure(new InetSocketAddress("127.0.0.1", 0), 10);
            listener.startup(server);
            int port = listener.getLocalPort();
            CountDownLatch connected = new CountDownLatch(1);
            try (ZooKeeper publisher = new ZooKeeper("127.0.0.1:" + port, 10000, event -> {
                if (event.getState() == KeeperState.SyncConnected) {
                    connected.countDown();
                }
            })) {
                assertTrue(connected.await(10, TimeUnit.SECONDS), "Catalog publisher connects");
                ServerStatus baseline = ServerStatus.read(port);
                assertEquals("standalone", baseline.mode(), "The diagnostic endpoint reports its role");

                byte[] activeEndpoint = "https://checkout.example.test:8443".getBytes(StandardCharsets.UTF_8);
                publisher.create("/service-catalog", new byte[0],
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                publisher.create("/service-catalog/checkout", activeEndpoint,
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                publisher.create("/service-catalog/legacy", "retiring".getBytes(StandardCharsets.UTF_8),
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

                ServerStatus populated = ServerStatus.read(port);
                assertEquals(baseline.nodeCount() + 3, populated.nodeCount(),
                        "The operator sees all three newly committed catalog records");
                assertEquals("standalone", populated.mode());

                publisher.delete("/service-catalog/legacy", -1);
                ServerStatus retired = ServerStatus.read(port);
                assertEquals(populated.nodeCount() - 1, retired.nodeCount(),
                        "Retiring a service reclaims one node in the reported capacity");
                assertEquals(List.of("checkout"), publisher.getChildren("/service-catalog", false));
                assertArrayEquals(activeEndpoint, publisher.getData("/service-catalog/checkout", false, null),
                        "Diagnostic requests do not disturb the catalog that applications consume");

                publisher.delete("/service-catalog/checkout", -1);
                publisher.delete("/service-catalog", -1);
                assertEquals(baseline.nodeCount(), ServerStatus.read(port).nodeCount(),
                        "Removing the catalog returns node usage to its initial level");
            }
        } finally {
            listener.shutdown();
            server.shutdown();
            server.getTxnLogFactory().close();
        }
    }

    private record ServerStatus(String mode, int nodeCount) {
        private static ServerStatus read(int port) throws Exception {
            // srvr is enabled by default and uses the same TCP port as application clients.
            String report = FourLetterWordMain.send4LetterWord("127.0.0.1", port, "srvr", false, 10000);
            return new ServerStatus(field(report, "Mode: "), Integer.parseInt(field(report, "Node count: ")));
        }

        private static String field(String report, String prefix) {
            return report.lines()
                    .filter(line -> line.startsWith(prefix))
                    .map(line -> line.substring(prefix.length()).trim())
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Missing " + prefix + " in: " + report));
        }
    }
}
