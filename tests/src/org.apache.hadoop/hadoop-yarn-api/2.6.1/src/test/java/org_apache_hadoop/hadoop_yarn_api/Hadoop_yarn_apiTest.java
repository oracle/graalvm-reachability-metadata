/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_yarn_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.hadoop.yarn.api.protocolrecords.AllocateRequest;
import org.apache.hadoop.yarn.api.protocolrecords.AllocateResponse;
import org.apache.hadoop.yarn.api.protocolrecords.CancelDelegationTokenRequest;
import org.apache.hadoop.yarn.api.protocolrecords.CancelDelegationTokenResponse;
import org.apache.hadoop.yarn.api.protocolrecords.FinishApplicationMasterRequest;
import org.apache.hadoop.yarn.api.protocolrecords.FinishApplicationMasterResponse;
import org.apache.hadoop.yarn.api.protocolrecords.GetApplicationReportRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetApplicationReportResponse;
import org.apache.hadoop.yarn.api.protocolrecords.GetApplicationsRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetApplicationsResponse;
import org.apache.hadoop.yarn.api.protocolrecords.GetClusterMetricsRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetClusterMetricsResponse;
import org.apache.hadoop.yarn.api.protocolrecords.GetClusterNodesRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetClusterNodesResponse;
import org.apache.hadoop.yarn.api.protocolrecords.GetContainerStatusesRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetContainerStatusesResponse;
import org.apache.hadoop.yarn.api.protocolrecords.GetDelegationTokenRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetDelegationTokenResponse;
import org.apache.hadoop.yarn.api.protocolrecords.GetNewApplicationRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetNewApplicationResponse;
import org.apache.hadoop.yarn.api.protocolrecords.GetQueueInfoRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetQueueInfoResponse;
import org.apache.hadoop.yarn.api.protocolrecords.GetQueueUserAclsInfoRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetQueueUserAclsInfoResponse;
import org.apache.hadoop.yarn.api.protocolrecords.KillApplicationRequest;
import org.apache.hadoop.yarn.api.protocolrecords.KillApplicationResponse;
import org.apache.hadoop.yarn.api.protocolrecords.RegisterApplicationMasterRequest;
import org.apache.hadoop.yarn.api.protocolrecords.RegisterApplicationMasterResponse;
import org.apache.hadoop.yarn.api.protocolrecords.RenewDelegationTokenRequest;
import org.apache.hadoop.yarn.api.protocolrecords.RenewDelegationTokenResponse;
import org.apache.hadoop.yarn.api.protocolrecords.StartContainerRequest;
import org.apache.hadoop.yarn.api.protocolrecords.StartContainersRequest;
import org.apache.hadoop.yarn.api.protocolrecords.StartContainersResponse;
import org.apache.hadoop.yarn.api.protocolrecords.StopContainersRequest;
import org.apache.hadoop.yarn.api.protocolrecords.StopContainersResponse;
import org.apache.hadoop.yarn.api.protocolrecords.SubmitApplicationRequest;
import org.apache.hadoop.yarn.api.protocolrecords.SubmitApplicationResponse;
import org.apache.hadoop.yarn.api.records.AMCommand;
import org.apache.hadoop.yarn.api.records.ApplicationAccessType;
import org.apache.hadoop.yarn.api.records.ApplicationAttemptId;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.ApplicationResourceUsageReport;
import org.apache.hadoop.yarn.api.records.ApplicationSubmissionContext;
import org.apache.hadoop.yarn.api.records.Container;
import org.apache.hadoop.yarn.api.records.ContainerExitStatus;
import org.apache.hadoop.yarn.api.records.ContainerId;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.api.records.ContainerState;
import org.apache.hadoop.yarn.api.records.ContainerStatus;
import org.apache.hadoop.yarn.api.records.FinalApplicationStatus;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.hadoop.yarn.api.records.LocalResourceType;
import org.apache.hadoop.yarn.api.records.LocalResourceVisibility;
import org.apache.hadoop.yarn.api.records.NMToken;
import org.apache.hadoop.yarn.api.records.NodeId;
import org.apache.hadoop.yarn.api.records.NodeReport;
import org.apache.hadoop.yarn.api.records.NodeState;
import org.apache.hadoop.yarn.api.records.PreemptionContainer;
import org.apache.hadoop.yarn.api.records.PreemptionContract;
import org.apache.hadoop.yarn.api.records.PreemptionMessage;
import org.apache.hadoop.yarn.api.records.PreemptionResourceRequest;
import org.apache.hadoop.yarn.api.records.Priority;
import org.apache.hadoop.yarn.api.records.QueueACL;
import org.apache.hadoop.yarn.api.records.QueueInfo;
import org.apache.hadoop.yarn.api.records.QueueState;
import org.apache.hadoop.yarn.api.records.QueueUserACLInfo;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.api.records.ResourceBlacklistRequest;
import org.apache.hadoop.yarn.api.records.ResourceRequest;
import org.apache.hadoop.yarn.api.records.SerializedException;
import org.apache.hadoop.yarn.api.records.StrictPreemptionContract;
import org.apache.hadoop.yarn.api.records.Token;
import org.apache.hadoop.yarn.api.records.URL;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.apache.hadoop.yarn.api.records.YarnClusterMetrics;
import org.junit.jupiter.api.Test;

