/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_zookeeper.zookeeper;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.StatsTrack;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.cli.ListQuotaCommand;
import org.apache.zookeeper.cli.SetQuotaCommand;
import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/// Exercises ZooKeeper's subtree quota accounting for tenant capacity monitoring.
/// An operator installs soft budgets on an existing configuration tree, detects
/// excess usage, and verifies that shrinking and deleting records reclaim capacity.
public class TenantQuotaAccountingTest {

    @TempDir
    Path storage;

    @Test
    @Timeout(60)
    public void tracksTenantUsageAndClearsBudgetWarningsAfterCleanup() throws Exception {
        Path snapshots = Files.createDirectory(storage.resolve("snapshots"));
        Path transactions = Files.createDirectory(storage.resolve("transactions"));
        ZooKeeperServer server = new ZooKeeperServer(snapshots.toFile(), transactions.toFile(), 2000);
        NIOServerCnxnFactory listener = new NIOServerCnxnFactory();
        try {
            listener.configure(new InetSocketAddress("127.0.0.1", 0), 10);
            listener.startup(server);
            CountDownLatch connected = new CountDownLatch(1);
            try (ZooKeeper operator = new ZooKeeper("127.0.0.1:" + listener.getLocalPort(), 10000, event -> {
                if (event.getState() == KeeperState.SyncConnected) {
                    connected.countDown();
                }
            })) {
                assertTrue(connected.await(10, TimeUnit.SECONDS), "Capacity operator connects");
                String tenant = "/tenants/payments";
                byte[] owner = bytes("owner=payments");
                byte[] configuration = bytes("mode=live");
                byte[] endpoint = bytes("https://payments.internal/api");
                operator.create("/tenants", new byte[0],
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                operator.create(tenant, owner, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                operator.create(tenant + "/config", configuration,
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                operator.create("/tenants/analytics", new byte[128],
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

                StatsTrack budget = new StatsTrack();
                budget.setCount(2);
                budget.setBytes(40);
                SetQuotaCommand.createQuota(operator, tenant, budget);

                QuotaReport initial = readQuota(operator, tenant);
                assertEquals(2, initial.limit().getCount());
                assertEquals(40, initial.limit().getBytes());
                assertEquals(2, initial.usage().getCount(),
                        "Installing a quota counts existing nodes, including the tenant root");
                assertEquals(owner.length + configuration.length, initial.usage().getBytes(),
                        "A sibling tenant's payload does not consume this tenant's budget");
                assertFalse(initial.overBudget());

                // Soft quotas report excess usage without rejecting application writes.
                operator.create(tenant + "/config/endpoint", endpoint,
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                QuotaReport expanded = readQuota(operator, tenant);
                assertEquals(3, expanded.usage().getCount());
                assertEquals(owner.length + configuration.length + endpoint.length,
                        expanded.usage().getBytes());
                assertTrue(expanded.usage().getCount() > expanded.limit().getCount());
                assertTrue(expanded.usage().getBytes() > expanded.limit().getBytes());
                assertTrue(expanded.overBudget(), "The operator should warn about tenant growth");
                assertArrayEquals(endpoint, operator.getData(tenant + "/config/endpoint", false, null));

                byte[] compactEndpoint = bytes("local");
                operator.setData(tenant + "/config/endpoint", compactEndpoint, 0);
                QuotaReport compacted = readQuota(operator, tenant);
                assertEquals(3, compacted.usage().getCount());
                assertEquals(owner.length + configuration.length + compactEndpoint.length,
                        compacted.usage().getBytes());
                assertTrue(compacted.usage().getBytes() < compacted.limit().getBytes());
                assertTrue(compacted.overBudget(), "Shrinking data does not fix the node-count warning");

                operator.delete(tenant + "/config/endpoint", 1);
                QuotaReport reclaimed = readQuota(operator, tenant);
                assertEquals(initial.usage().getCount(), reclaimed.usage().getCount());
                assertEquals(initial.usage().getBytes(), reclaimed.usage().getBytes());
                assertEquals(initial.limit().getCount(), reclaimed.limit().getCount());
                assertEquals(initial.limit().getBytes(), reclaimed.limit().getBytes());
                assertFalse(reclaimed.overBudget(), "Deleting the extra record clears the capacity warning");
                assertArrayEquals(configuration, operator.getData(tenant + "/config", false, null));
            }
        } finally {
            listener.shutdown();
            server.shutdown();
            server.getTxnLogFactory().close();
        }
    }

    private static QuotaReport readQuota(ZooKeeper operator, String tenant) throws Exception {
        List<StatsTrack> quota = ListQuotaCommand.listQuota(operator, tenant);
        return new QuotaReport(quota.get(0), quota.get(1));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private record QuotaReport(StatsTrack limit, StatsTrack usage) {
        private boolean overBudget() {
            return usage.getCount() > limit.getCount() || usage.getBytes() > limit.getBytes();
        }
    }
}
