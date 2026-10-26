/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_zookeeper.zookeeper;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.ZooKeeperMain;
import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/// Exercises ZooKeeper's command-line shell subsystem for release-subtree maintenance.
/// An operator provisions release records with shell commands and recursively removes
/// a retired release without deleting the configuration an active application reads.
public class CliReleaseCleanupTest {

    @TempDir
    Path storage;

    @Test
    @Timeout(60)
    public void removesRetiredReleaseRecursivelyWhileKeepingActiveConfiguration() throws Exception {
        Path snapshots = Files.createDirectory(storage.resolve("snapshots"));
        Path transactions = Files.createDirectory(storage.resolve("transactions"));
        ZooKeeperServer server = new ZooKeeperServer(snapshots.toFile(), transactions.toFile(), 2000);
        NIOServerCnxnFactory listener = new NIOServerCnxnFactory();
        try {
            listener.configure(new InetSocketAddress("127.0.0.1", 0), 10);
            listener.startup(server);
            String address = "127.0.0.1:" + listener.getLocalPort();
            CountDownLatch operatorConnected = new CountDownLatch(1);
            CountDownLatch applicationConnected = new CountDownLatch(1);
            try (ZooKeeper operator = new ZooKeeper(address, 10000, event -> {
                if (event.getState() == KeeperState.SyncConnected) {
                    operatorConnected.countDown();
                }
            }); ZooKeeper application = new ZooKeeper(address, 10000, event -> {
                if (event.getState() == KeeperState.SyncConnected) {
                    applicationConnected.countDown();
                }
            })) {
                assertTrue(operatorConnected.await(10, TimeUnit.SECONDS), "Operator connects");
                assertTrue(applicationConnected.await(10, TimeUnit.SECONDS), "Application connects");
                ZooKeeperMain shell = new ZooKeeperMain(operator);
                shell.executeLine("create /releases registry");
                shell.executeLine("create /releases/retired image=checkout:blue");
                shell.executeLine("create /releases/retired/settings mode=legacy");
                shell.executeLine("create /releases/retired/settings/cache endpoint=cache-blue.internal");
                shell.executeLine("create /releases/active image=checkout:green");
                shell.executeLine("create /releases/active/settings mode=live");
                shell.executeLine("create /releases/active/settings/cache endpoint=cache-green.internal");

                assertEquals(List.of("active", "retired"),
                        application.getChildren("/releases", false).stream().sorted().toList());
                assertArrayEquals(bytes("endpoint=cache-blue.internal"),
                        application.getData("/releases/retired/settings/cache", false, null));
                assertArrayEquals(bytes("image=checkout:green"),
                        application.getData("/releases/active", false, null));

                // The shell's recursive command removes a nonempty, multilevel subtree.
                shell.executeLine("deleteall /releases/retired");

                assertNull(application.exists("/releases/retired/settings/cache", false));
                assertNull(application.exists("/releases/retired/settings", false));
                assertNull(application.exists("/releases/retired", false));
                assertEquals(List.of("active"), application.getChildren("/releases", false));
                assertArrayEquals(bytes("image=checkout:green"),
                        application.getData("/releases/active", false, null));
                assertArrayEquals(bytes("mode=live"),
                        application.getData("/releases/active/settings", false, null));
                assertArrayEquals(bytes("endpoint=cache-green.internal"),
                        application.getData("/releases/active/settings/cache", false, null));
            }
        } finally {
            listener.shutdown();
            server.shutdown();
            server.getTxnLogFactory().close();
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
