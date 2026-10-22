/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_zookeeper.zookeeper;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/// Exercises ZooKeeper ephemeral-sequential leader election and session-close handoff.
/// Two scheduler instances join an election, and the standby watches its predecessor
/// so that closing the leader's session promotes the remaining scheduler.
public class SequentialLeaderHandoffTest {

    @TempDir
    Path storage;

    @Test
    @Timeout(60)
    public void promotesStandbyWhenLeaderClosesItsSession() throws Exception {
        Path snapshots = Files.createDirectory(storage.resolve("snapshots"));
        Path transactions = Files.createDirectory(storage.resolve("transactions"));
        ZooKeeperServer server = new ZooKeeperServer(snapshots.toFile(), transactions.toFile(), 2000);
        NIOServerCnxnFactory listener = new NIOServerCnxnFactory();
        try {
            listener.configure(new InetSocketAddress("127.0.0.1", 0), 10);
            listener.startup(server);
            String address = "127.0.0.1:" + listener.getLocalPort();
            CountDownLatch standbyConnected = new CountDownLatch(1);
            try (ZooKeeper standby = new ZooKeeper(address, 10000, event -> {
                if (event.getState() == KeeperState.SyncConnected) {
                    standbyConnected.countDown();
                }
            })) {
                assertTrue(standbyConnected.await(10, TimeUnit.SECONDS), "Standby connects");
                standby.create("/scheduler-election", new byte[0],
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                CountDownLatch leaderConnected = new CountDownLatch(1);
                CountDownLatch predecessorRemoved = new CountDownLatch(1);
                String leaderPath;
                String standbyPath;
                byte[] leaderEndpoint = "scheduler-a.internal:8080".getBytes(StandardCharsets.UTF_8);
                byte[] standbyEndpoint = "scheduler-b.internal:8080".getBytes(StandardCharsets.UTF_8);
                try (ZooKeeper leader = new ZooKeeper(address, 10000, event -> {
                    if (event.getState() == KeeperState.SyncConnected) {
                        leaderConnected.countDown();
                    }
                })) {
                    assertTrue(leaderConnected.await(10, TimeUnit.SECONDS), "Leader connects");
                    leaderPath = leader.create("/scheduler-election/candidate-", leaderEndpoint,
                            ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
                    standbyPath = standby.create("/scheduler-election/candidate-", standbyEndpoint,
                            ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);

                    assertTrue(leaderPath.compareTo(standbyPath) < 0,
                            "The earlier contender has the lower election sequence");
                    assertEquals(2, standby.getChildren("/scheduler-election", false).size());
                    assertEquals(leaderPath, electedCandidate(standby));
                    Stat incumbent = standby.exists(leaderPath, event -> {
                        if (event.getType() == EventType.NodeDeleted && leaderPath.equals(event.getPath())) {
                            predecessorRemoved.countDown();
                        }
                    });
                    assertNotNull(incumbent, "The standby watches its live predecessor");
                    assertEquals(leader.getSessionId(), incumbent.getEphemeralOwner());
                    assertArrayEquals(leaderEndpoint,
                            standby.getData(electedCandidate(standby), false, null));
                }

                assertTrue(predecessorRemoved.await(10, TimeUnit.SECONDS),
                        "Closing the leader session notifies the standby of its predecessor's removal");
                assertNull(standby.exists(leaderPath, false));
                assertEquals(standbyPath, electedCandidate(standby));
                assertEquals(List.of(standbyPath.substring(standbyPath.lastIndexOf('/') + 1)),
                        standby.getChildren("/scheduler-election", false));
                Stat promoted = new Stat();
                assertArrayEquals(standbyEndpoint, standby.getData(electedCandidate(standby), false, promoted));
                assertEquals(standby.getSessionId(), promoted.getEphemeralOwner());
            }
        } finally {
            listener.shutdown();
            server.shutdown();
        }
    }

    private static String electedCandidate(ZooKeeper client) throws KeeperException, InterruptedException {
        List<String> candidates = client.getChildren("/scheduler-election", false).stream().sorted().toList();
        return "/scheduler-election/" + candidates.getFirst();
    }
}
