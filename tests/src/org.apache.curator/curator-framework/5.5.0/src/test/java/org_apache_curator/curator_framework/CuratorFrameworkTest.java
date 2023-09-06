/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_framework;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.transaction.CuratorOp;
import org.apache.curator.framework.api.transaction.CuratorTransactionResult;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CuratorFrameworkTest {

    @BeforeAll
    static void beforeAll() {
        EmbedTestingServer.start();
    }

    @Test
    void testCreateClient() {
        try (CuratorFramework client = CuratorFrameworkFactory.newClient(EmbedTestingServer.getConnectString(),
                new ExponentialBackoffRetry(1000, 3))) {
            client.start();
            assertDoesNotThrow(() -> client.blockUntilConnected());
        }
        try (CuratorFramework client = getCuratorFramework()) {
            client.start();
            assertDoesNotThrow(() -> client.blockUntilConnected());
        }
    }

    @Test
    void testCRUD() {
        try (CuratorFramework client = getCuratorFramework()) {
            client.start();
            assertDoesNotThrow(() -> client.blockUntilConnected());
            client.create().forPath("/testCreate", new byte[0]);
            client.create().withMode(CreateMode.EPHEMERAL).forPath("/testCreateEphemeral", new byte[0]);
            client.create().withProtection().withMode(CreateMode.EPHEMERAL_SEQUENTIAL).forPath("/testCreateEphemeralSequential", new byte[0]);
            client.create().idempotent().forPath("/testCreateIdempotent", new byte[0]);
            assertThat(client.getChildren().watched().forPath("/testCreate")).isEmpty();
            assertThat(client.getChildren().watched().forPath("/testCreateEphemeral")).isEmpty();
            assertThrows(KeeperException.NoNodeException.class, () -> client.getChildren().watched().forPath("/testCreateEphemeralSequential"));
            assertThat(client.getChildren().watched().forPath("/testCreateIdempotent")).isEmpty();
            client.setData().forPath("/testCreate", "firstTestWord".getBytes(StandardCharsets.UTF_8));
            assertThat(client.getChildren().watched().forPath("/testCreate")).isEmpty();
            client.getCuratorListenable().addListener((client1, event) -> {
            });
            client.setData().inBackground().forPath("/testCreate", "secondTestWord".getBytes(StandardCharsets.UTF_8));
            assertThat(client.getChildren().watched().forPath("/testCreate")).isEmpty();
            client.setData().idempotent().withVersion(2).forPath("/testCreate", "thirdTestWord".getBytes(StandardCharsets.UTF_8));
            assertThat(client.getChildren().watched().forPath("/testCreate")).isEmpty();
            client.delete().forPath("/testCreate");
            assertThrows(KeeperException.NoNodeException.class, () -> client.getChildren().watched().forPath("/testCreate"));
            client.delete().guaranteed().forPath("/testCreateEphemeral");
            assertThrows(KeeperException.NoNodeException.class, () -> client.getChildren().watched().forPath("/testCreateEphemeral"));
            client.delete().idempotent().withVersion(0).forPath("/testCreateIdempotent");
            assertThrows(KeeperException.NoNodeException.class, () -> client.getChildren().watched().forPath("/testCreateIdempotent"));
            client.create().forPath("/deleteIdempotentFirst", new byte[0]);
            client.create().forPath("/deleteIdempotentSecond", new byte[0]);
            client.create().forPath("/deleteIdempotentThird", new byte[0]);
            client.delete().idempotent().forPath("/deleteIdempotentFirst");
            client.delete().quietly().withVersion(0).forPath("/deleteIdempotentSecond");
            client.delete().quietly().forPath("/deleteIdempotentThird");
            assertThrows(KeeperException.NoNodeException.class, () -> client.getChildren().watched().forPath("/deleteIdempotentFirst"));
            assertThrows(KeeperException.NoNodeException.class, () -> client.getChildren().watched().forPath("/deleteIdempotentSecond"));
            assertThrows(KeeperException.NoNodeException.class, () -> client.getChildren().watched().forPath("/deleteIdempotentThird"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testTransaction() {
        try (CuratorFramework client = getCuratorFramework()) {
            client.start();
            assertDoesNotThrow(() -> client.blockUntilConnected());
            client.create().forPath("/testFirst", new byte[0]);
            client.create().forPath("/testSecond", new byte[0]);
            client.create().forPath("/testSecond/path", new byte[0]);
            client.create().forPath("/testThird", new byte[0]);
            client.create().forPath("/testThird/testSagittarius", new byte[0]);
            client.create().forPath("/testThird/testSagittarius/path", new byte[0]);
            CuratorOp createOp = client.transactionOp().create().forPath("/testFirst/path", "some data".getBytes());
            CuratorOp setDataOp = client.transactionOp().setData().forPath("/testSecond/path", "other data".getBytes());
            CuratorOp deleteOp = client.transactionOp().delete().forPath("/testThird/testSagittarius/path");
            List<CuratorTransactionResult> results = client.transaction().forOperations(createOp, setDataOp, deleteOp);
            assertThat(results.get(0).getForPath()).isEqualTo("/testFirst/path");
            assertThat(results.get(1).getForPath()).isEqualTo("/testSecond/path");
            assertThat(results.get(2).getForPath()).isEqualTo("/testThird/testSagittarius/path");
            assertThat(results.get(0).getForPath()).isEqualTo("/testFirst/path");
            assertThat(results.get(1).getForPath()).isEqualTo("/testSecond/path");
            assertThat(results.get(2).getForPath()).isEqualTo("/testThird/testSagittarius/path");
            for (CuratorTransactionResult result : results) {
                System.out.println(result.getForPath() + " - " + result.getType());

            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private CuratorFramework getCuratorFramework() {
        return CuratorFrameworkFactory.builder()
                .connectString(EmbedTestingServer.getConnectString())
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .connectionTimeoutMs(10000)
                .sessionTimeoutMs(10000)
                .build();
    }
}