public class Hadoop_yarn_apiTest {
    @Test
    void createsApplicationSubmissionRecords() {
        ApplicationId applicationId = ApplicationId.newInstance(123456789L, 42);
        ApplicationAttemptId attemptId = ApplicationAttemptId.newInstance(applicationId, 3);
        ContainerId containerId = ContainerId.newInstance(attemptId, 7);
        NodeId nodeId = NodeId.newInstance("worker.example.test", 8042);
        Priority priority = Priority.newInstance(5);
        Resource capability = Resource.newInstance(2048, 2);
        Token token = Token.newInstance(bytes(1, 2, 3), "container-kind", bytes(4, 5), "node-service");

        URL resourceUrl = URL.newInstance("hdfs", "yarn", 8020, "/apps/app.jar");
        LocalResource localResource = LocalResource.newInstance(
                resourceUrl,
                LocalResourceType.FILE,
                LocalResourceVisibility.APPLICATION,
                4096L,
                111L,
                "*.jar");
        Map<String, LocalResource> localResources = new LinkedHashMap<>();
        localResources.put("app.jar", localResource);
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("JAVA_HOME", "/opt/jdk");
        Map<String, ByteBuffer> serviceData = new LinkedHashMap<>();
        serviceData.put("shuffle", byteBuffer(9, 8, 7));
        Map<ApplicationAccessType, String> acls = new LinkedHashMap<>();
        acls.put(ApplicationAccessType.VIEW_APP, "alice,bob");

        ContainerLaunchContext launchContext = ContainerLaunchContext.newInstance(
                localResources,
                environment,
                Arrays.asList("echo starting", "java -jar app.jar"),
                serviceData,
                byteBuffer(6, 6, 6),
                acls);
        ApplicationSubmissionContext submission = ApplicationSubmissionContext.newInstance(
                applicationId,
                "demo-application",
                "default",
                priority,
                launchContext,
                false,
                true,
                2,
                capability,
                "MAPREDUCE");

        assertThat(applicationId.getClusterTimestamp()).isEqualTo(123456789L);
        assertThat(applicationId.getId()).isEqualTo(42);
        assertThat(applicationId).isEqualTo(ApplicationId.newInstance(123456789L, 42));
        assertThat(attemptId.getApplicationId()).isEqualTo(applicationId);
        assertThat(containerId.getApplicationAttemptId()).isEqualTo(attemptId);
        assertThat(nodeId.toString()).isEqualTo("worker.example.test:8042");
        assertThat(priority.getPriority()).isEqualTo(5);
        assertThat(capability.getMemory()).isEqualTo(2048);
        assertThat(capability.getVirtualCores()).isEqualTo(2);
        assertThat(byteBufferToBytes(token.getIdentifier())).containsExactly(1, 2, 3);
        assertThat(token.getKind()).isEqualTo("container-kind");
        assertThat(localResource.getResource().getFile()).isEqualTo("/apps/app.jar");
        assertThat(localResource.getPattern()).isEqualTo("*.jar");
        assertThat(launchContext.getLocalResources()).containsEntry("app.jar", localResource);
        assertThat(launchContext.getEnvironment()).containsEntry("JAVA_HOME", "/opt/jdk");
        assertThat(launchContext.getCommands()).containsExactly("echo starting", "java -jar app.jar");
        assertThat(byteBufferToBytes(launchContext.getServiceData().get("shuffle"))).containsExactly(9, 8, 7);
        assertThat(launchContext.getApplicationACLs()).containsEntry(ApplicationAccessType.VIEW_APP, "alice,bob");
        assertThat(submission.getApplicationId()).isEqualTo(applicationId);
        assertThat(submission.getApplicationName()).isEqualTo("demo-application");
        assertThat(submission.getAMContainerSpec()).isEqualTo(launchContext);
        assertThat(submission.getApplicationType()).isEqualTo("MAPREDUCE");
    }

