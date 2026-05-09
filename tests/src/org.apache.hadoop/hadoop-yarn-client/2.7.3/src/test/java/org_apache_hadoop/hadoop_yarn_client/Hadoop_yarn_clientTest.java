/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_yarn_client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.yarn.api.protocolrecords.GetNewApplicationResponse;
import org.apache.hadoop.yarn.api.records.ApplicationAccessType;
import org.apache.hadoop.yarn.api.records.ApplicationAttemptId;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationSubmissionContext;
import org.apache.hadoop.yarn.api.records.Container;
import org.apache.hadoop.yarn.api.records.ContainerId;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.api.records.ContainerState;
import org.apache.hadoop.yarn.api.records.ContainerStatus;
import org.apache.hadoop.yarn.api.records.NodeId;
import org.apache.hadoop.yarn.api.records.NodeReport;
import org.apache.hadoop.yarn.api.records.Priority;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.api.records.ResourceRequest;
import org.apache.hadoop.yarn.api.records.Token;
import org.apache.hadoop.yarn.client.api.AMRMClient;
import org.apache.hadoop.yarn.client.api.NMClient;
import org.apache.hadoop.yarn.client.api.NMTokenCache;
import org.apache.hadoop.yarn.client.api.SharedCacheClient;
import org.apache.hadoop.yarn.client.api.YarnClientApplication;
import org.apache.hadoop.yarn.client.api.async.AMRMClientAsync;
import org.apache.hadoop.yarn.client.api.async.NMClientAsync;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Hadoop_yarn_clientTest {
    private static final int MEMORY_MB = 128;
    private static final int VIRTUAL_CORES = 2;

    @Test
    void containerRequestPreservesSchedulingConstraintsAndRejectsInvalidInput() {
        Resource capability = Resource.newInstance(MEMORY_MB, VIRTUAL_CORES);
        Priority priority = Priority.newInstance(7);
        String[] nodes = {"node-a.example.test", "node-b.example.test"};
        String[] racks = {"/rack-a"};

        AMRMClient.ContainerRequest request = new AMRMClient.ContainerRequest(
                capability,
                nodes,
                racks,
                priority,
                false,
                "gpu");
        nodes[0] = "mutated.example.test";
        racks[0] = "/mutated";

        assertThat(request.getCapability()).isSameAs(capability);
        assertThat(request.getPriority()).isSameAs(priority);
        assertThat(request.getNodes()).containsExactly("node-a.example.test", "node-b.example.test");
        assertThat(request.getRacks()).containsExactly("/rack-a");
        assertThat(request.getRelaxLocality()).isFalse();
        assertThat(request.getNodeLabelExpression()).isEqualTo("gpu");
        assertThat(request.toString()).contains("Capability", "Priority");

        assertThatThrownBy(() -> new AMRMClient.ContainerRequest(null, null, null, priority))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Resource");
        assertThatThrownBy(() -> new AMRMClient.ContainerRequest(capability, null, null, priority, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("locality relaxation");
    }

    @Test
    void amrmClientTracksContainerRequestsAndTokenCacheWithoutResourceManager() {
        AMRMClient<AMRMClient.ContainerRequest> client = AMRMClient.createAMRMClient();
        NMTokenCache tokenCache = new NMTokenCache();
        client.setNMTokenCache(tokenCache);

        Resource capability = Resource.newInstance(MEMORY_MB, VIRTUAL_CORES);
        Priority priority = Priority.newInstance(3);
        AMRMClient.ContainerRequest request = new AMRMClient.ContainerRequest(capability, null, null, priority);

        client.addContainerRequest(request);
        List<? extends Collection<AMRMClient.ContainerRequest>> matchingRequests =
                client.getMatchingRequests(priority, ResourceRequest.ANY, capability);

        assertThat(client.getNMTokenCache()).isSameAs(tokenCache);
        assertThat(matchingRequests).isNotEmpty();
        assertThat(matchingRequests).anySatisfy(requests -> assertThat(requests).contains(request));

        client.updateBlacklist(Collections.singletonList("bad-node.example.test"), Collections.emptyList());
        client.releaseAssignedContainer(ContainerId.newContainerId(
                ApplicationAttemptId.newInstance(ApplicationId.newInstance(1234L, 1), 1),
                1L));
        client.removeContainerRequest(request);

        assertThat(client.getMatchingRequests(priority, ResourceRequest.ANY, capability))
                .allSatisfy(requests -> assertThat(requests).doesNotContain(request));
    }

    @Test
    void amrmClientWaitForPollsUntilConditionIsSatisfiedAndValidatesArguments() throws InterruptedException {
        AMRMClient<AMRMClient.ContainerRequest> client = AMRMClient.createAMRMClient();
        AtomicInteger checks = new AtomicInteger();

        client.waitFor(() -> checks.incrementAndGet() == 3, 1, 10);

        assertThat(checks.get()).isEqualTo(3);
        assertThatThrownBy(() -> client.waitFor(null, 1, 1))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("check");
        assertThatThrownBy(() -> client.waitFor(() -> true, -1, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("checkEveryMillis");
        assertThatThrownBy(() -> client.waitFor(() -> true, 1, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("logInterval");
    }

    @Test
    void nmTokenCacheSupportsInstanceAndSingletonTokenManagement() {
        Token token = Token.newInstance(
                "identifier".getBytes(StandardCharsets.UTF_8),
                "kind",
                "password".getBytes(StandardCharsets.UTF_8),
                "service");
        NMTokenCache cache = new NMTokenCache();

        cache.setToken("node-a:1234", token);
        assertThat(cache.containsToken("node-a:1234")).isTrue();
        assertThat(cache.getToken("node-a:1234")).isSameAs(token);
        assertThat(cache.numberOfTokensInCache()).isEqualTo(1);

        cache.removeToken("node-a:1234");
        assertThat(cache.containsToken("node-a:1234")).isFalse();
        assertThat(cache.numberOfTokensInCache()).isZero();

        NMTokenCache singleton = NMTokenCache.getSingleton();
        try {
            NMTokenCache.setNMToken("node-b:2345", token);
            assertThat(NMTokenCache.getNMToken("node-b:2345")).isSameAs(token);
        } finally {
            singleton.removeToken("node-b:2345");
        }
    }

    @Test
    void sharedCacheClientComputesStableChecksumsForLocalFiles(@TempDir File temporaryDirectory) throws IOException {
        File firstFile = new File(temporaryDirectory, "first-resource.txt");
        File secondFile = new File(temporaryDirectory, "second-resource.txt");
        File differentFile = new File(temporaryDirectory, "different-resource.txt");
        byte[] resourceBytes = "shared-cache-resource".getBytes(StandardCharsets.UTF_8);
        Files.write(firstFile.toPath(), resourceBytes);
        Files.write(secondFile.toPath(), resourceBytes);
        Files.write(differentFile.toPath(), "different-shared-cache-resource".getBytes(StandardCharsets.UTF_8));

        SharedCacheClient client = SharedCacheClient.createSharedCacheClient();
        YarnConfiguration configuration = new YarnConfiguration();
        configuration.setBoolean("fs.file.impl.disable.cache", true);
        try {
            client.init(configuration);

            String firstChecksum = client.getFileChecksum(new Path(firstFile.toURI()));
            String secondChecksum = client.getFileChecksum(new Path(secondFile.toURI()));
            String differentChecksum = client.getFileChecksum(new Path(differentFile.toURI()));

            assertThat(firstChecksum).isNotBlank();
            assertThat(secondChecksum).isEqualTo(firstChecksum);
            assertThat(differentChecksum).isNotEqualTo(firstChecksum);
        } finally {
            client.stop();
        }
    }

    @Test
    void yarnClientApplicationWrapsNewApplicationResponseAndSubmissionContext() {
        ApplicationId applicationId = ApplicationId.newInstance(1111L, 42);
        Resource maximumCapability = Resource.newInstance(4096, 8);
        Resource minimumCapability = Resource.newInstance(64, 1);
        GetNewApplicationResponse response = GetNewApplicationResponse.newInstance(
                applicationId,
                minimumCapability,
                maximumCapability);
        ContainerLaunchContext launchContext = ContainerLaunchContext.newInstance(
                Collections.emptyMap(),
                Collections.singletonMap("ENV", "test"),
                Collections.singletonList("echo started"),
                Collections.singletonMap("metrics", ByteBuffer.wrap(new byte[] {1, 2, 3})),
                ByteBuffer.wrap(new byte[] {4, 5}),
                Collections.singletonMap(ApplicationAccessType.VIEW_APP, "user"));
        ApplicationSubmissionContext submissionContext = ApplicationSubmissionContext.newInstance(
                applicationId,
                "native-image-yarn-test",
                "default",
                Priority.newInstance(1),
                launchContext,
                false,
                true,
                2,
                Resource.newInstance(MEMORY_MB, VIRTUAL_CORES),
                "integration-test",
                true);

        YarnClientApplication application = new YarnClientApplication(response, submissionContext);

        assertThat(application.getNewApplicationResponse().getApplicationId()).isEqualTo(applicationId);
        assertThat(application.getNewApplicationResponse().getMaximumResourceCapability()).isEqualTo(maximumCapability);
        assertThat(application.getApplicationSubmissionContext().getApplicationName())
                .isEqualTo("native-image-yarn-test");
        assertThat(application.getApplicationSubmissionContext().getAMContainerSpec().getEnvironment())
                .containsEntry("ENV", "test");
        assertThat(application.getApplicationSubmissionContext().getKeepContainersAcrossApplicationAttempts()).isTrue();
    }

    @Test
    void nmClientFactoryExposesTokenCacheWithoutStartingNetworkServices() {
        NMClient nmClient = NMClient.createNMClient("test-nm-client");
        NMTokenCache cache = new NMTokenCache();

        nmClient.setNMTokenCache(cache);
        nmClient.cleanupRunningContainersOnStop(false);

        assertThat(nmClient.getNMTokenCache()).isSameAs(cache);
    }

    @Test
    void asyncClientsExposeCallbacksAndDelegateLocalState() {
        AMRMClientAsync.CallbackHandler amrmHandler = new RecordingAMRMCallbackHandler();
        AMRMClientAsync<AMRMClient.ContainerRequest> amrmClient =
                AMRMClientAsync.createAMRMClientAsync(25, amrmHandler);
        RecordingNMCallbackHandler nmHandler = new RecordingNMCallbackHandler();
        NMClientAsync nmClient = NMClientAsync.createNMClientAsync(nmHandler);
        NMClient delegate = NMClient.createNMClient("delegate-nm-client");

        amrmClient.setHeartbeatInterval(10);
        nmClient.setClient(delegate);

        Resource capability = Resource.newInstance(MEMORY_MB, VIRTUAL_CORES);
        Priority priority = Priority.newInstance(5);
        AMRMClient.ContainerRequest request = new AMRMClient.ContainerRequest(capability, null, null, priority);
        amrmClient.addContainerRequest(request);

        assertThat(amrmClient.getMatchingRequests(priority, ResourceRequest.ANY, capability))
                .anySatisfy(requests -> assertThat(requests).contains(request));
        assertThat(nmClient.getCallbackHandler()).isSameAs(nmHandler);
        assertThat(nmClient.getClient()).isSameAs(delegate);
    }

    @Test
    void nmClientAsyncReportsOutOfOrderStopThroughCallbackWithoutStartingThreads() {
        RecordingNMCallbackHandler handler = new RecordingNMCallbackHandler();
        NMClientAsync client = NMClientAsync.createNMClientAsync(handler);
        ContainerId containerId = ContainerId.newContainerId(
                ApplicationAttemptId.newInstance(ApplicationId.newInstance(2222L, 3), 1),
                9L);
        NodeId nodeId = NodeId.newInstance("node.example.test", 8042);

        client.stopContainerAsync(containerId, nodeId);

        assertThat(handler.stopContainerError.get()).isInstanceOf(Exception.class);
        assertThat(handler.stoppedContainer.get()).isEqualTo(containerId);
    }

    @Test
    void yarnRecordsUsedByClientCallbacksRoundTripPublicFields() {
        ApplicationId applicationId = ApplicationId.newInstance(3333L, 4);
        ApplicationAttemptId attemptId = ApplicationAttemptId.newInstance(applicationId, 2);
        ContainerId containerId = ContainerId.newContainerId(attemptId, 12L);
        NodeId nodeId = NodeId.newInstance("node.example.test", 1234);
        Resource resource = Resource.newInstance(MEMORY_MB, VIRTUAL_CORES);
        Priority priority = Priority.newInstance(2);
        Token token = Token.newInstance(new byte[] {1}, "container", new byte[] {2}, "node.example.test:1234");
        Container container = Container.newInstance(
                containerId,
                nodeId,
                "node.example.test:8042",
                resource,
                priority,
                token);
        ContainerStatus status = ContainerStatus.newInstance(containerId, ContainerState.COMPLETE, "finished", 0);

        assertThat(container.getId()).isEqualTo(containerId);
        assertThat(container.getNodeId()).isEqualTo(nodeId);
        assertThat(container.getResource()).isEqualTo(resource);
        assertThat(container.getPriority()).isEqualTo(priority);
        assertThat(status.getContainerId()).isEqualTo(containerId);
        assertThat(status.getState()).isEqualTo(ContainerState.COMPLETE);
        assertThat(status.getDiagnostics()).isEqualTo("finished");
        assertThat(status.getExitStatus()).isZero();
        assertThat(containerId.toString()).contains("container");
    }

    private static final class RecordingAMRMCallbackHandler implements AMRMClientAsync.CallbackHandler {
        @Override
        public void onContainersCompleted(List<ContainerStatus> statuses) {
        }

        @Override
        public void onContainersAllocated(List<Container> containers) {
        }

        @Override
        public void onShutdownRequest() {
        }

        @Override
        public void onNodesUpdated(List<NodeReport> updatedNodes) {
        }

        @Override
        public float getProgress() {
            return 0.5f;
        }

        @Override
        public void onError(Throwable error) {
            throw new AssertionError(error);
        }
    }

    private static final class RecordingNMCallbackHandler implements NMClientAsync.CallbackHandler {
        private final AtomicReference<ContainerId> stoppedContainer = new AtomicReference<>();
        private final AtomicReference<Throwable> stopContainerError = new AtomicReference<>();

        @Override
        public void onContainerStarted(ContainerId containerId, Map<String, ByteBuffer> allServiceResponse) {
        }

        @Override
        public void onContainerStatusReceived(ContainerId containerId, ContainerStatus containerStatus) {
        }

        @Override
        public void onContainerStopped(ContainerId containerId) {
            stoppedContainer.set(containerId);
        }

        @Override
        public void onStartContainerError(ContainerId containerId, Throwable error) {
        }

        @Override
        public void onGetContainerStatusError(ContainerId containerId, Throwable error) {
        }

        @Override
        public void onStopContainerError(ContainerId containerId, Throwable error) {
            stoppedContainer.set(containerId);
            stopContainerError.set(error);
        }
    }
}
