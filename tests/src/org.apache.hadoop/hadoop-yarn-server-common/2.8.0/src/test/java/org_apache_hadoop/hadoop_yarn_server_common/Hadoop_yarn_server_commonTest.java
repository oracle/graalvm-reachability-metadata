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
import java.util.Map;
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
import org.apache.hadoop.yarn.server.api.records.MasterKey;
import org.apache.hadoop.yarn.server.api.records.NodeAction;
import org.apache.hadoop.yarn.server.api.records.NodeHealthStatus;
import org.apache.hadoop.yarn.server.api.records.NodeStatus;
import org.apache.hadoop.yarn.server.records.Version;
import org.apache.hadoop.yarn.server.security.MasterKeyData;
import org.apache.hadoop.yarn.server.security.http.RMAuthenticationFilter;
import org.apache.hadoop.yarn.server.security.http.RMAuthenticationFilterInitializer;
import org.apache.hadoop.yarn.server.sharedcache.SharedCacheUtil;
import org.apache.hadoop.yarn.server.utils.BuilderUtils;
import org.apache.hadoop.yarn.server.utils.YarnServerBuilderUtils;
import org.apache.hadoop.yarn.server.webapp.WebPageUtils;
import org.apache.hadoop.yarn.server.webapp.dao.AppAttemptsInfo;
import org.apache.hadoop.yarn.server.webapp.dao.AppsInfo;
import org.apache.hadoop.yarn.server.webapp.dao.ContainersInfo;
import org.apache.hadoop.yarn.util.Records;
import org.junit.jupiter.api.Test;

public class Hadoop_yarn_server_commonTest {
    @Test
    void nodeManagerRegistrationCarriesResourceContainersAndRunningApplications() {
        NodeId nodeId = NodeId.newInstance("worker.example.test", 45454);
        Resource nodeResource = Resource.newInstance(8192, 4);
        ApplicationId applicationId = ApplicationId.newInstance(1234L, 7);
        ContainerId containerId = containerId(applicationId, 1, 99L);
        Priority priority = Priority.newInstance(3);
        NMContainerStatus containerStatus = NMContainerStatus.newInstance(
                containerId,
                0,
                ContainerState.RUNNING,
                Resource.newInstance(1024, 1),
                "healthy container",
                0,
                priority,
                44L);

        RegisterNodeManagerRequest request = RegisterNodeManagerRequest.newInstance(
                nodeId,
                8042,
                nodeResource,
                "test-nm-version",
                Collections.singletonList(containerStatus),
                Collections.singletonList(applicationId));

        assertThat(request.getNodeId()).isEqualTo(nodeId);
        assertThat(request.getHttpPort()).isEqualTo(8042);
        assertThat(request.getResource().getMemorySize()).isEqualTo(8192L);
        assertThat(request.getResource().getVirtualCores()).isEqualTo(4);
        assertThat(request.getNMVersion()).isEqualTo("test-nm-version");
        assertThat(request.getRunningApplications()).containsExactly(applicationId);

        NMContainerStatus roundTrippedStatus = request.getNMContainerStatuses().get(0);
        assertThat(roundTrippedStatus.getContainerId()).isEqualTo(containerId);
        assertThat(roundTrippedStatus.getContainerState()).isEqualTo(ContainerState.RUNNING);
        assertThat(roundTrippedStatus.getAllocatedResource().getMemorySize()).isEqualTo(1024L);
        assertThat(roundTrippedStatus.getDiagnostics()).isEqualTo("healthy container");
        assertThat(roundTrippedStatus.getPriority().getPriority()).isEqualTo(3);
        assertThat(roundTrippedStatus.getCreationTime()).isEqualTo(44L);
    }