    @Test
    void createsResourceRequestsContainersAndPreemptionRecords() {
        ApplicationId applicationId = ApplicationId.newInstance(2000L, 1);
        ApplicationAttemptId attemptId = ApplicationAttemptId.newInstance(applicationId, 1);
        ContainerId containerId = ContainerId.newInstance(attemptId, 1);
        Priority priority = Priority.newInstance(1);
        Resource resource = Resource.newInstance(1024, 1);
        ResourceRequest anyRequest = ResourceRequest.newInstance(priority, ResourceRequest.ANY, resource, 3, true);
        ResourceRequest rackRequest = ResourceRequest.newInstance(priority, "/rack1", resource, 1, false);
        Token token = Token.newInstance(bytes(10), "container", bytes(11), "nm");
        Container container = Container.newInstance(
                containerId,
                NodeId.newInstance("worker", 1234),
                "worker:8042",
                resource,
                priority,
                token);
        ContainerStatus status = ContainerStatus.newInstance(
                containerId,
                ContainerState.COMPLETE,
                "finished",
                0);
        PreemptionContainer preemptedContainer = PreemptionContainer.newInstance(containerId);
        PreemptionResourceRequest preemptionRequest = PreemptionResourceRequest.newInstance(anyRequest);
        StrictPreemptionContract strictContract = StrictPreemptionContract.newInstance(
                Collections.singleton(preemptedContainer));
        PreemptionContract contract = PreemptionContract.newInstance(
                Collections.singletonList(preemptionRequest),
                Collections.singleton(preemptedContainer));
        PreemptionMessage message = PreemptionMessage.newInstance(strictContract, contract);
        ResourceBlacklistRequest blacklist = ResourceBlacklistRequest.newInstance(
                Collections.singletonList("bad-node"),
                Collections.singletonList("recovered-node"));
        NMToken nmToken = NMToken.newInstance(container.getNodeId(), token);

        assertThat(ResourceRequest.isAnyLocation(ResourceRequest.ANY)).isTrue();
        assertThat(ResourceRequest.isAnyLocation("worker")).isFalse();
        assertThat(anyRequest.getCapability()).isEqualTo(resource);
        assertThat(anyRequest.getNumContainers()).isEqualTo(3);
        assertThat(anyRequest.getRelaxLocality()).isTrue();
        assertThat(rackRequest.getRelaxLocality()).isFalse();
        assertThat(container.getId()).isEqualTo(containerId);
        assertThat(container.getNodeHttpAddress()).isEqualTo("worker:8042");
        assertThat(status.getState()).isEqualTo(ContainerState.COMPLETE);
        assertThat(status.getDiagnostics()).isEqualTo("finished");
        assertThat(message.getStrictContract().getContainers()).containsExactly(preemptedContainer);
        assertThat(message.getContract().getResourceRequest()).containsExactly(preemptionRequest);
        assertThat(blacklist.getBlacklistAdditions()).containsExactly("bad-node");
        assertThat(blacklist.getBlacklistRemovals()).containsExactly("recovered-node");
        assertThat(nmToken.getNodeId()).isEqualTo(container.getNodeId());
        assertThat(nmToken.getToken()).isEqualTo(token);
    }

