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
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.apache.hadoop.yarn.api.records.impl.pb.ApplicationAttemptIdPBImpl;
import org.apache.hadoop.yarn.api.records.impl.pb.ApplicationIdPBImpl;
import org.apache.hadoop.yarn.api.records.impl.pb.ApplicationReportPBImpl;
import org.apache.hadoop.yarn.api.records.impl.pb.ApplicationResourceUsageReportPBImpl;
import org.apache.hadoop.yarn.api.records.impl.pb.ApplicationSubmissionContextPBImpl;
import org.apache.hadoop.yarn.api.records.impl.pb.ContainerIdPBImpl;
import org.apache.hadoop.yarn.api.records.impl.pb.ContainerLaunchContextPBImpl;
import org.apache.hadoop.yarn.api.records.impl.pb.ContainerPBImpl;
import org.apache.hadoop.yarn.api.records.impl.pb.ContainerStatusPBImpl;
import org.apache.hadoop.yarn.api.records.impl.pb.LocalResourcePBImpl;
import org.apache.hadoop.yarn.api.records.impl.pb.NMTokenPBImpl;
import org.apache.hadoop.yarn.api.records.impl.pb.NodeIdPBImpl;
import org.apache.hadoop.yarn.api.records.impl.pb.NodeReportPBImpl;
import org.apache.hadoop.yarn.api.records.impl.pb.PreemptionContainerPBImpl;
import org.apache.hadoop.yarn.api.records.impl.pb.PreemptionContractPBImpl;
import org.apache.hadoop.yarn.api.records.impl.pb.PreemptionMessagePBImpl;
import org.apache.hadoop.yarn.api.records.impl.pb.PreemptionResourceRequestPBImpl;
import org.apache.hadoop.yarn.api.records.impl.pb.PriorityPBImpl;
import org.apache.hadoop.yarn.api.records.impl.pb.QueueInfoPBImpl;
import org.apache.hadoop.yarn.api.records.impl.pb.QueueUserACLInfoPBImpl;
import org.apache.hadoop.yarn.api.records.impl.pb.ResourceBlacklistRequestPBImpl;
import org.apache.hadoop.yarn.api.records.impl.pb.ResourcePBImpl;
import org.apache.hadoop.yarn.api.records.impl.pb.ResourceRequestPBImpl;
import org.apache.hadoop.yarn.api.records.impl.pb.SerializedExceptionPBImpl;
import org.apache.hadoop.yarn.api.records.impl.pb.StrictPreemptionContractPBImpl;
import org.apache.hadoop.yarn.api.records.impl.pb.TokenPBImpl;
import org.apache.hadoop.yarn.api.records.impl.pb.URLPBImpl;
import org.apache.hadoop.yarn.api.records.impl.pb.YarnClusterMetricsPBImpl;
import org.apache.hadoop.yarn.proto.YarnProtos;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.AllocateRequestPBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.AllocateResponsePBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.CancelDelegationTokenRequestPBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.CancelDelegationTokenResponsePBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.FinishApplicationMasterRequestPBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.FinishApplicationMasterResponsePBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.GetApplicationReportRequestPBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.GetApplicationReportResponsePBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.GetApplicationsRequestPBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.GetApplicationsResponsePBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.GetClusterMetricsRequestPBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.GetClusterMetricsResponsePBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.GetClusterNodesRequestPBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.GetClusterNodesResponsePBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.GetContainerStatusesRequestPBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.GetContainerStatusesResponsePBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.GetDelegationTokenRequestPBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.GetDelegationTokenResponsePBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.GetNewApplicationRequestPBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.GetNewApplicationResponsePBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.GetQueueInfoRequestPBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.GetQueueInfoResponsePBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.GetQueueUserAclsInfoRequestPBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.GetQueueUserAclsInfoResponsePBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.KillApplicationRequestPBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.KillApplicationResponsePBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.RegisterApplicationMasterRequestPBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.RegisterApplicationMasterResponsePBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.RenewDelegationTokenRequestPBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.RenewDelegationTokenResponsePBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.StartContainerRequestPBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.StartContainersRequestPBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.StartContainersResponsePBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.StopContainersRequestPBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.StopContainersResponsePBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.SubmitApplicationRequestPBImpl;
import org.apache.hadoop.yarn.api.protocolrecords.impl.pb.SubmitApplicationResponsePBImpl;
import org.junit.jupiter.api.Test;

