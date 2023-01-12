/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_etcd.jetcd_core.impl;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.Election;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.Lease;
import io.etcd.jetcd.election.CampaignResponse;
import io.etcd.jetcd.election.LeaderKey;
import io.etcd.jetcd.election.LeaderResponse;
import io.etcd.jetcd.election.NoLeaderException;
import io.etcd.jetcd.election.NotLeaderException;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.test.EtcdClusterExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static io_etcd.jetcd_core.impl.TestUtil.randomByteSequence;
import static io_etcd.jetcd_core.impl.TestUtil.randomString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@SuppressWarnings({"resource", "CatchMayIgnoreException", "ResultOfMethodCallIgnored"})
// `@org.junit.jupiter.api.Timeout(value = 30)` can't be used in the nativeTest GraalVM CE 22.3
public class ElectionTest {
    private static final int OPERATION_TIMEOUT = 5;
    @RegisterExtension
    public static final EtcdClusterExtension cluster = EtcdClusterExtension.builder()
            .withNodes(3)
            .build();
    private static Election electionClient;
    private static Lease leaseClient;
    private static KV kvClient;

    @BeforeAll
    public static void setUp() {
        Client client = TestUtil.client(cluster).build();
        electionClient = client.getElectionClient();
        leaseClient = client.getLeaseClient();
        kvClient = client.getKVClient();
    }

    @Test
    public void testIsolatedElection() throws Exception {
        ByteSequence electionName = ByteSequence.from(randomString(), StandardCharsets.UTF_8);
        long leaseId = leaseClient.grant(10).get().getID();
        ByteSequence firstProposal = ByteSequence.from("proposal1", StandardCharsets.UTF_8);
        CampaignResponse campaignResponse = electionClient.campaign(electionName, leaseId, firstProposal)
                .get(OPERATION_TIMEOUT, TimeUnit.SECONDS);
        assertThat(campaignResponse.getLeader()).isNotNull();
        assertThat(campaignResponse.getLeader().getLease()).isEqualTo(leaseId);
        assertThat(campaignResponse.getLeader().getName()).isEqualTo(electionName);
        GetOption getOption = GetOption.newBuilder().isPrefix(true).build();
        List<KeyValue> keys = kvClient.get(electionName, getOption).get().getKvs();
        assertThat(keys.size()).isEqualTo(1);
        assertThat(keys.get(0).getKey().toString()).isEqualTo(campaignResponse.getLeader().getKey().toString());
        assertThat(keys.get(0).getValue()).isEqualTo(firstProposal);
        LeaderResponse leaderResponse = electionClient.leader(electionName)
                .get(OPERATION_TIMEOUT, TimeUnit.SECONDS);
        assertThat(leaderResponse.getKv().getKey()).isEqualTo(campaignResponse.getLeader().getKey());
        assertThat(leaderResponse.getKv().getValue()).isEqualTo(firstProposal);
        assertThat(leaderResponse.getKv().getLease()).isEqualTo(leaseId);
        assertThat(leaderResponse.getKv().getCreateRevision()).isEqualTo(campaignResponse.getLeader().getRevision());
        ByteSequence secondProposal = ByteSequence.from("proposal2", StandardCharsets.UTF_8);
        electionClient.proclaim(campaignResponse.getLeader(), secondProposal).get(OPERATION_TIMEOUT, TimeUnit.SECONDS);
        keys = kvClient.get(electionName, getOption).get().getKvs();
        assertThat(keys.size()).isEqualTo(1);
        assertThat(keys.get(0).getValue()).isEqualTo(secondProposal);
        electionClient.resign(campaignResponse.getLeader()).get(OPERATION_TIMEOUT, TimeUnit.SECONDS);
        keys = kvClient.get(electionName, getOption).get().getKvs();
        assertThat(keys).isEmpty();
        leaseClient.revoke(leaseId).get();
    }

    @Test
    public void testEmptyElection() throws Exception {
        ByteSequence electionName = ByteSequence.from(randomString(), StandardCharsets.UTF_8);
        try {
            electionClient.leader(electionName).get(OPERATION_TIMEOUT, TimeUnit.SECONDS);
            fail("etcd communicates missing leader with error");
        } catch (ExecutionException e) {
            assertThat(e.getCause()).isInstanceOf(NoLeaderException.class);
        }
    }

    @Test
    public void testRetryCampaignWithDifferentValue() throws Exception {
        ByteSequence electionName = ByteSequence.from(randomString(), StandardCharsets.UTF_8);
        long leaseId = leaseClient.grant(10).get().getID();
        ByteSequence firstProposal = ByteSequence.from("proposal1", StandardCharsets.UTF_8);
        CampaignResponse campaignResponse1 = electionClient.campaign(electionName, leaseId, firstProposal)
                .get(OPERATION_TIMEOUT, TimeUnit.SECONDS);
        ByteSequence secondProposal = ByteSequence.from("proposal2", StandardCharsets.UTF_8);
        CampaignResponse campaignResponse2 = electionClient.campaign(electionName, leaseId, secondProposal)
                .get(OPERATION_TIMEOUT, TimeUnit.SECONDS);
        LeaderResponse leaderResponse = electionClient.leader(electionName).get(OPERATION_TIMEOUT, TimeUnit.SECONDS);
        assertThat(leaderResponse.getKv().getKey()).isEqualTo(campaignResponse1.getLeader().getKey());
        assertThat(campaignResponse1.getLeader().getKey()).isEqualTo(campaignResponse2.getLeader().getKey());
        assertThat(campaignResponse1.getLeader().getRevision()).isEqualTo(campaignResponse2.getLeader().getRevision());
        GetOption getOption = GetOption.newBuilder().isPrefix(true).build();
        List<KeyValue> keys = kvClient.get(electionName, getOption).get().getKvs();
        assertThat(keys.size()).isEqualTo(1);
        assertThat(keys.get(0).getValue()).isEqualTo(secondProposal);
        electionClient.resign(campaignResponse1.getLeader()).get(OPERATION_TIMEOUT, TimeUnit.SECONDS);
        leaseClient.revoke(leaseId).get();
    }