    @Test
    void createsReportsQueuesMetricsAndSerializedExceptions() {
        ApplicationId applicationId = ApplicationId.newInstance(3000L, 2);
        ApplicationAttemptId attemptId = ApplicationAttemptId.newInstance(applicationId, 4);
        Token clientToken = Token.newInstance(bytes(1), "client", bytes(2), "am");
        Token amRmToken = Token.newInstance(bytes(3), "amrm", bytes(4), "rm");
        ApplicationResourceUsageReport usage = ApplicationResourceUsageReport.newInstance(
                2,
                1,
                Resource.newInstance(2048, 2),
                Resource.newInstance(1024, 1),
                Resource.newInstance(4096, 4),
                600L,
                60L);
        ApplicationReport report = ApplicationReport.newInstance(
                applicationId,
                attemptId,
                "alice",
                "default",
                "demo",
                "am-host",
                10000,
                clientToken,
                YarnApplicationState.RUNNING,
                "healthy",
                "http://tracking",
                10L,
                20L,
                FinalApplicationStatus.SUCCEEDED,
                usage,
                "http://original-tracking",
                0.75f,
                "YARN",
                amRmToken);
        NodeReport nodeReport = NodeReport.newInstance(
                NodeId.newInstance("node1", 8041),
                NodeState.RUNNING,
                "node1:8042",
                "/rack1",
                Resource.newInstance(512, 1),
                Resource.newInstance(8192, 8),
                2,
                "healthy",
                123L);
        QueueInfo childQueue = QueueInfo.newInstance(
                "child",
                0.25f,
                0.50f,
                0.10f,
                Collections.<QueueInfo>emptyList(),
                Collections.<ApplicationReport>emptyList(),
                QueueState.RUNNING,
                Collections.<String>emptySet(),
                "");
        QueueInfo queueInfo = QueueInfo.newInstance(
                "root",
                1.0f,
                1.0f,
                0.25f,
                Collections.singletonList(childQueue),
                Collections.singletonList(report),
                QueueState.RUNNING,
                Collections.singleton("gpu"),
                "gpu");
        QueueUserACLInfo aclInfo = QueueUserACLInfo.newInstance(
                "root",
                Arrays.asList(QueueACL.SUBMIT_APPLICATIONS, QueueACL.ADMINISTER_QUEUE));
        YarnClusterMetrics metrics = YarnClusterMetrics.newInstance(5);
        SerializedException serialized = SerializedException.newInstance(
                new RuntimeException("top", new IllegalStateException("nested")));

        assertThat(usage.getNumUsedContainers()).isEqualTo(2);
        assertThat(usage.getNeededResources().getVirtualCores()).isEqualTo(4);
        assertThat(usage.getMemorySeconds()).isEqualTo(600L);
        assertThat(usage.getVcoreSeconds()).isEqualTo(60L);
        assertThat(report.getApplicationId()).isEqualTo(applicationId);
        assertThat(report.getYarnApplicationState()).isEqualTo(YarnApplicationState.RUNNING);
        assertThat(report.getProgress()).isEqualTo(0.75f);
        assertThat(report.getAMRMToken()).isEqualTo(amRmToken);
        assertThat(nodeReport.getRackName()).isEqualTo("/rack1");
        assertThat(nodeReport.getCapability().getMemory()).isEqualTo(8192);
        assertThat(queueInfo.getChildQueues()).containsExactly(childQueue);
        assertThat(queueInfo.getApplications()).containsExactly(report);
        assertThat(queueInfo.getAccessibleNodeLabels()).containsExactly("gpu");
        assertThat(queueInfo.getDefaultNodeLabelExpression()).isEqualTo("gpu");
        assertThat(aclInfo.getUserAcls()).containsExactly(QueueACL.SUBMIT_APPLICATIONS, QueueACL.ADMINISTER_QUEUE);
        assertThat(metrics.getNumNodeManagers()).isEqualTo(5);
        assertThat(serialized.getMessage()).contains("top");
        assertThat(serialized.getCause().getMessage()).contains("nested");
    }

