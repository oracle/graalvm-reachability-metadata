/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_yarn_server_common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.http.FilterContainer;
import org.apache.hadoop.security.KerberosInfo;
import org.apache.hadoop.yarn.api.records.ApplicationAttemptId;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.Container;
import org.apache.hadoop.yarn.api.records.ContainerId;
import org.apache.hadoop.yarn.api.records.ContainerState;
import org.apache.hadoop.yarn.api.records.ContainerStatus;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.hadoop.yarn.api.records.LocalResourceType;
import org.apache.hadoop.yarn.api.records.LocalResourceVisibility;
import org.apache.hadoop.yarn.api.records.NodeId;
import org.apache.hadoop.yarn.api.records.NodeLabel;
import org.apache.hadoop.yarn.api.records.Priority;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.api.records.ResourceRequest;
import org.apache.hadoop.yarn.api.records.ResourceUtilization;
import org.apache.hadoop.yarn.api.records.Token;
import org.apache.hadoop.yarn.api.records.URL;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.security.client.RMDelegationTokenIdentifier;
import org.apache.hadoop.yarn.server.RMNMSecurityInfoClass;
import org.apache.hadoop.yarn.server.api.ResourceTrackerPB;
import org.apache.hadoop.yarn.server.api.SCMUploaderProtocolPB;
import org.apache.hadoop.yarn.server.api.protocolrecords.NMContainerStatus;
import org.apache.hadoop.yarn.server.api.protocolrecords.NodeHeartbeatRequest;
import org.apache.hadoop.yarn.server.api.protocolrecords.NodeHeartbeatResponse;
import org.apache.hadoop.yarn.server.api.protocolrecords.RegisterNodeManagerRequest;
import org.apache.hadoop.yarn.server.api.protocolrecords.RegisterNodeManagerResponse;
import org.apache.hadoop.yarn.server.api.protocolrecords.SCMUploaderCanUploadRequest;
import org.apache.hadoop.yarn.server.api.protocolrecords.SCMUploaderCanUploadResponse;
import org.apache.hadoop.yarn.server.api.protocolrecords.SCMUploaderNotifyRequest;
import org.apache.hadoop.yarn.server.api.protocolrecords.SCMUploaderNotifyResponse;
import org.apache.hadoop.yarn.server.api.protocolrecords.impl.pb.NMContainerStatusPBImpl;
import org.apache.hadoop.yarn.server.api.protocolrecords.impl.pb.NodeHeartbeatRequestPBImpl;
import org.apache.hadoop.yarn.server.api.protocolrecords.impl.pb.NodeHeartbeatResponsePBImpl;
import org.apache.hadoop.yarn.server.api.protocolrecords.impl.pb.RegisterNodeManagerRequestPBImpl;
import org.apache.hadoop.yarn.server.api.protocolrecords.impl.pb.RegisterNodeManagerResponsePBImpl;
import org.apache.hadoop.yarn.server.api.protocolrecords.impl.pb.SCMUploaderCanUploadRequestPBImpl;
import org.apache.hadoop.yarn.server.api.protocolrecords.impl.pb.SCMUploaderCanUploadResponsePBImpl;
import org.apache.hadoop.yarn.server.api.protocolrecords.impl.pb.SCMUploaderNotifyRequestPBImpl;
import org.apache.hadoop.yarn.server.api.protocolrecords.impl.pb.SCMUploaderNotifyResponsePBImpl;
import org.apache.hadoop.yarn.server.api.records.MasterKey;
import org.apache.hadoop.yarn.server.api.records.NodeAction;
import org.apache.hadoop.yarn.server.api.records.NodeHealthStatus;
import org.apache.hadoop.yarn.server.api.records.NodeStatus;
import org.apache.hadoop.yarn.server.api.records.impl.pb.MasterKeyPBImpl;
import org.apache.hadoop.yarn.server.api.records.impl.pb.NodeHealthStatusPBImpl;
import org.apache.hadoop.yarn.server.api.records.impl.pb.NodeStatusPBImpl;
import org.apache.hadoop.yarn.server.records.Version;
import org.apache.hadoop.yarn.server.records.impl.pb.VersionPBImpl;
import org.apache.hadoop.yarn.server.security.MasterKeyData;
import org.apache.hadoop.yarn.server.security.http.RMAuthenticationFilter;
import org.apache.hadoop.yarn.server.security.http.RMAuthenticationFilterInitializer;
import org.apache.hadoop.yarn.server.sharedcache.SharedCacheUtil;
import org.apache.hadoop.yarn.server.utils.BuilderUtils;
import org.apache.hadoop.yarn.server.webapp.WebPageUtils;
import org.apache.hadoop.yarn.server.webapp.dao.AppAttemptsInfo;
import org.apache.hadoop.yarn.server.webapp.dao.AppsInfo;
import org.apache.hadoop.yarn.server.webapp.dao.ContainersInfo;
import org.junit.jupiter.api.Test;

