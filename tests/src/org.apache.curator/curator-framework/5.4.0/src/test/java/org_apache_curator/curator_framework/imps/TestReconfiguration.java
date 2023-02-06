/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_framework.imps;

import org.apache.curator.ensemble.EnsembleProvider;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.imps.EnsembleTracker;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.InstanceSpec;
import org.apache.curator.test.TestingCluster;
import org.apache.curator.test.compatibility.CuratorTestBase;
import org.apache.curator.test.compatibility.Timing2;
import org.apache.curator.utils.CloseableUtils;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.server.quorum.QuorumPeer;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig;
import org.apache.zookeeper.server.quorum.flexible.QuorumMaj;
import org.apache.zookeeper.server.quorum.flexible.QuorumVerifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SuppressWarnings({"SameParameterValue", "unused"})
public class TestReconfiguration extends CuratorTestBase {
    private final Timing2 timing = new Timing2();
    private TestingCluster cluster;
    private EnsembleProvider ensembleProvider;
    private static final String superUserPasswordDigest = "curator-test:zghsj3JfJqK7DbWf0RQ1BgbJH9w=";
    private static final String superUserPassword = "curator-test";

    @BeforeEach
    @Override
    public void setup() throws Exception {
        super.setup();
        QuorumPeerConfig.setReconfigEnabled(true);
        System.setProperty("zookeeper.DigestAuthenticationProvider.superDigest", superUserPasswordDigest);
        CloseableUtils.closeQuietly(server);
        cluster = createAndStartCluster(3);
    }

    @AfterEach
    @Override
    public void teardown() throws Exception {
        CloseableUtils.closeQuietly(cluster);
        ensembleProvider = null;
        System.clearProperty("zookeeper.DigestAuthenticationProvider.superDigest");
        super.teardown();
    }

    @Test
    public void testBasicGetConfig() throws Exception {
        try (CuratorFramework client = newClient()) {
            client.start();
            byte[] configData = client.getConfig().forEnsemble();
            QuorumVerifier quorumVerifier = toQuorumVerifier(configData);
            System.out.println(quorumVerifier);
            assertConfig(quorumVerifier, cluster.getInstances());
            Assertions.assertEquals(EnsembleTracker.configToConnectionString(quorumVerifier), ensembleProvider.getConnectionString());
        }
    }

    @Test
    public void testConfigToConnectionStringIPv4Normal() throws Exception {
        String config = "server.1=10.1.2.3:2888:3888:participant;10.2.3.4:2181";
        String configString = EnsembleTracker.configToConnectionString(toQuorumVerifier(config.getBytes()));
        assertEquals("10.2.3.4:2181", configString);
    }

    @Test
    public void testConfigToConnectionStringIPv6Normal() throws Exception {
        String config = "server.1=[1010:0001:0002:0003:0004:0005:0006:0007]:2888:3888:participant;[2001:db8:85a3:0:0:8a2e:370:7334]:2181";
        String configString = EnsembleTracker.configToConnectionString(toQuorumVerifier(config.getBytes()));
        assertEquals("2001:db8:85a3:0:0:8a2e:370:7334:2181", configString);
    }

    @Test
    public void testConfigToConnectionStringIPv4NoClientAddr() throws Exception {
        String config = "server.1=10.1.2.3:2888:3888:participant;2181";
        String configString = EnsembleTracker.configToConnectionString(toQuorumVerifier(config.getBytes()));
        assertEquals("10.1.2.3:2181", configString);
    }

    @Test
    public void testConfigToConnectionStringIPv4WildcardClientAddr() throws Exception {
        String config = "server.1=10.1.2.3:2888:3888:participant;0.0.0.0:2181";
        String configString = EnsembleTracker.configToConnectionString(toQuorumVerifier(config.getBytes()));
        assertEquals("10.1.2.3:2181", configString);
    }

    @Test
    public void testConfigToConnectionStringNoClientAddrOrPort() throws Exception {
        String config = "server.1=10.1.2.3:2888:3888:participant";
        String configString = EnsembleTracker.configToConnectionString(toQuorumVerifier(config.getBytes()));
        assertEquals("", configString);
    }

    @Test
    public void testHostname() throws Exception {
        String config = "server.1=localhost:2888:3888:participant;localhost:2181";
        String configString = EnsembleTracker.configToConnectionString(toQuorumVerifier(config.getBytes()));
        assertEquals("localhost:2181", configString);
    }