    @Test
    void createsApplicationClientProtocolRecords() {
        ApplicationId applicationId = ApplicationId.newInstance(4000L, 1);
        ApplicationSubmissionContext submission = ApplicationSubmissionContext.newInstance(
                applicationId,
                "client-app",
                "default",
                Priority.newInstance(0),
                ContainerLaunchContext.newInstance(
                        Collections.<String, LocalResource>emptyMap(),
                        Collections.<String, String>emptyMap(),
                        Collections.singletonList("true"),
                        Collections.<String, ByteBuffer>emptyMap(),
                        byteBuffer(1),
                        Collections.<ApplicationAccessType, String>emptyMap()),
                false,
                true,
                1,
                Resource.newInstance(256, 1));
        ApplicationReport report = ApplicationReport.newInstance(
                applicationId,
                ApplicationAttemptId.newInstance(applicationId, 1),
                "alice",
                "default",
                "client-app",
                "host",
                1,
                Token.newInstance(bytes(2), "client", bytes(3), "am"),
                YarnApplicationState.ACCEPTED,
                "",
                "N/A",
                1L,
                0L,
                FinalApplicationStatus.UNDEFINED,
                ApplicationResourceUsageReport.newInstance(
                        0,
                        0,
                        Resource.newInstance(0, 0),
                        Resource.newInstance(0, 0),
                        Resource.newInstance(0, 0),
                        0L,
                        0L),
                "N/A",
                0.0f,
                "YARN",
                Token.newInstance(bytes(4), "amrm", bytes(5), "rm"));
        QueueInfo queueInfo = QueueInfo.newInstance(
                "default",
                1.0f,
                1.0f,
                0.0f,
                Collections.<QueueInfo>emptyList(),
                Collections.singletonList(report),
                QueueState.RUNNING,
                Collections.<String>emptySet(),
                "");
        NodeReport nodeReport = NodeReport.newInstance(
                NodeId.newInstance("node", 8041),
                NodeState.RUNNING,
                "node:8042",
                "/default-rack",
                Resource.newInstance(0, 0),
                Resource.newInstance(4096, 4),
                0,
                "healthy",
                1L);

        SubmitApplicationRequest submitRequest = SubmitApplicationRequest.newInstance(submission);
        SubmitApplicationResponse submitResponse = SubmitApplicationResponse.newInstance();
        GetNewApplicationRequest newApplicationRequest = GetNewApplicationRequest.newInstance();
        GetNewApplicationResponse newApplicationResponse = GetNewApplicationResponse.newInstance(
                applicationId,
                Resource.newInstance(128, 1),
                Resource.newInstance(8192, 8));
        GetApplicationReportRequest reportRequest = GetApplicationReportRequest.newInstance(applicationId);
        GetApplicationReportResponse reportResponse = GetApplicationReportResponse.newInstance(report);
        GetApplicationsRequest applicationsRequest = GetApplicationsRequest.newInstance(
                Collections.singleton("YARN"),
                EnumSet.of(YarnApplicationState.ACCEPTED, YarnApplicationState.RUNNING));
        GetApplicationsResponse applicationsResponse = GetApplicationsResponse.newInstance(
                Collections.singletonList(report));
        GetClusterMetricsRequest metricsRequest = GetClusterMetricsRequest.newInstance();
        GetClusterMetricsResponse metricsResponse = GetClusterMetricsResponse.newInstance(
                YarnClusterMetrics.newInstance(1));
        GetClusterNodesRequest nodesRequest = GetClusterNodesRequest.newInstance(EnumSet.of(NodeState.RUNNING));
        GetClusterNodesResponse nodesResponse = GetClusterNodesResponse.newInstance(
                Collections.singletonList(nodeReport));
        GetQueueInfoRequest queueInfoRequest = GetQueueInfoRequest.newInstance("default", true, true, false);
        GetQueueInfoResponse queueInfoResponse = GetQueueInfoResponse.newInstance(queueInfo);
        GetQueueUserAclsInfoRequest aclsRequest = GetQueueUserAclsInfoRequest.newInstance();
        GetQueueUserAclsInfoResponse aclsResponse = GetQueueUserAclsInfoResponse.newInstance(Collections.singletonList(
                QueueUserACLInfo.newInstance("default", Collections.singletonList(QueueACL.SUBMIT_APPLICATIONS))));
        KillApplicationRequest killRequest = KillApplicationRequest.newInstance(applicationId);
        KillApplicationResponse killResponse = KillApplicationResponse.newInstance(true);

        assertThat(submitRequest.getApplicationSubmissionContext()).isEqualTo(submission);
        assertThat(submitResponse).isNotNull();
        assertThat(newApplicationRequest).isNotNull();
        assertThat(newApplicationResponse.getApplicationId()).isEqualTo(applicationId);
        assertThat(newApplicationResponse.getMaximumResourceCapability().getMemory()).isEqualTo(8192);
        assertThat(reportRequest.getApplicationId()).isEqualTo(applicationId);
        assertThat(reportResponse.getApplicationReport()).isEqualTo(report);
        assertThat(applicationsRequest.getApplicationTypes()).containsExactly("YARN");
        assertThat(applicationsRequest.getApplicationStates())
                .contains(YarnApplicationState.ACCEPTED, YarnApplicationState.RUNNING);
        assertThat(applicationsResponse.getApplicationList()).containsExactly(report);
        assertThat(metricsRequest).isNotNull();
        assertThat(metricsResponse.getClusterMetrics().getNumNodeManagers()).isEqualTo(1);
        assertThat(nodesRequest.getNodeStates()).isEqualTo(EnumSet.of(NodeState.RUNNING));
        assertThat(nodesResponse.getNodeReports()).containsExactly(nodeReport);
        assertThat(queueInfoRequest.getQueueName()).isEqualTo("default");
        assertThat(queueInfoRequest.getIncludeApplications()).isTrue();
        assertThat(queueInfoResponse.getQueueInfo()).isEqualTo(queueInfo);
        assertThat(aclsRequest).isNotNull();
        assertThat(aclsResponse.getUserAclsInfoList()).hasSize(1);
        assertThat(killRequest.getApplicationId()).isEqualTo(applicationId);
        assertThat(killResponse.getIsKillCompleted()).isTrue();
    }