public class Hadoop_yarn_server_commonTest {
    @Test
    void nodeManagerRegistrationCarriesResourceContainersAndRunningApplications() {
        NodeId nodeId = NodeId.newInstance("worker.example.test", 45454);
        Resource nodeResource = Resource.newInstance(8192, 4);
        ApplicationId applicationId = ApplicationId.newInstance(1234L, 7);
        ContainerId containerId = containerId(applicationId, 1, 99L);
        Priority priority = Priority.newInstance(3);
        NMContainerStatus containerStatus = newNmContainerStatus(
                containerId,
                1,
                ContainerState.RUNNING,
                Resource.newInstance(1024, 1),
                "healthy container",
                0,
                priority,
                44L);

        RegisterNodeManagerRequest request = newRegisterNodeManagerRequest(
                nodeId,
                8042,
                nodeResource,
                "test-nm-version",
                Collections.singletonList(containerStatus),
                Collections.singletonList(applicationId));

        assertThat(request.getNodeId()).isEqualTo(nodeId);
        assertThat(request.getHttpPort()).isEqualTo(8042);
        assertThat(request.getResource().getMemorySize()).isEqualTo(8192);
        assertThat(request.getResource().getVirtualCores()).isEqualTo(4);
        assertThat(request.getNMVersion()).isEqualTo("test-nm-version");
        assertThat(request.getRunningApplications()).containsExactly(applicationId);

        NMContainerStatus roundTrippedStatus = request.getNMContainerStatuses().get(0);
        assertThat(roundTrippedStatus.getContainerId()).isEqualTo(containerId);
        assertThat(roundTrippedStatus.getVersion()).isEqualTo(1);
        assertThat(roundTrippedStatus.getContainerState()).isEqualTo(ContainerState.RUNNING);
        assertThat(roundTrippedStatus.getAllocatedResource().getMemorySize()).isEqualTo(1024);
        assertThat(roundTrippedStatus.getDiagnostics()).isEqualTo("healthy container");
        assertThat(roundTrippedStatus.getPriority().getPriority()).isEqualTo(3);
        assertThat(roundTrippedStatus.getCreationTime()).isEqualTo(44L);
    }

