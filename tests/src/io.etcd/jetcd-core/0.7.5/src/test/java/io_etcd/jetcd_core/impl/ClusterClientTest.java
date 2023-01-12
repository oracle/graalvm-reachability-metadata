/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_etcd.jetcd_core.impl;

import io.etcd.jetcd.Client;
import io.etcd.jetcd.Cluster;
import io.etcd.jetcd.cluster.Member;
import io.etcd.jetcd.test.EtcdClusterExtension;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.Network;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("resource")
// `@org.junit.jupiter.api.Timeout(value = 30)` can't be used in the nativeTest GraalVM CE 22.3
public class ClusterClientTest {
    private static final Network NETWORK = Network.newNetwork();

    @RegisterExtension
    public static final EtcdClusterExtension n1 = EtcdClusterExtension.builder()
        .withNodes(1)
        .withPrefix("n1")
        .withNetwork(NETWORK)
        .build();
    @RegisterExtension
    public static final EtcdClusterExtension n2 = EtcdClusterExtension.builder()
        .withNodes(1)
        .withPrefix("n2")
        .withNetwork(NETWORK)
        .build();
    @RegisterExtension
    public static final EtcdClusterExtension n3 = EtcdClusterExtension.builder()
        .withNodes(1)
        .withPrefix("n3")
        .withNetwork(NETWORK)
        .build();

    @RegisterExtension
    public static final EtcdClusterExtension cluster = EtcdClusterExtension.builder()
        .withNodes(3)
        .withPrefix("cluster")
        .build();

    @Test
    public void testMemberList() throws ExecutionException, InterruptedException {
        try (Client client = TestUtil.client(cluster).build()) {
            assertThat(client.getClusterClient().listMember().get().getMembers()).hasSize(3);
        }
    }

    @Test
    @Disabled("io.etcd.jetcd.common.exception.EtcdException")
    public void testMemberManagement() throws ExecutionException, InterruptedException, TimeoutException {
        final Client client = Client.builder().endpoints(n1.clientEndpoints()).build();
        final Cluster clusterClient = client.getClusterClient();
        Member m2 = clusterClient.addMember(n2.peerEndpoints())
                .get(5, TimeUnit.SECONDS)
                .getMember();
        assertThat(m2).isNotNull();
        assertThat(clusterClient.listMember().get().getMembers()).hasSize(2);
    }

    @Test
    @Disabled("io.etcd.jetcd.common.exception.EtcdException")
    public void testMemberManagementAddNonLearner() throws ExecutionException, InterruptedException, TimeoutException {
        final Client client = Client.builder().endpoints(n1.clientEndpoints()).build();
        final Cluster clusterClient = client.getClusterClient();
        Member m2 = clusterClient.addMember(n2.peerEndpoints(), false).get(5, TimeUnit.SECONDS).getMember();
        assertThat(m2).isNotNull();
        assertThat(m2.isLearner()).isFalse();
        List<Member> members = clusterClient.listMember().get().getMembers();
        assertThat(members).hasSize(2);
        assertThat(members.stream().filter(Member::isLearner).findAny()).isEmpty();
    }

    @Test
    public void testMemberManagementAddLearner() throws ExecutionException, InterruptedException, TimeoutException {
        final Client client = Client.builder().endpoints(n1.clientEndpoints()).build();
        final Cluster clusterClient = client.getClusterClient();
        Member m2 = clusterClient.addMember(n2.peerEndpoints(), true)
            .get(5, TimeUnit.SECONDS)
            .getMember();
        assertThat(m2).isNotNull();
        assertThat(m2.isLearner()).isTrue();
        List<Member> members = clusterClient.listMember().get().getMembers();
        assertThat(members).hasSize(2);
        assertThat(members.stream().filter(Member::isLearner).findAny()).isPresent();
    }
}