    @Test
    public void testIPv6Wildcard1() throws Exception {
        String config = "server.1=[2001:db8:85a3:0:0:8a2e:370:7334]:2888:3888:participant;[::]:2181";
        String configString = EnsembleTracker.configToConnectionString(toQuorumVerifier(config.getBytes()));
        assertEquals("2001:db8:85a3:0:0:8a2e:370:7334:2181", configString);
    }

    @Test
    public void testIPv6Wildcard2() throws Exception {
        String config = "server.1=[1010:0001:0002:0003:0004:0005:0006:0007]:2888:3888:participant;[::0]:2181";
        String configString = EnsembleTracker.configToConnectionString(toQuorumVerifier(config.getBytes()));
        assertEquals("1010:1:2:3:4:5:6:7:2181", configString);
    }

    @Test
    public void testMixedIPv1() throws Exception {
        String config = "server.1=10.1.2.3:2888:3888:participant;[::]:2181";
        String configString = EnsembleTracker.configToConnectionString(toQuorumVerifier(config.getBytes()));
        assertEquals("10.1.2.3:2181", configString);
    }

    @Test
    public void testMixedIPv2() throws Exception {
        String config = "server.1=[2001:db8:85a3:0:0:8a2e:370:7334]:2888:3888:participant;127.0.0.1:2181";
        String configString = EnsembleTracker.configToConnectionString(toQuorumVerifier(config.getBytes()));
        assertEquals("127.0.0.1:2181", configString);
    }

    @Override
    protected void createServer() {
    }

    private CuratorFramework newClient() {
        return newClient(cluster.getConnectString(), true);
    }

    private CuratorFramework newClient(String connectionString) {
        return newClient(connectionString, true);
    }

    private CuratorFramework newClient(String connectionString, boolean withEnsembleProvider) {
        final AtomicReference<String> connectString = new AtomicReference<>(connectionString);
        ensembleProvider = new EnsembleProvider() {
            @Override
            public void start() {
            }

            @Override
            public boolean updateServerListEnabled() {
                return false;
            }

            @Override
            public String getConnectionString() {
                return connectString.get();
            }

            @Override
            public void close() {
            }

            @Override
            public void setConnectionString(String connectionString) {
                connectString.set(connectionString);
            }
        };
        return CuratorFrameworkFactory.builder()
                .ensembleProvider(ensembleProvider)
                .ensembleTracker(withEnsembleProvider)
                .sessionTimeoutMs(timing.session())
                .connectionTimeoutMs(timing.connection())
                .authorization("digest", superUserPassword.getBytes())
                .retryPolicy(new ExponentialBackoffRetry(timing.forSleepingABit().milliseconds(), 3))
                .build();
    }

    private CountDownLatch setChangeWaiter(CuratorFramework client) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        Watcher watcher = event -> {
            if (event.getType() == Watcher.Event.EventType.NodeDataChanged) {
                latch.countDown();
            }
        };
        client.getConfig().usingWatcher(watcher).forEnsemble();
        return latch;
    }

    private void assertConfig(QuorumVerifier config, Collection<InstanceSpec> instances) {
        instances.forEach(instance -> {
            QuorumPeer.QuorumServer quorumServer = config.getAllMembers().get((long) instance.getServerId());
            assertNotNull(quorumServer, String.format("Looking for %s - found %s", instance.getServerId(), config.getAllMembers()));
            assertEquals(quorumServer.clientAddr.getPort(), instance.getPort());
        });
    }

    private List<String> toReconfigSpec(Collection<InstanceSpec> instances) {
        String localhost = new InetSocketAddress((InetAddress) null, 0).getAddress().getHostAddress();
        return instances.stream()
                .map(instance -> "server." + instance.getServerId() + "=" +
                        localhost + ":" + instance.getElectionPort() + ":" + instance.getQuorumPort() + ";" + instance.getPort())
                .collect(Collectors.toList());
    }

    private static QuorumVerifier toQuorumVerifier(byte[] bytes) throws Exception {
        assertNotNull(bytes);
        Properties properties = new Properties();
        properties.load(new ByteArrayInputStream(bytes));
        return new QuorumMaj(properties);
    }
}