    @Test
    void heartbeatRequestAndResponseRoundTripNodeStatusCleanupListsAndCredentials() {
        ApplicationId applicationId = ApplicationId.newInstance(5678L, 11);
        ContainerId runningContainer = containerId(applicationId, 2, 3L);
        Resource containerCapability = Resource.newInstance(512, 1);
        ContainerStatus containerStatus = BuilderUtils.newContainerStatus(
                runningContainer,
                ContainerState.COMPLETE,
                "finished",
                0,
                containerCapability);
        NodeHealthStatus healthStatus = newNodeHealthStatus(true, "all disks healthy", 99L);
        ResourceUtilization containersUtilization = ResourceUtilization.newInstance(256, 512, 0.5F);
        ResourceUtilization nodeUtilization = ResourceUtilization.newInstance(1024, 2048, 1.5F);
        NodeStatus nodeStatus = newNodeStatus(
                NodeId.newInstance("nm.example.test", 1234),
                5,
                Collections.singletonList(containerStatus),
                Collections.singletonList(applicationId),
                healthStatus,
                containersUtilization,
                nodeUtilization,
                Collections.<Container>emptyList());
        MasterKey containerTokenKey = masterKey(17, new byte[] {1, 2, 3, 4});
        MasterKey nmTokenKey = masterKey(23, new byte[] {5, 6, 7, 8});

        NodeHeartbeatRequest request = newNodeHeartbeatRequest(
                nodeStatus,
                containerTokenKey,
                nmTokenKey,
                Collections.emptySet());

        assertThat(request.getNodeStatus().getResponseId()).isEqualTo(5);
        assertThat(request.getNodeStatus().getContainersStatuses()).hasSize(1);
        assertThat(request.getNodeStatus().getContainersStatuses().get(0)
                .getCapability().getMemorySize()).isEqualTo(512);
        assertThat(request.getNodeStatus().getNodeHealthStatus().getHealthReport()).isEqualTo("all disks healthy");
        assertThat(request.getNodeStatus().getContainersUtilization().getPhysicalMemory()).isEqualTo(256);
        assertThat(request.getNodeStatus().getNodeUtilization().getCPU()).isEqualTo(1.5F);
        assertThat(request.getNodeStatus().getIncreasedContainers()).isEmpty();
        assertThat(request.getNodeLabels()).isEmpty();
        assertThat(request.getLastKnownContainerTokenMasterKey().getKeyId()).isEqualTo(17);
        assertThat(bytes(request.getLastKnownNMTokenMasterKey().getBytes()))
                .containsExactly((byte) 5, (byte) 6, (byte) 7, (byte) 8);

        NodeHeartbeatResponse response = newNodeHeartbeatResponse(
                6,
                NodeAction.NORMAL,
                Collections.singletonList(runningContainer),
                Collections.singletonList(applicationId),
                containerTokenKey,
                nmTokenKey,
                1000L);
        ContainerId removedContainer = containerId(applicationId, 2, 4L);
        Map<ApplicationId, ByteBuffer> credentials = new HashMap<ApplicationId, ByteBuffer>();
        credentials.put(applicationId, ByteBuffer.wrap(new byte[] {9, 10, 11}));

        response.addContainersToBeRemovedFromNM(Collections.singletonList(removedContainer));
        response.setDiagnosticsMessage("continue processing");
        response.setSystemCredentialsForApps(credentials);

        assertThat(response.getResponseId()).isEqualTo(6);
        assertThat(response.getNodeAction()).isEqualTo(NodeAction.NORMAL);
        assertThat(response.getContainersToCleanup()).containsExactly(runningContainer);
        assertThat(response.getContainersToBeRemovedFromNM()).containsExactly(removedContainer);
        assertThat(response.getApplicationsToCleanup()).containsExactly(applicationId);
        assertThat(response.getNextHeartBeatInterval()).isEqualTo(1000L);
        assertThat(response.getDiagnosticsMessage()).isEqualTo("continue processing");
        assertThat(response.getContainerTokenMasterKey().getKeyId()).isEqualTo(17);
        assertThat(response.getNMTokenMasterKey().getKeyId()).isEqualTo(23);
        assertThat(bytes(response.getSystemCredentialsForApps().get(applicationId)))
                .containsExactly((byte) 9, (byte) 10, (byte) 11);
    }

    @Test
    void registerNodeManagerResponseExposesMasterKeysAndDiagnostics() {
        RegisterNodeManagerResponse response = new RegisterNodeManagerResponsePBImpl();
        MasterKey containerTokenKey = masterKey(101, new byte[] {12, 13});
        MasterKey nmTokenKey = masterKey(102, new byte[] {14, 15});

        response.setContainerTokenMasterKey(containerTokenKey);
        response.setNMTokenMasterKey(nmTokenKey);
        response.setNodeAction(NodeAction.RESYNC);
        response.setRMIdentifier(987654321L);
        response.setDiagnosticsMessage("resync requested");
        response.setRMVersion("rm-test-version");

        assertThat(response.getContainerTokenMasterKey().getKeyId()).isEqualTo(101);
        assertThat(bytes(response.getNMTokenMasterKey().getBytes())).containsExactly((byte) 14, (byte) 15);
        assertThat(response.getNodeAction()).isEqualTo(NodeAction.RESYNC);
        assertThat(response.getRMIdentifier()).isEqualTo(987654321L);
        assertThat(response.getDiagnosticsMessage()).isEqualTo("resync requested");
        assertThat(response.getRMVersion()).isEqualTo("rm-test-version");
    }