    @Test
    void heartbeatRequestAndResponseRoundTripNodeStatusCleanupListsAndCredentials() {
        ApplicationId applicationId = ApplicationId.newInstance(5678L, 11);
        ContainerId runningContainer = containerId(applicationId, 2, 3L);
        ContainerStatus containerStatus = BuilderUtils.newContainerStatus(
                runningContainer,
                ContainerState.COMPLETE,
                "finished",
                0,
                Resource.newInstance(1024, 1));
        NodeHealthStatus healthStatus = NodeHealthStatus.newInstance(true, "all disks healthy", 99L);
        ResourceUtilization containersUtilization = ResourceUtilization.newInstance(1024, 2048, 1.0F);
        ResourceUtilization nodeUtilization = ResourceUtilization.newInstance(2048, 4096, 2.0F);
        NodeStatus nodeStatus = NodeStatus.newInstance(
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

        NodeHeartbeatRequest request = NodeHeartbeatRequest.newInstance(
                nodeStatus,
                containerTokenKey,
                nmTokenKey,
                Collections.emptySet());

        assertThat(request.getNodeStatus().getResponseId()).isEqualTo(5);
        assertThat(request.getNodeStatus().getContainersStatuses()).hasSize(1);
        assertThat(request.getNodeStatus().getNodeHealthStatus().getHealthReport()).isEqualTo("all disks healthy");
        assertThat(request.getNodeStatus().getContainersUtilization().getPhysicalMemory()).isEqualTo(1024);
        assertThat(request.getNodeStatus().getContainersUtilization().getVirtualMemory()).isEqualTo(2048);
        assertThat(request.getNodeStatus().getContainersUtilization().getCPU()).isEqualTo(1.0F);
        assertThat(request.getNodeStatus().getNodeUtilization().getPhysicalMemory()).isEqualTo(2048);
        assertThat(request.getNodeStatus().getNodeUtilization().getVirtualMemory()).isEqualTo(4096);
        assertThat(request.getNodeStatus().getNodeUtilization().getCPU()).isEqualTo(2.0F);
        assertThat(request.getNodeStatus().getIncreasedContainers()).isEmpty();
        assertThat(request.getLastKnownContainerTokenMasterKey().getKeyId()).isEqualTo(17);
        assertThat(bytes(request.getLastKnownNMTokenMasterKey().getBytes()))
                .containsExactly((byte) 5, (byte) 6, (byte) 7, (byte) 8);

        NodeHeartbeatResponse response = YarnServerBuilderUtils.newNodeHeartbeatResponse(
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
        RegisterNodeManagerResponse response = Records.newRecord(RegisterNodeManagerResponse.class);
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
        assertThat(request.getCapability().getMemorySize()).isEqualTo(2048L);
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
        assertThat(container.getResource().getMemorySize()).isEqualTo(1024L);
        assertThat(container.getPriority().getPriority()).isEqualTo(1);
        assertThat(container.getContainerToken().getKind()).isEqualTo("kind");
    }

    @Test
    void sharedCacheUploaderRecordsExposeRequestAndResponseState() {
        SCMUploaderCanUploadRequest canUploadRequest = Records.newRecord(SCMUploaderCanUploadRequest.class);
        SCMUploaderCanUploadResponse canUploadResponse = Records.newRecord(SCMUploaderCanUploadResponse.class);
        SCMUploaderNotifyRequest notifyRequest = Records.newRecord(SCMUploaderNotifyRequest.class);
        SCMUploaderNotifyResponse notifyResponse = Records.newRecord(SCMUploaderNotifyResponse.class);

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
        Version version = Version.newInstance(2, 7);
        Version sameMajorNewerMinor = Version.newInstance(2, 9);
        Version differentMajor = Version.newInstance(3, 0);

        assertThat(version.getMajorVersion()).isEqualTo(2);
        assertThat(version.getMinorVersion()).isEqualTo(7);
        assertThat(version.toString()).isEqualTo("2.7");
        assertThat(version.isCompatibleTo(sameMajorNewerMinor)).isTrue();
        assertThat(version.isCompatibleTo(differentMajor)).isFalse();
        assertThat(version).isEqualTo(Version.newInstance(2, 7));

        SecretKey secretKey = new SecretKeySpec(new byte[] {21, 22, 23, 24}, "HmacSHA1");
        MasterKeyData keyData = new MasterKeyData(77, secretKey);

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
        return ContainerId.newContainerId(applicationAttemptId, containerSequence);
    }

    private static MasterKey masterKey(int keyId, byte[] keyBytes) {
        MasterKey masterKey = Records.newRecord(MasterKey.class);
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