public class Hadoop_yarn_apiTest {
    @Test
    void createsApplicationSubmissionRecords() {
        ApplicationId applicationId = applicationId(123456789L, 42);
        ApplicationAttemptId attemptId = applicationAttemptId(applicationId, 3);
        ContainerId containerId = containerId(attemptId, 7);
        NodeId nodeId = nodeId("worker.example.test", 8042);
        Priority priority = priority(5);
        Resource capability = resource(2048, 2);
        Token token = token(bytes(1, 2, 3), "container-kind", bytes(4, 5), "node-service");

        URL resourceUrl = url("hdfs", "yarn", 8020, "/apps/app.jar");
        LocalResource localResource = localResource(
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

        ContainerLaunchContext launchContext = containerLaunchContext(
                localResources,
                environment,
                Arrays.asList("echo starting", "java -jar app.jar"),
                serviceData,
                byteBuffer(6, 6, 6),
                acls);
        ApplicationSubmissionContext submission = applicationSubmissionContext(
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
        assertThat(applicationId).isEqualTo(applicationId(123456789L, 42));
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
        ApplicationId applicationId = applicationId(2000L, 1);
        ApplicationAttemptId attemptId = applicationAttemptId(applicationId, 1);
        ContainerId containerId = containerId(attemptId, 1);
        Priority priority = priority(1);
        Resource resource = resource(1024, 1);
        ResourceRequest anyRequest = resourceRequest(priority, ResourceRequest.ANY, resource, 3, true);
        ResourceRequest rackRequest = resourceRequest(priority, "/rack1", resource, 1, false);
        Token token = token(bytes(10), "container", bytes(11), "nm");
        Container container = container(
                containerId,
                nodeId("worker", 1234),
                "worker:8042",
                resource,
                priority,
                token);
        ContainerStatus status = containerStatus(
                containerId,
                ContainerState.COMPLETE,
                "finished",
                0);
        PreemptionContainer preemptedContainer = preemptionContainer(containerId);
        PreemptionResourceRequest preemptionRequest = preemptionResourceRequest(anyRequest);
        StrictPreemptionContract strictContract = strictPreemptionContract(
                Collections.singleton(preemptedContainer));
        PreemptionContract contract = preemptionContract(
                Collections.singletonList(preemptionRequest),
                Collections.singleton(preemptedContainer));
        PreemptionMessage message = preemptionMessage(strictContract, contract);
        ResourceBlacklistRequest blacklist = resourceBlacklistRequest(
                Collections.singletonList("bad-node"),
                Collections.singletonList("recovered-node"));
        NMToken nmToken = nmToken(container.getNodeId(), token);

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
        ApplicationId applicationId = applicationId(3000L, 2);
        ApplicationAttemptId attemptId = applicationAttemptId(applicationId, 4);
        Token clientToken = token(bytes(1), "client", bytes(2), "am");
        Token amRmToken = token(bytes(3), "amrm", bytes(4), "rm");
        ApplicationResourceUsageReport usage = applicationResourceUsageReport(
                2,
                1,
                resource(2048, 2),
                resource(1024, 1),
                resource(4096, 4),
                600L,
                60L);
        ApplicationReport report = applicationReport(
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
        NodeReport nodeReport = nodeReport(
                nodeId("node1", 8041),
                NodeState.RUNNING,
                "node1:8042",
                "/rack1",
                resource(512, 1),
                resource(8192, 8),
                2,
                "healthy",
                123L);
        QueueInfo childQueue = queueInfo(
                "child",
                0.25f,
                0.50f,
                0.10f,
                Collections.<QueueInfo>emptyList(),
                Collections.<ApplicationReport>emptyList(),
                QueueState.RUNNING,
                Collections.<String>emptySet(),
                "");
        QueueInfo queueInfo = queueInfo(
                "root",
                1.0f,
                1.0f,
                0.25f,
                Collections.singletonList(childQueue),
                Collections.singletonList(report),
                QueueState.RUNNING,
                Collections.singleton("gpu"),
                "gpu");
        QueueUserACLInfo aclInfo = queueUserACLInfo(
                "root",
                Arrays.asList(QueueACL.SUBMIT_APPLICATIONS, QueueACL.ADMINISTER_QUEUE));
        YarnClusterMetrics metrics = yarnClusterMetrics(5);
        SerializedException serialized = serializedException(
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
        ApplicationId applicationId = applicationId(4000L, 1);
        ApplicationSubmissionContext submission = applicationSubmissionContext(
                applicationId,
                "client-app",
                "default",
                priority(0),
                containerLaunchContext(
                        Collections.<String, LocalResource>emptyMap(),
                        Collections.<String, String>emptyMap(),
                        Collections.singletonList("true"),
                        Collections.<String, ByteBuffer>emptyMap(),
                        byteBuffer(1),
                        Collections.<ApplicationAccessType, String>emptyMap()),
                false,
                true,
                1,
                resource(256, 1));
        ApplicationReport report = applicationReport(
                applicationId,
                applicationAttemptId(applicationId, 1),
                "alice",
                "default",
                "client-app",
                "host",
                1,
                token(bytes(2), "client", bytes(3), "am"),
                YarnApplicationState.ACCEPTED,
                "",
                "N/A",
                1L,
                0L,
                FinalApplicationStatus.UNDEFINED,
                applicationResourceUsageReport(
                        0,
                        0,
                        resource(0, 0),
                        resource(0, 0),
                        resource(0, 0),
                        0L,
                        0L),
                "N/A",
                0.0f,
                "YARN",
                token(bytes(4), "amrm", bytes(5), "rm"));
        QueueInfo queueInfo = queueInfo(
                "default",
                1.0f,
                1.0f,
                0.0f,
                Collections.<QueueInfo>emptyList(),
                Collections.singletonList(report),
                QueueState.RUNNING,
                Collections.<String>emptySet(),
                "");
        NodeReport nodeReport = nodeReport(
                nodeId("node", 8041),
                NodeState.RUNNING,
                "node:8042",
                "/default-rack",
                resource(0, 0),
                resource(4096, 4),
                0,
                "healthy",
                1L);

        SubmitApplicationRequest submitRequest = submitApplicationRequest(submission);
        SubmitApplicationResponse submitResponse = submitApplicationResponse();
        GetNewApplicationRequest newApplicationRequest = getNewApplicationRequest();
        GetNewApplicationResponse newApplicationResponse = getNewApplicationResponse(
                applicationId,
                resource(128, 1),
                resource(8192, 8));
        GetApplicationReportRequest reportRequest = getApplicationReportRequest(applicationId);
        GetApplicationReportResponse reportResponse = getApplicationReportResponse(report);
        GetApplicationsRequest applicationsRequest = getApplicationsRequest(
                Collections.singleton("YARN"),
                EnumSet.of(YarnApplicationState.ACCEPTED, YarnApplicationState.RUNNING));
        GetApplicationsResponse applicationsResponse = getApplicationsResponse(
                Collections.singletonList(report));
        GetClusterMetricsRequest metricsRequest = getClusterMetricsRequest();
        GetClusterMetricsResponse metricsResponse = getClusterMetricsResponse(
                yarnClusterMetrics(1));
        GetClusterNodesRequest nodesRequest = getClusterNodesRequest(EnumSet.of(NodeState.RUNNING));
        GetClusterNodesResponse nodesResponse = getClusterNodesResponse(
                Collections.singletonList(nodeReport));
        GetQueueInfoRequest queueInfoRequest = getQueueInfoRequest("default", true, true, false);
        GetQueueInfoResponse queueInfoResponse = getQueueInfoResponse(queueInfo);
        GetQueueUserAclsInfoRequest aclsRequest = getQueueUserAclsInfoRequest();
        GetQueueUserAclsInfoResponse aclsResponse = getQueueUserAclsInfoResponse(Collections.singletonList(
                queueUserACLInfo("default", Collections.singletonList(QueueACL.SUBMIT_APPLICATIONS))));
        KillApplicationRequest killRequest = killApplicationRequest(applicationId);
        KillApplicationResponse killResponse = killApplicationResponse(true);

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
        ApplicationId applicationId = applicationId(5000L, 9);
        ApplicationAttemptId attemptId = applicationAttemptId(applicationId, 2);
        ContainerId containerId = containerId(attemptId, 12);
        Priority priority = priority(2);
        Resource resource = resource(512, 1);
        ResourceRequest ask = resourceRequest(priority, ResourceRequest.ANY, resource, 2);
        Token containerToken = token(bytes(12), "container", bytes(13), "node");
        Container container = container(
                containerId,
                nodeId("node", 8041),
                "node:8042",
                resource,
                priority,
                containerToken);
        ContainerStatus status = containerStatus(containerId, ContainerState.RUNNING, "running", -1000);
        ResourceBlacklistRequest blacklist = resourceBlacklistRequest(
                Collections.singletonList("bad"),
                Collections.<String>emptyList());
        PreemptionContainer preemptionContainer = preemptionContainer(containerId);
        PreemptionMessage preemptionMessage = preemptionMessage(
                strictPreemptionContract(Collections.singleton(preemptionContainer)),
                preemptionContract(
                        Collections.singletonList(preemptionResourceRequest(ask)),
                        Collections.singleton(preemptionContainer)));
        NMToken nmToken = nmToken(container.getNodeId(), containerToken);
        NodeReport nodeReport = nodeReport(
                container.getNodeId(),
                NodeState.RUNNING,
                "node:8042",
                "/rack",
                resource,
                resource(4096, 4),
                1,
                "ok",
                5L);
        Map<ApplicationAccessType, String> acls = Collections.singletonMap(ApplicationAccessType.MODIFY_APP, "admin");
        ContainerLaunchContext launchContext = containerLaunchContext(
                Collections.<String, LocalResource>emptyMap(),
                Collections.singletonMap("A", "B"),
                Collections.singletonList("sleep 1"),
                Collections.<String, ByteBuffer>emptyMap(),
                byteBuffer(1, 2),
                acls);
        Map<String, ByteBuffer> serviceMetadata = Collections.singletonMap("aux", byteBuffer(7));
        SerializedException failure = serializedException(new IllegalArgumentException("container failed"));
        Map<ContainerId, SerializedException> failedRequests = Collections.singletonMap(containerId, failure);

        RegisterApplicationMasterRequest registerRequest = registerApplicationMasterRequest(
                "am-host",
                12000,
                "http://am");
        RegisterApplicationMasterResponse registerResponse = registerApplicationMasterResponse(
                resource(128, 1),
                resource(8192, 8),
                acls,
                byteBuffer(3, 4),
                Collections.singletonList(container),
                "default",
                Collections.singletonList(nmToken));
        AllocateRequest allocateRequest = allocateRequest(
                3,
                0.5f,
                Collections.singletonList(ask),
                Collections.singletonList(containerId),
                blacklist);
        AllocateResponse allocateResponse = allocateResponse(
                4,
                Collections.singletonList(status),
                Collections.singletonList(container),
                Collections.singletonList(nodeReport),
                resource(16384, 16),
                AMCommand.AM_RESYNC,
                6,
                preemptionMessage,
                Collections.singletonList(nmToken));
        FinishApplicationMasterRequest finishRequest = finishApplicationMasterRequest(
                FinalApplicationStatus.SUCCEEDED,
                "done",
                "http://done");
        FinishApplicationMasterResponse finishResponse = finishApplicationMasterResponse(true);
        StartContainerRequest startContainerRequest = startContainerRequest(launchContext, containerToken);
        StartContainersRequest startContainersRequest = startContainersRequest(
                Collections.singletonList(startContainerRequest));
        StartContainersResponse startContainersResponse = startContainersResponse(
                serviceMetadata,
                Collections.singletonList(containerId),
                failedRequests);
        StopContainersRequest stopContainersRequest = stopContainersRequest(
                Collections.singletonList(containerId));
        StopContainersResponse stopContainersResponse = stopContainersResponse(
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
        ApplicationId applicationId = applicationId(6000L, 3);
        ApplicationAttemptId attemptId = applicationAttemptId(applicationId, 1);
        ContainerId completedContainerId = containerId(attemptId, 1);
        ContainerId failedContainerId = containerId(attemptId, 2);
        ContainerStatus completedStatus = containerStatus(
                completedContainerId,
                ContainerState.COMPLETE,
                "finished successfully",
                ContainerExitStatus.SUCCESS);
        SerializedException failure = serializedException(
                new IllegalArgumentException("container status unavailable"));

        GetContainerStatusesRequest request = getContainerStatusesRequest(
                Arrays.asList(completedContainerId, failedContainerId));
        GetContainerStatusesResponse response = getContainerStatusesResponse(
                Collections.singletonList(completedStatus),
                Collections.singletonMap(failedContainerId, failure));

        assertThat(request.getContainerIds()).containsExactly(completedContainerId, failedContainerId);
        assertThat(response.getContainerStatuses()).containsExactly(completedStatus);
        assertThat(completedStatus.getExitStatus()).isEqualTo(ContainerExitStatus.SUCCESS);
        assertThat(response.getFailedRequests()).containsEntry(failedContainerId, failure);
    }

    @Test
    void createsDelegationTokenProtocolRecords() {
        Token delegationToken = token(
                bytes(21, 22, 23),
                "RM_DELEGATION_TOKEN",
                bytes(24, 25),
                "resource-manager");
        GetDelegationTokenRequest getRequest = getDelegationTokenRequest("history-server");
        GetDelegationTokenResponse getResponse = getDelegationTokenResponse(delegationToken);
        RenewDelegationTokenRequest renewRequest = renewDelegationTokenRequest(delegationToken);
        RenewDelegationTokenResponse renewResponse = renewDelegationTokenResponse(123456789L);
        CancelDelegationTokenRequest cancelRequest = cancelDelegationTokenRequest(delegationToken);
        CancelDelegationTokenResponse cancelResponse = cancelDelegationTokenResponse();

        assertThat(getRequest.getRenewer()).isEqualTo("history-server");
        assertThat(getResponse.getRMDelegationToken()).isEqualTo(delegationToken);
        assertThat(renewRequest.getDelegationToken()).isEqualTo(delegationToken);
        assertThat(renewResponse.getNextExpirationTime()).isEqualTo(123456789L);
        assertThat(cancelRequest.getDelegationToken()).isEqualTo(delegationToken);
        assertThat(cancelResponse).isNotNull();
    }

    @Test
    void mutatesRecordsThroughSetters() {
        Resource resource = resource(1, 1);
        resource.setMemory(4096);
        resource.setVirtualCores(4);
        Priority priority = priority(1);
        priority.setPriority(9);
        ResourceRequest request = resourceRequest(priority, "old", resource, 1);
        request.setResourceName("new");
        request.setNumContainers(5);
        request.setRelaxLocality(false);
        URL url = url("file", null, 0, "/tmp/a");
        url.setScheme("hdfs");
        url.setUserInfo("user");
        url.setHost("namenode");
        url.setPort(8020);
        url.setFile("/tmp/b");
        LocalResource localResource = localResource(
                url,
                LocalResourceType.FILE,
                LocalResourceVisibility.PRIVATE,
                1L,
                2L);
        localResource.setSize(3L);
        localResource.setTimestamp(4L);
        localResource.setPattern("*.txt");
        Token token = token(bytes(1), "kind", bytes(2), "service");
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

    private static ApplicationId applicationId(long clusterTimestamp, int id) {
        return new ApplicationIdPBImpl(YarnProtos.ApplicationIdProto.newBuilder()
                .setClusterTimestamp(clusterTimestamp)
                .setId(id)
                .build());
    }

    private static ApplicationAttemptId applicationAttemptId(ApplicationId applicationId, int attemptId) {
        return new ApplicationAttemptIdPBImpl(YarnProtos.ApplicationAttemptIdProto.newBuilder()
                .setApplicationId(((ApplicationIdPBImpl) applicationId).getProto())
                .setAttemptId(attemptId)
                .build());
    }

    private static ContainerId containerId(ApplicationAttemptId applicationAttemptId, long containerId) {
        return new ContainerIdPBImpl(YarnProtos.ContainerIdProto.newBuilder()
                .setAppAttemptId(((ApplicationAttemptIdPBImpl) applicationAttemptId).getProto())
                .setId(containerId)
                .build());
    }

    private static NodeId nodeId(String host, int port) {
        return new NodeIdPBImpl(YarnProtos.NodeIdProto.newBuilder()
                .setHost(host)
                .setPort(port)
                .build());
    }

    private static Priority priority(int priority) {
        PriorityPBImpl record = new PriorityPBImpl();
        record.setPriority(priority);
        return record;
    }

    private static Resource resource(int memory, int virtualCores) {
        ResourcePBImpl record = new ResourcePBImpl();
        record.setMemory(memory);
        record.setVirtualCores(virtualCores);
        return record;
    }

    private static Token token(byte[] identifier, String kind, byte[] password, String service) {
        TokenPBImpl record = new TokenPBImpl();
        record.setIdentifier(ByteBuffer.wrap(identifier));
        record.setKind(kind);
        record.setPassword(ByteBuffer.wrap(password));
        record.setService(service);
        return record;
    }

    private static URL url(String scheme, String host, int port, String file) {
        URLPBImpl record = new URLPBImpl();
        record.setScheme(scheme);
        record.setHost(host);
        record.setPort(port);
        record.setFile(file);
        return record;
    }

    private static LocalResource localResource(URL resource, LocalResourceType type, LocalResourceVisibility visibility,
            long size, long timestamp) {
        return localResource(resource, type, visibility, size, timestamp, null);
    }

    private static LocalResource localResource(URL resource, LocalResourceType type, LocalResourceVisibility visibility,
            long size, long timestamp, String pattern) {
        LocalResourcePBImpl record = new LocalResourcePBImpl();
        record.setResource(resource);
        record.setType(type);
        record.setVisibility(visibility);
        record.setSize(size);
        record.setTimestamp(timestamp);
        record.setPattern(pattern);
        return record;
    }

    private static ContainerLaunchContext containerLaunchContext(Map<String, LocalResource> localResources,
            Map<String, String> environment, List<String> commands, Map<String, ByteBuffer> serviceData,
            ByteBuffer tokens, Map<ApplicationAccessType, String> applicationAcls) {
        ContainerLaunchContextPBImpl record = new ContainerLaunchContextPBImpl();
        record.setLocalResources(localResources);
        record.setEnvironment(environment);
        record.setCommands(commands);
        record.setServiceData(serviceData);
        record.setTokens(tokens);
        record.setApplicationACLs(applicationAcls);
        return record;
    }

    private static ApplicationSubmissionContext applicationSubmissionContext(ApplicationId applicationId,
            String applicationName, String queue, Priority priority, ContainerLaunchContext amContainer,
            boolean unmanagedAm, boolean cancelTokensWhenComplete, int maxAppAttempts, Resource resource) {
        return applicationSubmissionContext(applicationId, applicationName, queue, priority, amContainer, unmanagedAm,
                cancelTokensWhenComplete, maxAppAttempts, resource, null);
    }

    private static ApplicationSubmissionContext applicationSubmissionContext(ApplicationId applicationId,
            String applicationName, String queue, Priority priority, ContainerLaunchContext amContainer,
            boolean unmanagedAm, boolean cancelTokensWhenComplete, int maxAppAttempts, Resource resource,
            String applicationType) {
        ApplicationSubmissionContextPBImpl record = new ApplicationSubmissionContextPBImpl();
        record.setApplicationId(applicationId);
        record.setApplicationName(applicationName);
        record.setQueue(queue);
        record.setPriority(priority);
        record.setAMContainerSpec(amContainer);
        record.setUnmanagedAM(unmanagedAm);
        record.setCancelTokensWhenComplete(cancelTokensWhenComplete);
        record.setMaxAppAttempts(maxAppAttempts);
        record.setResource(resource);
        record.setApplicationType(applicationType);
        return record;
    }

    private static ResourceRequest resourceRequest(Priority priority, String resourceName, Resource capability,
            int numContainers) {
        return resourceRequest(priority, resourceName, capability, numContainers, true);
    }

    private static ResourceRequest resourceRequest(Priority priority, String resourceName, Resource capability,
            int numContainers, boolean relaxLocality) {
        ResourceRequestPBImpl record = new ResourceRequestPBImpl();
        record.setPriority(priority);
        record.setResourceName(resourceName);
        record.setCapability(capability);
        record.setNumContainers(numContainers);
        record.setRelaxLocality(relaxLocality);
        return record;
    }

    private static Container container(ContainerId id, NodeId nodeId, String nodeHttpAddress, Resource resource,
            Priority priority, Token containerToken) {
        ContainerPBImpl record = new ContainerPBImpl();
        record.setId(id);
        record.setNodeId(nodeId);
        record.setNodeHttpAddress(nodeHttpAddress);
        record.setResource(resource);
        record.setPriority(priority);
        record.setContainerToken(containerToken);
        return record;
    }

    private static ContainerStatus containerStatus(ContainerId containerId, ContainerState state, String diagnostics,
            int exitStatus) {
        ContainerStatusPBImpl record = new ContainerStatusPBImpl();
        record.setContainerId(containerId);
        record.setState(state);
        record.setDiagnostics(diagnostics);
        record.setExitStatus(exitStatus);
        return record;
    }

    private static PreemptionContainer preemptionContainer(ContainerId id) {
        PreemptionContainerPBImpl record = new PreemptionContainerPBImpl();
        record.setId(id);
        return record;
    }

    private static PreemptionResourceRequest preemptionResourceRequest(ResourceRequest resourceRequest) {
        PreemptionResourceRequestPBImpl record = new PreemptionResourceRequestPBImpl();
        record.setResourceRequest(resourceRequest);
        return record;
    }

    private static StrictPreemptionContract strictPreemptionContract(Set<PreemptionContainer> containers) {
        StrictPreemptionContractPBImpl record = new StrictPreemptionContractPBImpl();
        record.setContainers(containers);
        return record;
    }

    private static PreemptionContract preemptionContract(List<PreemptionResourceRequest> resourceRequests,
            Set<PreemptionContainer> containers) {
        PreemptionContractPBImpl record = new PreemptionContractPBImpl();
        record.setResourceRequest(resourceRequests);
        record.setContainers(containers);
        return record;
    }

    private static PreemptionMessage preemptionMessage(StrictPreemptionContract strictContract,
            PreemptionContract contract) {
        PreemptionMessagePBImpl record = new PreemptionMessagePBImpl();
        record.setStrictContract(strictContract);
        record.setContract(contract);
        return record;
    }

    private static ResourceBlacklistRequest resourceBlacklistRequest(List<String> additions, List<String> removals) {
        ResourceBlacklistRequestPBImpl record = new ResourceBlacklistRequestPBImpl();
        record.setBlacklistAdditions(additions);
        record.setBlacklistRemovals(removals);
        return record;
    }

    private static NMToken nmToken(NodeId nodeId, Token token) {
        NMTokenPBImpl record = new NMTokenPBImpl();
        record.setNodeId(nodeId);
        record.setToken(token);
        return record;
    }

    private static ApplicationResourceUsageReport applicationResourceUsageReport(int usedContainers,
            int reservedContainers, Resource usedResources, Resource reservedResources, Resource neededResources,
            long memorySeconds, long vcoreSeconds) {
        ApplicationResourceUsageReportPBImpl record = new ApplicationResourceUsageReportPBImpl();
        record.setNumUsedContainers(usedContainers);
        record.setNumReservedContainers(reservedContainers);
        record.setUsedResources(usedResources);
        record.setReservedResources(reservedResources);
        record.setNeededResources(neededResources);
        record.setMemorySeconds(memorySeconds);
        record.setVcoreSeconds(vcoreSeconds);
        return record;
    }

    private static ApplicationReport applicationReport(ApplicationId applicationId, ApplicationAttemptId attemptId,
            String user, String queue, String name, String host, int rpcPort, Token clientToken,
            YarnApplicationState state, String diagnostics, String trackingUrl, long startTime, long finishTime,
            FinalApplicationStatus finalStatus, ApplicationResourceUsageReport usageReport, String originalTrackingUrl,
            float progress, String applicationType, Token amRmToken) {
        ApplicationReportPBImpl record = new ApplicationReportPBImpl();
        record.setApplicationId(applicationId);
        record.setCurrentApplicationAttemptId(attemptId);
        record.setUser(user);
        record.setQueue(queue);
        record.setName(name);
        record.setHost(host);
        record.setRpcPort(rpcPort);
        record.setClientToAMToken(clientToken);
        record.setYarnApplicationState(state);
        record.setDiagnostics(diagnostics);
        record.setTrackingUrl(trackingUrl);
        record.setStartTime(startTime);
        record.setFinishTime(finishTime);
        record.setFinalApplicationStatus(finalStatus);
        record.setApplicationResourceUsageReport(usageReport);
        record.setOriginalTrackingUrl(originalTrackingUrl);
        record.setProgress(progress);
        record.setApplicationType(applicationType);
        record.setAMRMToken(amRmToken);
        return record;
    }

    private static NodeReport nodeReport(NodeId nodeId, NodeState nodeState, String httpAddress, String rackName,
            Resource used, Resource capability, int numContainers, String healthReport, long lastHealthReportTime) {
        NodeReportPBImpl record = new NodeReportPBImpl();
        record.setNodeId(nodeId);
        record.setNodeState(nodeState);
        record.setHttpAddress(httpAddress);
        record.setRackName(rackName);
        record.setUsed(used);
        record.setCapability(capability);
        record.setNumContainers(numContainers);
        record.setHealthReport(healthReport);
        record.setLastHealthReportTime(lastHealthReportTime);
        return record;
    }

    private static QueueInfo queueInfo(String queueName, float capacity, float maximumCapacity, float currentCapacity,
            List<QueueInfo> childQueues, List<ApplicationReport> applications, QueueState queueState,
            Set<String> accessibleNodeLabels, String defaultNodeLabelExpression) {
        QueueInfoPBImpl record = new QueueInfoPBImpl();
        record.setQueueName(queueName);
        record.setCapacity(capacity);
        record.setMaximumCapacity(maximumCapacity);
        record.setCurrentCapacity(currentCapacity);
        record.setChildQueues(childQueues);
        record.setApplications(applications);
        record.setQueueState(queueState);
        record.setAccessibleNodeLabels(accessibleNodeLabels);
        record.setDefaultNodeLabelExpression(defaultNodeLabelExpression);
        return record;
    }

    private static QueueUserACLInfo queueUserACLInfo(String queueName, List<QueueACL> userAcls) {
        QueueUserACLInfoPBImpl record = new QueueUserACLInfoPBImpl();
        record.setQueueName(queueName);
        record.setUserAcls(userAcls);
        return record;
    }

    private static YarnClusterMetrics yarnClusterMetrics(int numNodeManagers) {
        YarnClusterMetricsPBImpl record = new YarnClusterMetricsPBImpl();
        record.setNumNodeManagers(numNodeManagers);
        return record;
    }

    private static SerializedException serializedException(Throwable throwable) {
        SerializedExceptionPBImpl record = new SerializedExceptionPBImpl();
        record.init(throwable);
        return record;
    }

    private static SubmitApplicationRequest submitApplicationRequest(ApplicationSubmissionContext context) {
        SubmitApplicationRequestPBImpl record = new SubmitApplicationRequestPBImpl();
        record.setApplicationSubmissionContext(context);
        return record;
    }

    private static SubmitApplicationResponse submitApplicationResponse() {
        return new SubmitApplicationResponsePBImpl();
    }

    private static GetNewApplicationRequest getNewApplicationRequest() {
        return new GetNewApplicationRequestPBImpl();
    }

    private static GetNewApplicationResponse getNewApplicationResponse(ApplicationId applicationId,
            Resource minimumCapability, Resource maximumCapability) {
        GetNewApplicationResponsePBImpl record = new GetNewApplicationResponsePBImpl();
        record.setApplicationId(applicationId);
        record.setMaximumResourceCapability(maximumCapability);
        return record;
    }

    private static GetApplicationReportRequest getApplicationReportRequest(ApplicationId applicationId) {
        GetApplicationReportRequestPBImpl record = new GetApplicationReportRequestPBImpl();
        record.setApplicationId(applicationId);
        return record;
    }

    private static GetApplicationReportResponse getApplicationReportResponse(ApplicationReport report) {
        GetApplicationReportResponsePBImpl record = new GetApplicationReportResponsePBImpl();
        record.setApplicationReport(report);
        return record;
    }

    private static GetApplicationsRequest getApplicationsRequest(Set<String> applicationTypes,
            EnumSet<YarnApplicationState> applicationStates) {
        GetApplicationsRequestPBImpl record = new GetApplicationsRequestPBImpl();
        record.setApplicationTypes(applicationTypes);
        record.setApplicationStates(applicationStates);
        return record;
    }

    private static GetApplicationsResponse getApplicationsResponse(List<ApplicationReport> reports) {
        GetApplicationsResponsePBImpl record = new GetApplicationsResponsePBImpl();
        record.setApplicationList(reports);
        return record;
    }

    private static GetClusterMetricsRequest getClusterMetricsRequest() {
        return new GetClusterMetricsRequestPBImpl();
    }

    private static GetClusterMetricsResponse getClusterMetricsResponse(YarnClusterMetrics metrics) {
        GetClusterMetricsResponsePBImpl record = new GetClusterMetricsResponsePBImpl();
        record.setClusterMetrics(metrics);
        return record;
    }

    private static GetClusterNodesRequest getClusterNodesRequest(EnumSet<NodeState> nodeStates) {
        GetClusterNodesRequestPBImpl record = new GetClusterNodesRequestPBImpl();
        record.setNodeStates(nodeStates);
        return record;
    }

    private static GetClusterNodesResponse getClusterNodesResponse(List<NodeReport> nodeReports) {
        GetClusterNodesResponsePBImpl record = new GetClusterNodesResponsePBImpl();
        record.setNodeReports(nodeReports);
        return record;
    }

    private static GetQueueInfoRequest getQueueInfoRequest(String queueName, boolean includeApplications,
            boolean includeChildQueues, boolean recursive) {
        GetQueueInfoRequestPBImpl record = new GetQueueInfoRequestPBImpl();
        record.setQueueName(queueName);
        record.setIncludeApplications(includeApplications);
        record.setIncludeChildQueues(includeChildQueues);
        record.setRecursive(recursive);
        return record;
    }

    private static GetQueueInfoResponse getQueueInfoResponse(QueueInfo queueInfo) {
        GetQueueInfoResponsePBImpl record = new GetQueueInfoResponsePBImpl();
        record.setQueueInfo(queueInfo);
        return record;
    }

    private static GetQueueUserAclsInfoRequest getQueueUserAclsInfoRequest() {
        return new GetQueueUserAclsInfoRequestPBImpl();
    }

    private static GetQueueUserAclsInfoResponse getQueueUserAclsInfoResponse(List<QueueUserACLInfo> aclInfos) {
        GetQueueUserAclsInfoResponsePBImpl record = new GetQueueUserAclsInfoResponsePBImpl();
        record.setUserAclsInfoList(aclInfos);
        return record;
    }

    private static KillApplicationRequest killApplicationRequest(ApplicationId applicationId) {
        KillApplicationRequestPBImpl record = new KillApplicationRequestPBImpl();
        record.setApplicationId(applicationId);
        return record;
    }

    private static KillApplicationResponse killApplicationResponse(boolean isKillCompleted) {
        KillApplicationResponsePBImpl record = new KillApplicationResponsePBImpl();
        record.setIsKillCompleted(isKillCompleted);
        return record;
    }

    private static RegisterApplicationMasterRequest registerApplicationMasterRequest(String host, int rpcPort,
            String trackingUrl) {
        RegisterApplicationMasterRequestPBImpl record = new RegisterApplicationMasterRequestPBImpl();
        record.setHost(host);
        record.setRpcPort(rpcPort);
        record.setTrackingUrl(trackingUrl);
        return record;
    }

    private static RegisterApplicationMasterResponse registerApplicationMasterResponse(Resource minimumCapability,
            Resource maximumCapability, Map<ApplicationAccessType, String> applicationAcls, ByteBuffer masterKey,
            List<Container> containersFromPreviousAttempts, String queue, List<NMToken> nmTokens) {
        RegisterApplicationMasterResponsePBImpl record = new RegisterApplicationMasterResponsePBImpl();
        record.setMaximumResourceCapability(maximumCapability);
        record.setApplicationACLs(applicationAcls);
        record.setClientToAMTokenMasterKey(masterKey);
        record.setContainersFromPreviousAttempts(containersFromPreviousAttempts);
        record.setQueue(queue);
        record.setNMTokensFromPreviousAttempts(nmTokens);
        return record;
    }

    private static AllocateRequest allocateRequest(int responseId, float progress, List<ResourceRequest> askList,
            List<ContainerId> releaseList, ResourceBlacklistRequest blacklistRequest) {
        AllocateRequestPBImpl record = new AllocateRequestPBImpl();
        record.setResponseId(responseId);
        record.setProgress(progress);
        record.setAskList(askList);
        record.setReleaseList(releaseList);
        record.setResourceBlacklistRequest(blacklistRequest);
        return record;
    }

    private static AllocateResponse allocateResponse(int responseId, List<ContainerStatus> completedStatuses,
            List<Container> allocatedContainers, List<NodeReport> updatedNodes, Resource availableResources,
            AMCommand amCommand, int numClusterNodes, PreemptionMessage preemptionMessage, List<NMToken> nmTokens) {
        AllocateResponsePBImpl record = new AllocateResponsePBImpl();
        record.setResponseId(responseId);
        record.setCompletedContainersStatuses(completedStatuses);
        record.setAllocatedContainers(allocatedContainers);
        record.setUpdatedNodes(updatedNodes);
        record.setAvailableResources(availableResources);
        record.setAMCommand(amCommand);
        record.setNumClusterNodes(numClusterNodes);
        record.setPreemptionMessage(preemptionMessage);
        record.setNMTokens(nmTokens);
        return record;
    }

    private static FinishApplicationMasterRequest finishApplicationMasterRequest(FinalApplicationStatus status,
            String diagnostics, String trackingUrl) {
        FinishApplicationMasterRequestPBImpl record = new FinishApplicationMasterRequestPBImpl();
        record.setFinalApplicationStatus(status);
        record.setDiagnostics(diagnostics);
        record.setTrackingUrl(trackingUrl);
        return record;
    }

    private static FinishApplicationMasterResponse finishApplicationMasterResponse(boolean isUnregistered) {
        FinishApplicationMasterResponsePBImpl record = new FinishApplicationMasterResponsePBImpl();
        record.setIsUnregistered(isUnregistered);
        return record;
    }

    private static StartContainerRequest startContainerRequest(ContainerLaunchContext context, Token token) {
        StartContainerRequestPBImpl record = new StartContainerRequestPBImpl();
        record.setContainerLaunchContext(context);
        record.setContainerToken(token);
        return record;
    }

    private static StartContainersRequest startContainersRequest(List<StartContainerRequest> requests) {
        StartContainersRequestPBImpl record = new StartContainersRequestPBImpl();
        record.setStartContainerRequests(requests);
        return record;
    }

    private static StartContainersResponse startContainersResponse(Map<String, ByteBuffer> serviceMetadata,
            List<ContainerId> successfullyStartedContainers, Map<ContainerId, SerializedException> failedRequests) {
        StartContainersResponsePBImpl record = new StartContainersResponsePBImpl();
        record.setAllServicesMetaData(serviceMetadata);
        record.setSuccessfullyStartedContainers(successfullyStartedContainers);
        record.setFailedRequests(failedRequests);
        return record;
    }

    private static StopContainersRequest stopContainersRequest(List<ContainerId> containerIds) {
        StopContainersRequestPBImpl record = new StopContainersRequestPBImpl();
        record.setContainerIds(containerIds);
        return record;
    }

    private static StopContainersResponse stopContainersResponse(List<ContainerId> successfullyStoppedContainers,
            Map<ContainerId, SerializedException> failedRequests) {
        StopContainersResponsePBImpl record = new StopContainersResponsePBImpl();
        record.setSuccessfullyStoppedContainers(successfullyStoppedContainers);
        record.setFailedRequests(failedRequests);
        return record;
    }

    private static GetContainerStatusesRequest getContainerStatusesRequest(List<ContainerId> containerIds) {
        GetContainerStatusesRequestPBImpl record = new GetContainerStatusesRequestPBImpl();
        record.setContainerIds(containerIds);
        return record;
    }

    private static GetContainerStatusesResponse getContainerStatusesResponse(List<ContainerStatus> statuses,
            Map<ContainerId, SerializedException> failedRequests) {
        GetContainerStatusesResponsePBImpl record = new GetContainerStatusesResponsePBImpl();
        record.setContainerStatuses(statuses);
        record.setFailedRequests(failedRequests);
        return record;
    }

    private static GetDelegationTokenRequest getDelegationTokenRequest(String renewer) {
        GetDelegationTokenRequestPBImpl record = new GetDelegationTokenRequestPBImpl();
        record.setRenewer(renewer);
        return record;
    }

    private static GetDelegationTokenResponse getDelegationTokenResponse(Token token) {
        GetDelegationTokenResponsePBImpl record = new GetDelegationTokenResponsePBImpl();
        record.setRMDelegationToken(token);
        return record;
    }

    private static RenewDelegationTokenRequest renewDelegationTokenRequest(Token token) {
        RenewDelegationTokenRequestPBImpl record = new RenewDelegationTokenRequestPBImpl();
        record.setDelegationToken(token);
        return record;
    }

    private static RenewDelegationTokenResponse renewDelegationTokenResponse(long nextExpirationTime) {
        RenewDelegationTokenResponsePBImpl record = new RenewDelegationTokenResponsePBImpl();
        record.setNextExpirationTime(nextExpirationTime);
        return record;
    }

    private static CancelDelegationTokenRequest cancelDelegationTokenRequest(Token token) {
        CancelDelegationTokenRequestPBImpl record = new CancelDelegationTokenRequestPBImpl();
        record.setDelegationToken(token);
        return record;
    }

    private static CancelDelegationTokenResponse cancelDelegationTokenResponse() {
        return new CancelDelegationTokenResponsePBImpl();
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