    @Test
    void sharedCacheUtilitiesBuildDepthAwarePathsAndGlobPatterns() {
        assertThat(SharedCacheUtil.getCacheEntryPath(3, "/shared-cache", "abcdef"))
                .isEqualTo("/shared-cache/a/b/c/abcdef");
        assertThat(SharedCacheUtil.getCacheEntryGlobPattern(3)).isEqualTo("*/*/*/*");

        assertThatThrownBy(() -> SharedCacheUtil.getCacheEntryPath(0, "/shared-cache", "abcdef"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cache depth");
        assertThatThrownBy(() -> SharedCacheUtil.getCacheEntryPath(4, "/shared-cache", "abc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("checksum");
    }

    @Test
    void builderUtilitiesCreateYarnRecordsWithExpectedValues() throws Exception {
        URL resourceUrl = BuilderUtils.newURL("hdfs", "namenode.example.test", 8020, "/apps/app.jar");
        LocalResource localResource = BuilderUtils.newLocalResource(
                resourceUrl,
                LocalResourceType.FILE,
                LocalResourceVisibility.APPLICATION,
                4096L,
                12345L,
                true);
        LocalResource uriResource = BuilderUtils.newLocalResource(
                new URI("hdfs://namenode.example.test:8020/apps/lib.jar"),
                LocalResourceType.ARCHIVE,
                LocalResourceVisibility.PUBLIC,
                8192L,
                54321L,
                false);

        assertThat(localResource.getResource().getHost()).isEqualTo("namenode.example.test");
        assertThat(localResource.getResource().getPort()).isEqualTo(8020);
        assertThat(localResource.getResource().getFile()).isEqualTo("/apps/app.jar");
        assertThat(localResource.getType()).isEqualTo(LocalResourceType.FILE);
        assertThat(localResource.getVisibility()).isEqualTo(LocalResourceVisibility.APPLICATION);
        assertThat(localResource.getSize()).isEqualTo(4096L);
        assertThat(localResource.getTimestamp()).isEqualTo(12345L);
        assertThat(localResource.getShouldBeUploadedToSharedCache()).isTrue();
        assertThat(uriResource.getType()).isEqualTo(LocalResourceType.ARCHIVE);
        assertThat(uriResource.getShouldBeUploadedToSharedCache()).isFalse();

        ResourceRequest request = BuilderUtils.newResourceRequest(
                Priority.newInstance(9),
                ResourceRequest.ANY,
                Resource.newInstance(2048, 2),
                3);
        assertThat(request.getPriority().getPriority()).isEqualTo(9);
        assertThat(request.getResourceName()).isEqualTo(ResourceRequest.ANY);
        assertThat(request.getCapability().getMemorySize()).isEqualTo(2048);
        assertThat(request.getCapability().getVirtualCores()).isEqualTo(2);
        assertThat(request.getNumContainers()).isEqualTo(3);

        Token token = BuilderUtils.newDelegationToken(
                new byte[] {1, 2},
                "kind",
                new byte[] {3, 4},
                "service");
        NodeId nodeId = BuilderUtils.newNodeId("worker.example.test", 8041);
        ContainerId containerId = containerId(ApplicationId.newInstance(2000L, 3), 4, 5L);
        Container container = BuilderUtils.newContainer(
                containerId,
                nodeId,
                "worker.example.test:8042",
                Resource.newInstance(1024, 1),
                Priority.newInstance(1),
                token);

        assertThat(container.getId()).isEqualTo(containerId);
        assertThat(container.getNodeId()).isEqualTo(nodeId);
        assertThat(container.getNodeHttpAddress()).isEqualTo("worker.example.test:8042");
        assertThat(container.getResource().getMemorySize()).isEqualTo(1024);
        assertThat(container.getPriority().getPriority()).isEqualTo(1);
        assertThat(container.getContainerToken().getKind()).isEqualTo("kind");
    }

    @Test
    void sharedCacheUploaderRecordsExposeRequestAndResponseState() {
        SCMUploaderCanUploadRequest canUploadRequest = new SCMUploaderCanUploadRequestPBImpl();
        SCMUploaderCanUploadResponse canUploadResponse = new SCMUploaderCanUploadResponsePBImpl();
        SCMUploaderNotifyRequest notifyRequest = new SCMUploaderNotifyRequestPBImpl();
        SCMUploaderNotifyResponse notifyResponse = new SCMUploaderNotifyResponsePBImpl();

        canUploadRequest.setResourceKey("resource-checksum");
        canUploadResponse.setUploadable(true);
        notifyRequest.setResourceKey("resource-checksum");
        notifyRequest.setFilename("archive.tar.gz");
        notifyResponse.setAccepted(true);

        assertThat(canUploadRequest.getResourceKey()).isEqualTo("resource-checksum");
        assertThat(canUploadResponse.getUploadable()).isTrue();
        assertThat(notifyRequest.getResourceKey()).isEqualTo("resource-checksum");
        assertThat(notifyRequest.getFileName()).isEqualTo("archive.tar.gz");
        assertThat(notifyResponse.getAccepted()).isTrue();
    }

    @Test
    void resourceTrackerSecurityInfoPublishesResourceManagerAndNodeManagerPrincipals() {
        RMNMSecurityInfoClass securityInfo = new RMNMSecurityInfoClass();
        Configuration configuration = new Configuration(false);

        KerberosInfo kerberosInfo = securityInfo.getKerberosInfo(ResourceTrackerPB.class, configuration);

        assertThat(kerberosInfo).isNotNull();
        assertThat(kerberosInfo.serverPrincipal()).isEqualTo(YarnConfiguration.RM_PRINCIPAL);
        assertThat(kerberosInfo.clientPrincipal()).isEqualTo(YarnConfiguration.NM_PRINCIPAL);
        assertThat(securityInfo.getTokenInfo(ResourceTrackerPB.class, configuration)).isNull();
        assertThat(securityInfo.getKerberosInfo(SCMUploaderProtocolPB.class, configuration)).isNull();
    }

    @Test
    void resourceManagerAuthenticationFilterInitializerInstallsFilterWithTokenAndProxySettings() {
        Configuration configuration = new Configuration(false);
        configuration.set("hadoop.http.authentication.type", "kerberos");
        configuration.set("hadoop.http.authentication.kerberos.principal", "HTTP/rm.example.test@EXAMPLE.COM");
        configuration.set("hadoop.http.authentication.signature.secret", "shared-secret");
        configuration.set("hadoop.proxyuser.alice.hosts", "host-a.example.test,host-b.example.test");
        configuration.set("hadoop.proxyuser.alice.groups", "analytics,operators");
        RecordingFilterContainer container = new RecordingFilterContainer();

        new RMAuthenticationFilterInitializer().initFilter(container, configuration);

        assertThat(container.filterName).isEqualTo("RMAuthenticationFilter");
        assertThat(container.filterClassName).isEqualTo(RMAuthenticationFilter.class.getName());
        assertThat(container.globalFilter).isFalse();
        assertThat(container.filterConfig)
                .containsEntry("type", "kerberos")
                .containsEntry("kerberos.principal", "HTTP/rm.example.test@EXAMPLE.COM")
                .containsEntry("signature.secret", "shared-secret")
                .containsEntry("cookie.path", "/")
                .containsEntry("proxyuser.alice.hosts", "host-a.example.test,host-b.example.test")
                .containsEntry("proxyuser.alice.groups", "analytics,operators")
                .containsEntry("delegation-token.token-kind", RMDelegationTokenIdentifier.KIND_NAME.toString());
    }

    @Test
    void versionsCompareCompatibilityAndMasterKeyDataPublishesEncodedSecret() {
        Version version = newVersion(2, 7);
        Version sameMajorNewerMinor = newVersion(2, 9);
        Version differentMajor = newVersion(3, 0);

        assertThat(version.getMajorVersion()).isEqualTo(2);
        assertThat(version.getMinorVersion()).isEqualTo(7);
        assertThat(version.toString()).isEqualTo("2.7");
        assertThat(version.isCompatibleTo(sameMajorNewerMinor)).isTrue();
        assertThat(version.isCompatibleTo(differentMajor)).isFalse();
        assertThat(version).isEqualTo(newVersion(2, 7));

        SecretKey secretKey = new SecretKeySpec(new byte[] {21, 22, 23, 24}, "HmacSHA1");
        MasterKeyData keyData = new MasterKeyData(masterKey(77, secretKey.getEncoded()), secretKey);

        assertThat(keyData.getMasterKey().getKeyId()).isEqualTo(77);
        assertThat(bytes(keyData.getMasterKey().getBytes()))
                .containsExactly((byte) 21, (byte) 22, (byte) 23, (byte) 24);
        assertThat(keyData.getSecretKey()).isSameAs(secretKey);
    }

    @Test
    void webContainerDaoCollectionsAndTableInitializersAreUsableWithoutWebServer() {
        AppsInfo appsInfo = new AppsInfo();
        AppAttemptsInfo attemptsInfo = new AppAttemptsInfo();
        ContainersInfo containersInfo = new ContainersInfo();

        appsInfo.add(null);
        attemptsInfo.add(null);
        containersInfo.add(null);

        assertThat(appsInfo.getApps()).hasSize(1).containsNull();
        assertThat(attemptsInfo.getAttempts()).hasSize(1).containsNull();
        assertThat(containersInfo.getContainers()).hasSize(1).containsNull();
        assertThat(WebPageUtils.appsTableInit()).contains("natural").contains("renderHadoopDate");
        assertThat(WebPageUtils.appsTableInit(true)).contains("'aaData'").contains("renderHadoopDate");
        assertThat(WebPageUtils.attemptsTableInit()).contains("natural");
        assertThat(WebPageUtils.containersTableInit()).contains("natural");
    }

    private static ContainerId containerId(ApplicationId applicationId, int attemptId, long containerSequence) {
        ApplicationAttemptId applicationAttemptId = ApplicationAttemptId.newInstance(applicationId, attemptId);
        return BuilderUtils.newContainerId(applicationAttemptId, containerSequence);
    }

    private static NMContainerStatus newNmContainerStatus(ContainerId containerId, int version,
            ContainerState containerState, Resource allocatedResource, String diagnostics, int exitStatus,
            Priority priority, long creationTime) {
        NMContainerStatus status = new NMContainerStatusPBImpl();
        status.setContainerId(containerId);
        status.setVersion(version);
        status.setContainerState(containerState);
        status.setAllocatedResource(allocatedResource);
        status.setDiagnostics(diagnostics);
        status.setContainerExitStatus(exitStatus);
        status.setPriority(priority);
        status.setCreationTime(creationTime);
        return status;
    }

    private static RegisterNodeManagerRequest newRegisterNodeManagerRequest(NodeId nodeId, int httpPort,
            Resource resource, String nmVersion, List<NMContainerStatus> containerStatuses,
            List<ApplicationId> runningApplications) {
        RegisterNodeManagerRequest request = new RegisterNodeManagerRequestPBImpl();
        request.setNodeId(nodeId);
        request.setHttpPort(httpPort);
        request.setResource(resource);
        request.setNMVersion(nmVersion);
        request.setContainerStatuses(containerStatuses);
        request.setRunningApplications(runningApplications);
        return request;
    }

    private static NodeHealthStatus newNodeHealthStatus(boolean isHealthy, String healthReport,
            long lastHealthReportTime) {
        NodeHealthStatus healthStatus = new NodeHealthStatusPBImpl();
        healthStatus.setIsNodeHealthy(isHealthy);
        healthStatus.setHealthReport(healthReport);
        healthStatus.setLastHealthReportTime(lastHealthReportTime);
        return healthStatus;
    }

    private static NodeStatus newNodeStatus(NodeId nodeId, int responseId,
            List<ContainerStatus> containerStatuses, List<ApplicationId> keepAliveApplications,
            NodeHealthStatus healthStatus, ResourceUtilization containersUtilization,
            ResourceUtilization nodeUtilization, List<Container> increasedContainers) {
        NodeStatus nodeStatus = new NodeStatusPBImpl();
        nodeStatus.setNodeId(nodeId);
        nodeStatus.setResponseId(responseId);
        nodeStatus.setContainersStatuses(containerStatuses);
        nodeStatus.setKeepAliveApplications(keepAliveApplications);
        nodeStatus.setNodeHealthStatus(healthStatus);
        nodeStatus.setContainersUtilization(containersUtilization);
        nodeStatus.setNodeUtilization(nodeUtilization);
        nodeStatus.setIncreasedContainers(increasedContainers);
        return nodeStatus;
    }

    private static NodeHeartbeatRequest newNodeHeartbeatRequest(NodeStatus nodeStatus, MasterKey containerTokenKey,
            MasterKey nmTokenKey, Set<NodeLabel> nodeLabels) {
        NodeHeartbeatRequest request = new NodeHeartbeatRequestPBImpl();
        request.setNodeStatus(nodeStatus);
        request.setLastKnownContainerTokenMasterKey(containerTokenKey);
        request.setLastKnownNMTokenMasterKey(nmTokenKey);
        request.setNodeLabels(nodeLabels);
        return request;
    }

    private static NodeHeartbeatResponse newNodeHeartbeatResponse(int responseId, NodeAction nodeAction,
            List<ContainerId> containersToCleanup, List<ApplicationId> applicationsToCleanup,
            MasterKey containerTokenKey, MasterKey nmTokenKey, long nextHeartbeatInterval) {
        NodeHeartbeatResponse response = new NodeHeartbeatResponsePBImpl();
        response.setResponseId(responseId);
        response.setNodeAction(nodeAction);
        response.addAllContainersToCleanup(containersToCleanup);
        response.addAllApplicationsToCleanup(applicationsToCleanup);
        response.setContainerTokenMasterKey(containerTokenKey);
        response.setNMTokenMasterKey(nmTokenKey);
        response.setNextHeartBeatInterval(nextHeartbeatInterval);
        return response;
    }

    private static Version newVersion(int majorVersion, int minorVersion) {
        Version version = new VersionPBImpl();
        version.setMajorVersion(majorVersion);
        version.setMinorVersion(minorVersion);
        return version;
    }

    private static MasterKey masterKey(int keyId, byte[] keyBytes) {
        MasterKey masterKey = new MasterKeyPBImpl();
        masterKey.setKeyId(keyId);
        masterKey.setBytes(ByteBuffer.wrap(Arrays.copyOf(keyBytes, keyBytes.length)));
        return masterKey;
    }

    private static byte[] bytes(ByteBuffer buffer) {
        ByteBuffer duplicate = buffer.duplicate();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return bytes;
    }

    private static final class RecordingFilterContainer implements FilterContainer {
        private String filterName;
        private String filterClassName;
        private Map<String, String> filterConfig;
        private boolean globalFilter;

        @Override
        public void addFilter(String name, String classname, Map<String, String> parameters) {
            filterName = name;
            filterClassName = classname;
            filterConfig = new HashMap<String, String>(parameters);
            globalFilter = false;
        }

        @Override
        public void addGlobalFilter(String name, String classname, Map<String, String> parameters) {
            filterName = name;
            filterClassName = classname;
            filterConfig = new HashMap<String, String>(parameters);
            globalFilter = true;
        }
    }
}