    @Test
    void createsApplicationMasterAndContainerManagerProtocolRecords() {
        ApplicationId applicationId = ApplicationId.newInstance(5000L, 9);
        ApplicationAttemptId attemptId = ApplicationAttemptId.newInstance(applicationId, 2);
        ContainerId containerId = ContainerId.newInstance(attemptId, 12);
        Priority priority = Priority.newInstance(2);
        Resource resource = Resource.newInstance(512, 1);
        ResourceRequest ask = ResourceRequest.newInstance(priority, ResourceRequest.ANY, resource, 2);
        Token containerToken = Token.newInstance(bytes(12), "container", bytes(13), "node");
        Container container = Container.newInstance(
                containerId,
                NodeId.newInstance("node", 8041),
                "node:8042",
                resource,
                priority,
                containerToken);
        ContainerStatus status = ContainerStatus.newInstance(containerId, ContainerState.RUNNING, "running", -1000);
        ResourceBlacklistRequest blacklist = ResourceBlacklistRequest.newInstance(
                Collections.singletonList("bad"),
                Collections.<String>emptyList());
        PreemptionContainer preemptionContainer = PreemptionContainer.newInstance(containerId);
        PreemptionMessage preemptionMessage = PreemptionMessage.newInstance(
                StrictPreemptionContract.newInstance(Collections.singleton(preemptionContainer)),
                PreemptionContract.newInstance(
                        Collections.singletonList(PreemptionResourceRequest.newInstance(ask)),
                        Collections.singleton(preemptionContainer)));
        NMToken nmToken = NMToken.newInstance(container.getNodeId(), containerToken);
        NodeReport nodeReport = NodeReport.newInstance(
                container.getNodeId(),
                NodeState.RUNNING,
                "node:8042",
                "/rack",
                resource,
                Resource.newInstance(4096, 4),
                1,
                "ok",
                5L);
        Map<ApplicationAccessType, String> acls = Collections.singletonMap(ApplicationAccessType.MODIFY_APP, "admin");
        ContainerLaunchContext launchContext = ContainerLaunchContext.newInstance(
                Collections.<String, LocalResource>emptyMap(),
                Collections.singletonMap("A", "B"),
                Collections.singletonList("sleep 1"),
                Collections.<String, ByteBuffer>emptyMap(),
                byteBuffer(1, 2),
                acls);
        Map<String, ByteBuffer> serviceMetadata = Collections.singletonMap("aux", byteBuffer(7));
        SerializedException failure = SerializedException.newInstance(new IllegalArgumentException("container failed"));
        Map<ContainerId, SerializedException> failedRequests = Collections.singletonMap(containerId, failure);

        RegisterApplicationMasterRequest registerRequest = RegisterApplicationMasterRequest.newInstance(
                "am-host",
                12000,
                "http://am");
        RegisterApplicationMasterResponse registerResponse = RegisterApplicationMasterResponse.newInstance(
                Resource.newInstance(128, 1),
                Resource.newInstance(8192, 8),
                acls,
                byteBuffer(3, 4),
                Collections.singletonList(container),
                "default",
                Collections.singletonList(nmToken));
        AllocateRequest allocateRequest = AllocateRequest.newInstance(
                3,
                0.5f,
                Collections.singletonList(ask),
                Collections.singletonList(containerId),
                blacklist);
        AllocateResponse allocateResponse = AllocateResponse.newInstance(
                4,
                Collections.singletonList(status),
                Collections.singletonList(container),
                Collections.singletonList(nodeReport),
                Resource.newInstance(16384, 16),
                AMCommand.AM_RESYNC,
                6,
                preemptionMessage,
                Collections.singletonList(nmToken));
        FinishApplicationMasterRequest finishRequest = FinishApplicationMasterRequest.newInstance(
                FinalApplicationStatus.SUCCEEDED,
                "done",
                "http://done");
        FinishApplicationMasterResponse finishResponse = FinishApplicationMasterResponse.newInstance(true);
        StartContainerRequest startContainerRequest = StartContainerRequest.newInstance(launchContext, containerToken);
        StartContainersRequest startContainersRequest = StartContainersRequest.newInstance(
                Collections.singletonList(startContainerRequest));
        StartContainersResponse startContainersResponse = StartContainersResponse.newInstance(
                serviceMetadata,
                Collections.singletonList(containerId),
                failedRequests);
        StopContainersRequest stopContainersRequest = StopContainersRequest.newInstance(
                Collections.singletonList(containerId));
        StopContainersResponse stopContainersResponse = StopContainersResponse.newInstance(
                Collections.singletonList(containerId),
                failedRequests);

        assertThat(registerRequest.getHost()).isEqualTo("am-host");
        assertThat(registerRequest.getRpcPort()).isEqualTo(12000);
        assertThat(registerResponse.getMaximumResourceCapability().getVirtualCores()).isEqualTo(8);
        assertThat(registerResponse.getApplicationACLs()).containsEntry(ApplicationAccessType.MODIFY_APP, "admin");
        assertThat(byteBufferToBytes(registerResponse.getClientToAMTokenMasterKey())).containsExactly(3, 4);
        assertThat(registerResponse.getContainersFromPreviousAttempts()).containsExactly(container);
        assertThat(registerResponse.getQueue()).isEqualTo("default");
        assertThat(registerResponse.getNMTokensFromPreviousAttempts()).containsExactly(nmToken);
        assertThat(allocateRequest.getResponseId()).isEqualTo(3);
        assertThat(allocateRequest.getAskList()).containsExactly(ask);
        assertThat(allocateRequest.getReleaseList()).containsExactly(containerId);
        assertThat(allocateRequest.getResourceBlacklistRequest()).isEqualTo(blacklist);
        assertThat(allocateResponse.getResponseId()).isEqualTo(4);
        assertThat(allocateResponse.getCompletedContainersStatuses()).containsExactly(status);
        assertThat(allocateResponse.getAllocatedContainers()).containsExactly(container);
        assertThat(allocateResponse.getUpdatedNodes()).containsExactly(nodeReport);
        assertThat(allocateResponse.getAMCommand()).isEqualTo(AMCommand.AM_RESYNC);
        assertThat(allocateResponse.getNumClusterNodes()).isEqualTo(6);
        assertThat(allocateResponse.getPreemptionMessage()).isEqualTo(preemptionMessage);
        assertThat(allocateResponse.getNMTokens()).containsExactly(nmToken);
        assertThat(finishRequest.getFinalApplicationStatus()).isEqualTo(FinalApplicationStatus.SUCCEEDED);
        assertThat(finishResponse.getIsUnregistered()).isTrue();
        assertThat(startContainerRequest.getContainerLaunchContext()).isEqualTo(launchContext);
        assertThat(startContainersRequest.getStartContainerRequests()).containsExactly(startContainerRequest);
        assertThat(startContainersResponse.getSuccessfullyStartedContainers()).containsExactly(containerId);
        assertThat(byteBufferToBytes(startContainersResponse.getAllServicesMetaData().get("aux"))).containsExactly(7);
        assertThat(startContainersResponse.getFailedRequests()).containsEntry(containerId, failure);
        assertThat(stopContainersRequest.getContainerIds()).containsExactly(containerId);
        assertThat(stopContainersResponse.getSuccessfullyStoppedContainers()).containsExactly(containerId);
        assertThat(stopContainersResponse.getFailedRequests()).containsEntry(containerId, failure);
    }