    @Test
    public void testProposeValueNotBeingLeader() throws Exception {
        ByteSequence electionName = ByteSequence.from(randomString(), StandardCharsets.UTF_8);
        LeaderKey leaderKey = new LeaderKey(electionName, randomByteSequence(), 1, 1);
        ByteSequence proposal = ByteSequence.from("proposal", StandardCharsets.UTF_8);
        try {
            electionClient.proclaim(leaderKey, proposal).get(OPERATION_TIMEOUT, TimeUnit.SECONDS);
            fail("Cannot proclaim proposal not being a leader");
        } catch (ExecutionException e) {
            assertThat(e.getCause()).isInstanceOf(NotLeaderException.class);
        }
        GetOption getOption = GetOption.newBuilder().isPrefix(true).build();
        List<KeyValue> keys = kvClient.get(electionName, getOption).get().getKvs();
        assertThat(keys).isEmpty();
    }

    @Test
    public void testObserveElections() throws Exception {
        int electionCount = 3;
        final AtomicInteger electionsSeen = new AtomicInteger(0);
        ByteSequence electionName = ByteSequence.from(randomString(), StandardCharsets.UTF_8);
        electionClient.observe(electionName, new Election.Listener() {
            @Override
            public void onNext(LeaderResponse response) {
                electionsSeen.incrementAndGet();
            }

            @Override
            public void onError(Throwable error) {
            }

            @Override
            public void onCompleted() {
            }
        });
        long leaseId = leaseClient.grant(10).get().getID();
        for (int i = 0; i < electionCount; ++i) {
            ByteSequence proposal = ByteSequence.from(randomString(), StandardCharsets.UTF_8);
            CampaignResponse campaignResponse = electionClient.campaign(electionName, leaseId, proposal)
                    .get(OPERATION_TIMEOUT, TimeUnit.SECONDS);
            Thread.sleep(100);
            electionClient.resign(campaignResponse.getLeader()).get(OPERATION_TIMEOUT, TimeUnit.SECONDS);
        }
        TestUtil.waitForCondition(
                () -> electionsSeen.get() == electionCount, OPERATION_TIMEOUT * 1000,
                "Observer did not receive expected notifications, got: " + electionsSeen.get());
        leaseClient.revoke(leaseId).get();
    }

    @Test
    public void testSynchronizationBarrier() throws Exception {
        final int threadCount = 5;
        final Random random = new Random();
        ByteSequence electionName = ByteSequence.from(randomString(), StandardCharsets.UTF_8);
        final AtomicInteger sharedVariable = new AtomicInteger(0);
        List<Client> clients = new ArrayList<>(threadCount);
        List<Long> leases = new ArrayList<>(threadCount);
        for (int i = 0; i < threadCount; ++i) {
            Client client = TestUtil.client(cluster).build();
            long leaseId = client.getLeaseClient().grant(100).get().getID();
            clients.add(client);
            leases.add(leaseId);
        }
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>(threadCount);
        IntStream.range(0, threadCount).forEach(id -> {
            final ByteSequence proposal = ByteSequence.from(Integer.toString(id), StandardCharsets.UTF_8);
            futures.add(executor.submit(() -> {
                try {
                    Election electionClient = clients.get(id).getElectionClient();
                    CampaignResponse campaignResponse = electionClient.campaign(electionName, leases.get(id), proposal)
                            .get(OPERATION_TIMEOUT, TimeUnit.SECONDS);
                    int localCopy = sharedVariable.get();
                    Thread.sleep(200 + random.nextInt(300));
                    sharedVariable.set(localCopy + 1);
                    electionClient.resign(campaignResponse.getLeader()).get(OPERATION_TIMEOUT, TimeUnit.SECONDS);
                } catch (Exception e) {
                    fail("Unexpected error in thread {}: {}", id, e);
                }
            }));
        });
        executor.shutdown();
        executor.awaitTermination(threadCount * OPERATION_TIMEOUT, TimeUnit.SECONDS);
        futures.forEach(f -> assertThat(f).isDone());
        assertThat(sharedVariable.get()).isEqualTo(threadCount);
        GetOption getOption = GetOption.newBuilder().isPrefix(true).build();
        assertThat(kvClient.get(electionName, getOption).get().getCount()).isEqualTo(0L);
        for (int i = 0; i < threadCount; ++i) {
            clients.get(i).getLeaseClient().revoke(leases.get(i)).get();
            clients.get(i).close();
        }
    }
}