    @Test
    void createsContainerStatusLookupProtocolRecords() {
        ApplicationId applicationId = ApplicationId.newInstance(6000L, 3);
        ApplicationAttemptId attemptId = ApplicationAttemptId.newInstance(applicationId, 1);
        ContainerId completedContainerId = ContainerId.newInstance(attemptId, 1);
        ContainerId failedContainerId = ContainerId.newInstance(attemptId, 2);
        ContainerStatus completedStatus = ContainerStatus.newInstance(
                completedContainerId,
                ContainerState.COMPLETE,
                "finished successfully",
                ContainerExitStatus.SUCCESS);
        SerializedException failure = SerializedException.newInstance(
                new IllegalArgumentException("container status unavailable"));

        GetContainerStatusesRequest request = GetContainerStatusesRequest.newInstance(
                Arrays.asList(completedContainerId, failedContainerId));
        GetContainerStatusesResponse response = GetContainerStatusesResponse.newInstance(
                Collections.singletonList(completedStatus),
                Collections.singletonMap(failedContainerId, failure));

        assertThat(request.getContainerIds()).containsExactly(completedContainerId, failedContainerId);
        assertThat(response.getContainerStatuses()).containsExactly(completedStatus);
        assertThat(completedStatus.getExitStatus()).isEqualTo(ContainerExitStatus.SUCCESS);
        assertThat(response.getFailedRequests()).containsEntry(failedContainerId, failure);
    }

    @Test
    void createsDelegationTokenProtocolRecords() {
        Token delegationToken = Token.newInstance(
                bytes(21, 22, 23),
                "RM_DELEGATION_TOKEN",
                bytes(24, 25),
                "resource-manager");
        GetDelegationTokenRequest getRequest = GetDelegationTokenRequest.newInstance("history-server");
        GetDelegationTokenResponse getResponse = GetDelegationTokenResponse.newInstance(delegationToken);
        RenewDelegationTokenRequest renewRequest = RenewDelegationTokenRequest.newInstance(delegationToken);
        RenewDelegationTokenResponse renewResponse = RenewDelegationTokenResponse.newInstance(123456789L);
        CancelDelegationTokenRequest cancelRequest = CancelDelegationTokenRequest.newInstance(delegationToken);
        CancelDelegationTokenResponse cancelResponse = CancelDelegationTokenResponse.newInstance();

        assertThat(getRequest.getRenewer()).isEqualTo("history-server");
        assertThat(getResponse.getRMDelegationToken()).isEqualTo(delegationToken);
        assertThat(renewRequest.getDelegationToken()).isEqualTo(delegationToken);
        assertThat(renewResponse.getNextExpirationTime()).isEqualTo(123456789L);
        assertThat(cancelRequest.getDelegationToken()).isEqualTo(delegationToken);
        assertThat(cancelResponse).isNotNull();
    }

    @Test
    void mutatesRecordsThroughSetters() {
        Resource resource = Resource.newInstance(1, 1);
        resource.setMemory(4096);
        resource.setVirtualCores(4);
        Priority priority = Priority.newInstance(1);
        priority.setPriority(9);
        ResourceRequest request = ResourceRequest.newInstance(priority, "old", resource, 1);
        request.setResourceName("new");
        request.setNumContainers(5);
        request.setRelaxLocality(false);
        URL url = URL.newInstance("file", null, 0, "/tmp/a");
        url.setScheme("hdfs");
        url.setUserInfo("user");
        url.setHost("namenode");
        url.setPort(8020);
        url.setFile("/tmp/b");
        LocalResource localResource = LocalResource.newInstance(
                url,
                LocalResourceType.FILE,
                LocalResourceVisibility.PRIVATE,
                1L,
                2L);
        localResource.setSize(3L);
        localResource.setTimestamp(4L);
        localResource.setPattern("*.txt");
        Token token = Token.newInstance(bytes(1), "kind", bytes(2), "service");
        token.setKind("updated-kind");
        token.setService("updated-service");
        token.setIdentifier(byteBuffer(3, 4));
        token.setPassword(byteBuffer(5, 6));

        assertThat(resource.getMemory()).isEqualTo(4096);
        assertThat(resource.getVirtualCores()).isEqualTo(4);
        assertThat(priority.getPriority()).isEqualTo(9);
        assertThat(request.getResourceName()).isEqualTo("new");
        assertThat(request.getNumContainers()).isEqualTo(5);
        assertThat(request.getRelaxLocality()).isFalse();
        assertThat(url.getScheme()).isEqualTo("hdfs");
        assertThat(url.getUserInfo()).isEqualTo("user");
        assertThat(url.getHost()).isEqualTo("namenode");
        assertThat(url.getPort()).isEqualTo(8020);
        assertThat(url.getFile()).isEqualTo("/tmp/b");
        assertThat(localResource.getSize()).isEqualTo(3L);
        assertThat(localResource.getTimestamp()).isEqualTo(4L);
        assertThat(localResource.getPattern()).isEqualTo("*.txt");
        assertThat(token.getKind()).isEqualTo("updated-kind");
        assertThat(token.getService()).isEqualTo("updated-service");
        assertThat(byteBufferToBytes(token.getIdentifier())).containsExactly(3, 4);
        assertThat(byteBufferToBytes(token.getPassword())).containsExactly(5, 6);
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            bytes[index] = (byte) values[index];
        }
        return bytes;
    }

    private static ByteBuffer byteBuffer(int... values) {
        return ByteBuffer.wrap(bytes(values));
    }

    private static byte[] byteBufferToBytes(ByteBuffer buffer) {
        ByteBuffer duplicate = buffer.duplicate();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return bytes;
    }
}
