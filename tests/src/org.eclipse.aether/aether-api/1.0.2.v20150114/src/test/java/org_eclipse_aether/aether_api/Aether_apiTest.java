/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_aether.aether_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.AbstractForwardingRepositorySystemSession;
import org.eclipse.aether.DefaultRepositoryCache;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.DefaultSessionData;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.artifact.DefaultArtifactType;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyManagement;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeployResult;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallResult;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.AuthenticationDigest;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.resolution.MetadataRequest;
import org.eclipse.aether.resolution.MetadataResult;
import org.eclipse.aether.resolution.VersionRequest;
import org.eclipse.aether.resolution.VersionResult;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferResource;
import org.junit.jupiter.api.Test;

public class Aether_apiTest {
    private static final File ARTIFACT_FILE = new File("target/demo-1.2.3.jar");
    private static final File METADATA_FILE = new File("target/maven-metadata.xml");

    @Test
    void artifactCoordinatesSupportParsingTypePropertiesAndCopyOnWriteMutators() {
        Map<String, String> typeProperties = new HashMap<>();
        typeProperties.put("language", "java");
        DefaultArtifactType type = new DefaultArtifactType("test-jar", "jar", "tests", typeProperties);
        Artifact artifact = new DefaultArtifact(
                "org.acme", "demo", "tests", "jar", "1.0-SNAPSHOT", typeProperties, type);

        assertThat(type.getId()).isEqualTo("test-jar");
        assertThat(type.getExtension()).isEqualTo("jar");
        assertThat(type.getClassifier()).isEqualTo("tests");
        assertThat(artifact.getGroupId()).isEqualTo("org.acme");
        assertThat(artifact.getArtifactId()).isEqualTo("demo");
        assertThat(artifact.getExtension()).isEqualTo("jar");
        assertThat(artifact.getClassifier()).isEqualTo("tests");
        assertThat(artifact.getVersion()).isEqualTo("1.0-SNAPSHOT");
        assertThat(artifact.getBaseVersion()).isEqualTo("1.0-SNAPSHOT");
        assertThat(artifact.isSnapshot()).isTrue();
        assertThat(artifact.getProperty("language", "missing")).isEqualTo("java");

        Artifact released = artifact.setVersion("1.0");
        Artifact withFile = released.setFile(ARTIFACT_FILE);
        Artifact withProperties = withFile.setProperties(Collections.singletonMap("packaging", "jar"));

        assertThat(released).isNotSameAs(artifact);
        assertThat(released.getVersion()).isEqualTo("1.0");
        assertThat(released.isSnapshot()).isFalse();
        assertThat(artifact.getVersion()).isEqualTo("1.0-SNAPSHOT");
        assertThat(withFile.getFile()).isEqualTo(ARTIFACT_FILE);
        assertThat(released.getFile()).isNull();
        assertThat(withProperties.getProperty("packaging", "missing")).isEqualTo("jar");
        assertThat(withFile.getProperty("packaging", "missing")).isEqualTo("missing");
        Artifact parsed = new DefaultArtifact("org.acme:demo:jar:tests:1.0");
        assertThat(parsed.getGroupId()).isEqualTo("org.acme");
        assertThat(parsed.getArtifactId()).isEqualTo("demo");
        assertThat(parsed.getExtension()).isEqualTo("jar");
        assertThat(parsed.getClassifier()).isEqualTo("tests");
        assertThat(parsed.getVersion()).isEqualTo("1.0");
    }

    @Test
    void dependenciesExclusionsAndNodesModelGraphStateAndTraversal() {
        Artifact rootArtifact = new DefaultArtifact("org.acme:root:jar:1.0");
        Artifact childArtifact = new DefaultArtifact("org.acme:child:jar:1.1");
        Exclusion loggingExclusion = new Exclusion("org.slf4j", "slf4j-api", "", "jar");
        Dependency rootDependency = new Dependency(
                rootArtifact, "compile", false, Collections.singleton(loggingExclusion));
        Dependency childDependency = new Dependency(childArtifact, "runtime", true);

        DefaultDependencyNode root = new DefaultDependencyNode(rootDependency);
        DefaultDependencyNode child = new DefaultDependencyNode(childDependency);
        root.setChildren(Collections.singletonList(child));
        root.setRequestContext("dependency-collection");
        root.setManagedBits(DependencyNode.MANAGED_SCOPE | DependencyNode.MANAGED_OPTIONAL);
        root.setData("origin", "direct");
        root.setRepositories(Collections.singletonList(centralRepository()));

        List<String> visits = new ArrayList<>();
        boolean completed = root.accept(new DependencyVisitor() {
            @Override
            public boolean visitEnter(DependencyNode node) {
                visits.add("enter:" + node.getArtifact().getArtifactId());
                return true;
            }

            @Override
            public boolean visitLeave(DependencyNode node) {
                visits.add("leave:" + node.getArtifact().getArtifactId());
                return true;
            }
        });

        assertThat(completed).isTrue();
        assertThat(rootDependency.getExclusions()).containsExactly(loggingExclusion);
        assertThat(root.getChildren()).containsExactly(child);
        assertThat(root.getDependency()).isEqualTo(rootDependency);
        assertThat(root.getArtifact()).isEqualTo(rootArtifact);
        assertThat(root.getManagedBits()).isEqualTo(DependencyNode.MANAGED_SCOPE | DependencyNode.MANAGED_OPTIONAL);
        assertThat(root.getData()).containsEntry("origin", "direct");
        assertThat(root.getRepositories()).containsExactly(centralRepository());
        assertThat(visits).containsExactly("enter:root", "enter:child", "leave:child", "leave:root");
        assertThat(childDependency.setScope("test").setOptional(false))
                .isEqualTo(new Dependency(childArtifact, "test", false));
    }

    @Test
    void metadataInstancesExposeCoordinatesNatureFilesAndCopyOnWriteProperties() {
        Map<String, String> properties = Collections.singletonMap("checksum", "required");
        Metadata metadata = new DefaultMetadata(
                "org.acme", "demo", "1.0", "maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT, properties, null);

        Metadata withFile = metadata.setFile(METADATA_FILE);
        Metadata withProperties = withFile.setProperties(Collections.singletonMap("local", "true"));

        assertThat(metadata.getGroupId()).isEqualTo("org.acme");
        assertThat(metadata.getArtifactId()).isEqualTo("demo");
        assertThat(metadata.getVersion()).isEqualTo("1.0");
        assertThat(metadata.getType()).isEqualTo("maven-metadata.xml");
        assertThat(metadata.getNature()).isEqualTo(Metadata.Nature.RELEASE_OR_SNAPSHOT);
        assertThat(metadata.getProperty("checksum", "missing")).isEqualTo("required");
        assertThat(withFile.getFile()).isEqualTo(METADATA_FILE);
        assertThat(metadata.getFile()).isNull();
        assertThat(withProperties.getProperty("local", "missing")).isEqualTo("true");
        assertThat(withFile.getProperty("local", "missing")).isEqualTo("missing");
        assertThat(withProperties.toString()).contains("org.acme", "demo", "1.0", "maven-metadata.xml");
    }

    @Test
    void remoteRepositoryBuilderPoliciesMirrorsAndProxyAreImmutableAfterBuild() {
        RepositoryPolicy releasePolicy = new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_DAILY,
                RepositoryPolicy.CHECKSUM_POLICY_FAIL);
        RepositoryPolicy snapshotPolicy = new RepositoryPolicy(false, RepositoryPolicy.UPDATE_POLICY_NEVER,
                RepositoryPolicy.CHECKSUM_POLICY_IGNORE);
        RemoteRepository mirror = new RemoteRepository.Builder(
                "mirror", "default", "https://mirror.example.org/repository").build();
        Proxy proxy = new Proxy(Proxy.TYPE_HTTP, "proxy.example.org", 8080);

        RemoteRepository repository = new RemoteRepository.Builder(
                "central", "default", "https://repo1.maven.org/maven2")
                .setReleasePolicy(releasePolicy)
                .setSnapshotPolicy(snapshotPolicy)
                .setProxy(proxy)
                .addMirroredRepository(mirror)
                .setRepositoryManager(true)
                .build();
        RemoteRepository changedCopy = new RemoteRepository.Builder(repository)
                .setId("central-copy")
                .setUrl("http://repo1.maven.org/maven2")
                .build();

        assertThat(repository.getId()).isEqualTo("central");
        assertThat(repository.getContentType()).isEqualTo("default");
        assertThat(repository.getUrl()).isEqualTo("https://repo1.maven.org/maven2");
        assertThat(repository.getProtocol()).isEqualTo("https");
        assertThat(repository.getHost()).isEqualTo("repo1.maven.org");
        assertThat(repository.getPolicy(false)).isEqualTo(releasePolicy);
        assertThat(repository.getPolicy(true)).isEqualTo(snapshotPolicy);
        assertThat(repository.getProxy()).isEqualTo(proxy);
        assertThat(repository.getMirroredRepositories()).containsExactly(mirror);
        assertThat(repository.isRepositoryManager()).isTrue();
        assertThat(changedCopy.getId()).isEqualTo("central-copy");
        assertThat(changedCopy.getProtocol()).isEqualTo("http");
        assertThat(repository).isNotEqualTo(changedCopy);
    }

    @Test
    void collectAndArtifactRequestsPreserveRootsRepositoriesContextAndTrace() {
        Artifact rootArtifact = new DefaultArtifact("org.acme:root:jar:1.0");
        Dependency rootDependency = new Dependency(rootArtifact, "compile");
        Dependency managedDependency = new Dependency(new DefaultArtifact("org.acme:managed:jar:2.0"), "runtime");
        RemoteRepository repository = centralRepository();
        RequestTrace trace = new RequestTrace("collect-root");

        CollectRequest collectRequest = new CollectRequest()
                .setRoot(rootDependency)
                .addDependency(new Dependency(new DefaultArtifact("org.acme:direct:jar:1.1"), "test"))
                .addManagedDependency(managedDependency)
                .addRepository(repository)
                .setRequestContext("project")
                .setTrace(trace);
        DefaultDependencyNode dependencyNode = new DefaultDependencyNode(rootDependency);
        ArtifactRequest artifactRequest = new ArtifactRequest(dependencyNode)
                .addRepository(repository)
                .setRequestContext("artifact")
                .setTrace(trace.newChild("artifact-resolution"));

        assertThat(collectRequest.getRoot()).isEqualTo(rootDependency);
        assertThat(collectRequest.getDependencies()).hasSize(1);
        assertThat(collectRequest.getManagedDependencies()).containsExactly(managedDependency);
        assertThat(collectRequest.getRepositories()).containsExactly(repository);
        assertThat(collectRequest.getRequestContext()).isEqualTo("project");
        assertThat(collectRequest.getTrace()).isEqualTo(trace);
        assertThat(artifactRequest.getDependencyNode()).isEqualTo(dependencyNode);
        assertThat(artifactRequest.getArtifact()).isEqualTo(rootArtifact);
        assertThat(artifactRequest.getRepositories()).containsExactly(repository);
        assertThat(artifactRequest.getTrace().getParent()).isEqualTo(trace);
    }

    @Test
    void artifactDescriptorRequestsAndResultsCapturePomModelRelocationsAliasesAndRepositories() {
        Artifact requestedArtifact = new DefaultArtifact("org.acme:application:pom:1.0");
        Artifact relocatedArtifact = new DefaultArtifact("org.example:application:pom:1.0");
        Artifact aliasArtifact = new DefaultArtifact("org.legacy:application:pom:1.0");
        Dependency directDependency = new Dependency(new DefaultArtifact("org.acme:library:jar:1.1"), "compile");
        Dependency managedDependency = new Dependency(new DefaultArtifact("org.acme:managed:jar:2.0"), "runtime");
        RemoteRepository repository = centralRepository();
        RemoteRepository descriptorRepository = new RemoteRepository.Builder(
                "descriptor-repo", "default", "https://descriptor.example.org/repository").build();
        RequestTrace trace = new RequestTrace("artifact-descriptor");
        RuntimeException warning = new RuntimeException("descriptor warning");

        ArtifactDescriptorRequest request = new ArtifactDescriptorRequest()
                .setArtifact(requestedArtifact)
                .addRepository(repository)
                .addRepository(descriptorRepository)
                .setRequestContext("descriptor")
                .setTrace(trace);
        ArtifactDescriptorResult result = new ArtifactDescriptorResult(request)
                .setArtifact(relocatedArtifact)
                .setRepository(repository)
                .addRelocation(relocatedArtifact)
                .addAlias(aliasArtifact)
                .addDependency(directDependency)
                .addManagedDependency(managedDependency)
                .addRepository(descriptorRepository)
                .setProperties(Collections.singletonMap("packaging", "jar"))
                .addException(warning);

        assertThat(request.getArtifact()).isEqualTo(requestedArtifact);
        assertThat(request.getRepositories()).containsExactly(repository, descriptorRepository);
        assertThat(request.getRequestContext()).isEqualTo("descriptor");
        assertThat(request.getTrace()).isEqualTo(trace);
        assertThat(result.getRequest()).isEqualTo(request);
        assertThat(result.getArtifact()).isEqualTo(relocatedArtifact);
        assertThat(result.getRepository()).isEqualTo(repository);
        assertThat(result.getRelocations()).containsExactly(relocatedArtifact);
        assertThat(result.getAliases()).containsExactly(aliasArtifact);
        assertThat(result.getDependencies()).containsExactly(directDependency);
        assertThat(result.getManagedDependencies()).containsExactly(managedDependency);
        assertThat(result.getRepositories()).containsExactly(descriptorRepository);
        assertThat(result.getProperties()).containsEntry("packaging", "jar");
        assertThat(result.getExceptions()).containsExactly(warning);
    }

    @Test
    void resultObjectsTrackResolvedMissingAndExceptionalOutcomes() {
        Artifact artifact = new DefaultArtifact("org.acme:demo:jar:1.0");
        ArtifactRequest artifactRequest = new ArtifactRequest().setArtifact(artifact);
        ArtifactResult artifactResult = new ArtifactResult(artifactRequest).setRepository(centralRepository());
        ArtifactNotFoundException failure = new ArtifactNotFoundException(artifact, centralRepository(), "not found");

        assertThat(artifactResult.isResolved()).isFalse();
        assertThat(artifactResult.isMissing()).isTrue();
        artifactResult.addException(failure);
        assertThat(artifactResult.getExceptions()).containsExactly(failure);
        assertThat(artifactResult.isMissing()).isTrue();
        artifactResult.setArtifact(artifact.setFile(ARTIFACT_FILE));
        assertThat(artifactResult.isResolved()).isTrue();

        MetadataRequest metadataRequest = new MetadataRequest(new DefaultMetadata("org.acme", "demo", "1.0",
                "maven-metadata.xml", Metadata.Nature.RELEASE), centralRepository(), "metadata");
        MetadataResult metadataResult = new MetadataResult(metadataRequest)
                .setMetadata(metadataRequest.getMetadata().setFile(METADATA_FILE))
                .setUpdated(true);
        assertThat(metadataResult.isResolved()).isTrue();
        assertThat(metadataResult.isMissing()).isFalse();
        assertThat(metadataResult.isUpdated()).isTrue();
        metadataResult.setException(failure);
        assertThat(metadataResult.getException()).isEqualTo(failure);
    }

    @Test
    void versionAndDependencyResolutionDtosCarryRequestsRootsCyclesAndArtifacts() {
        Artifact artifact = new DefaultArtifact("org.acme:demo:jar:1.0");
        VersionRequest versionRequest = new VersionRequest(
                artifact, Collections.singletonList(centralRepository()), "version")
                .setTrace(new RequestTrace("version-trace"));
        VersionResult versionResult = new VersionResult(versionRequest)
                .setVersion("1.0")
                .setRepository(centralRepository());
        DependencyRequest dependencyRequest = new DependencyRequest(
                new DefaultDependencyNode(new Dependency(artifact, "compile")), (node, parents) -> true);
        ArtifactResult artifactResult = new ArtifactResult(new ArtifactRequest().setArtifact(artifact))
                .setArtifact(artifact);
        DependencyResult dependencyResult = new DependencyResult(dependencyRequest)
                .setRoot(dependencyRequest.getRoot())
                .setCollectExceptions(Collections.singletonList(new RuntimeException("collect")))
                .setArtifactResults(Collections.singletonList(artifactResult));
        CollectResult collectResult = new CollectResult(
                new CollectRequest().setRoot(new Dependency(artifact, "compile")))
                .setRoot(dependencyRequest.getRoot());

        assertThat(versionResult.getRequest()).isEqualTo(versionRequest);
        assertThat(versionResult.getVersion()).isEqualTo("1.0");
        assertThat(versionResult.getRepository()).isEqualTo(centralRepository());
        assertThat(dependencyResult.getRequest()).isEqualTo(dependencyRequest);
        assertThat(dependencyResult.getRoot()).isEqualTo(dependencyRequest.getRoot());
        assertThat(dependencyResult.getCollectExceptions()).hasSize(1);
        assertThat(dependencyResult.getArtifactResults()).containsExactly(artifactResult);
        assertThat(collectResult.getRoot()).isEqualTo(dependencyRequest.getRoot());
        assertThat(collectResult.getExceptions()).isEmpty();
    }

    @Test
    void installDeployRequestsAndResultsAggregateArtifactsMetadataRepositoryAndTrace() {
        Artifact artifact = new DefaultArtifact("org.acme:demo:jar:1.0").setFile(ARTIFACT_FILE);
        Metadata metadata = new DefaultMetadata(
                "org.acme", "demo", "1.0", "maven-metadata.xml", Metadata.Nature.RELEASE)
                .setFile(METADATA_FILE);
        RequestTrace trace = new RequestTrace("publish");

        InstallRequest installRequest = new InstallRequest()
                .addArtifact(artifact)
                .addMetadata(metadata)
                .setTrace(trace);
        InstallResult installResult = new InstallResult(installRequest).addArtifact(artifact).addMetadata(metadata);
        DeployRequest deployRequest = new DeployRequest()
                .addArtifact(artifact)
                .addMetadata(metadata)
                .setRepository(centralRepository())
                .setTrace(trace);
        DeployResult deployResult = new DeployResult(deployRequest).addArtifact(artifact).addMetadata(metadata);

        assertThat(installRequest.getArtifacts()).containsExactly(artifact);
        assertThat(installRequest.getMetadata()).containsExactly(metadata);
        assertThat(installRequest.getTrace()).isEqualTo(trace);
        assertThat(installResult.getRequest()).isEqualTo(installRequest);
        assertThat(installResult.getArtifacts()).containsExactly(artifact);
        assertThat(installResult.getMetadata()).containsExactly(metadata);
        assertThat(deployRequest.getRepository()).isEqualTo(centralRepository());
        assertThat(deployRequest.getTrace()).isEqualTo(trace);
        assertThat(deployResult.getRequest()).isEqualTo(deployRequest);
        assertThat(deployResult.getArtifacts()).containsExactly(artifact);
        assertThat(deployResult.getMetadata()).containsExactly(metadata);
    }

    @Test
    void sessionsDataCacheAndForwardingSessionDelegateState() {
        DefaultSessionData data = new DefaultSessionData();
        assertThat(data.get("key")).isNull();
        data.set("key", "value");
        assertThat(data.get("key")).isEqualTo("value");
        assertThat(data.set("key", "value", "updated")).isTrue();
        assertThat(data.set("key", "value", "ignored")).isFalse();
        assertThat(data.get("key")).isEqualTo("updated");

        DefaultRepositoryCache cache = new DefaultRepositoryCache();
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession()
                .setOffline(true)
                .setIgnoreArtifactDescriptorRepositories(true)
                .setChecksumPolicy(RepositoryPolicy.CHECKSUM_POLICY_WARN)
                .setUpdatePolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS)
                .setSystemProperty("java.version", "test")
                .setUserProperty("profile", "integration")
                .setConfigProperty("retries", 3)
                .setData(data)
                .setCache(cache);
        cache.put(session, "artifact", "cached-value");

        RepositorySystemSession forwarding = new TestForwardingSession(session);
        assertThat(forwarding.isOffline()).isTrue();
        assertThat(forwarding.isIgnoreArtifactDescriptorRepositories()).isTrue();
        assertThat(forwarding.getChecksumPolicy()).isEqualTo(RepositoryPolicy.CHECKSUM_POLICY_WARN);
        assertThat(forwarding.getUpdatePolicy()).isEqualTo(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
        assertThat(forwarding.getSystemProperties()).containsEntry("java.version", "test");
        assertThat(forwarding.getUserProperties()).containsEntry("profile", "integration");
        assertThat(forwarding.getConfigProperties()).containsEntry("retries", 3);
        assertThat(forwarding.getData()).isSameAs(data);
        assertThat(forwarding.getCache().get(session, "artifact")).isEqualTo("cached-value");
    }

    @Test
    void repositoryAndTransferEventsExposeBuilderStateAndPayloadBuffers() {
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession();
        Artifact artifact = new DefaultArtifact("org.acme:demo:jar:1.0");
        Metadata metadata = new DefaultMetadata(
                "org.acme", "demo", "1.0", "maven-metadata.xml", Metadata.Nature.RELEASE);
        RequestTrace trace = RequestTrace.newChild(new RequestTrace("root"), "download");
        RuntimeException failure = new RuntimeException("broken");

        RepositoryEvent repositoryEvent = new RepositoryEvent.Builder(
                session, RepositoryEvent.EventType.ARTIFACT_RESOLVED)
                .setArtifact(artifact)
                .setMetadata(metadata)
                .setRepository(new LocalRepository("target/local-repo"))
                .setFile(ARTIFACT_FILE)
                .setExceptions(Collections.singletonList(failure))
                .setTrace(trace)
                .build();
        TransferResource resource = new TransferResource("https://repo.example.org/", "org/acme/demo/1.0/demo-1.0.jar",
                ARTIFACT_FILE, trace)
                .setContentLength(128L)
                .setResumeOffset(16L);
        TransferEvent transferEvent = new TransferEvent.Builder(session, resource)
                .setType(TransferEvent.EventType.PROGRESSED)
                .setRequestType(TransferEvent.RequestType.GET)
                .setTransferredBytes(32L)
                .addTransferredBytes(16L)
                .setDataBuffer(new byte[] {1, 2, 3, 4}, 1, 2)
                .setException(failure)
                .build();
        TransferEvent copiedEvent = new TransferEvent.Builder(session, resource)
                .setType(TransferEvent.EventType.INITIATED)
                .copy()
                .resetType(TransferEvent.EventType.SUCCEEDED)
                .build();

        assertThat(repositoryEvent.getType()).isEqualTo(RepositoryEvent.EventType.ARTIFACT_RESOLVED);
        assertThat(repositoryEvent.getArtifact()).isEqualTo(artifact);
        assertThat(repositoryEvent.getMetadata()).isEqualTo(metadata);
        assertThat(repositoryEvent.getFile()).isEqualTo(ARTIFACT_FILE);
        assertThat(repositoryEvent.getExceptions()).containsExactly(failure);
        assertThat(repositoryEvent.getException()).isEqualTo(failure);
        assertThat(repositoryEvent.getTrace()).isEqualTo(trace);
        assertThat(resource.getRepositoryUrl()).isEqualTo("https://repo.example.org/");
        assertThat(resource.getResourceName()).isEqualTo("org/acme/demo/1.0/demo-1.0.jar");
        assertThat(resource.getContentLength()).isEqualTo(128L);
        assertThat(resource.getResumeOffset()).isEqualTo(16L);
        assertThat(transferEvent.getType()).isEqualTo(TransferEvent.EventType.PROGRESSED);
        assertThat(transferEvent.getRequestType()).isEqualTo(TransferEvent.RequestType.GET);
        assertThat(transferEvent.getTransferredBytes()).isEqualTo(48L);
        assertThat(transferEvent.getDataLength()).isEqualTo(2);
        assertThat(bytesFrom(transferEvent.getDataBuffer())).containsExactly((byte) 2, (byte) 3);
        assertThat(transferEvent.getException()).isEqualTo(failure);
        assertThat(copiedEvent.getType()).isEqualTo(TransferEvent.EventType.SUCCEEDED);
    }

    @Test
    void dependencyManagementAppliesVersionScopeOptionalExclusionsAndProperties() {
        Exclusion exclusion = new Exclusion("org.unwanted", "legacy", "", "jar");
        DependencyManagement management = new DependencyManagement()
                .setVersion("2.0")
                .setScope("runtime")
                .setOptional(true)
                .setExclusions(Collections.singleton(exclusion))
                .setProperties(Collections.singletonMap("classifier", "tests"));

        assertThat(management.getVersion()).isEqualTo("2.0");
        assertThat(management.getScope()).isEqualTo("runtime");
        assertThat(management.getOptional()).isTrue();
        assertThat(management.getExclusions()).containsExactly(exclusion);
        assertThat(management.getProperties()).containsEntry("classifier", "tests");
        assertThatThrownBy(() -> management.getExclusions().add(new Exclusion("g", "a", "", "jar")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void authenticationContextFillsRepositoryAndProxyCredentialsAndComputesDigests() {
        TestAuthentication repositoryAuthentication = new TestAuthentication(
                "repository-user", "repository-secret".toCharArray(), "keys/repository.pem");
        TestAuthentication proxyAuthentication = new TestAuthentication(
                "proxy-user", "proxy-secret".toCharArray(), "keys/proxy.pem");
        Proxy proxy = new Proxy(Proxy.TYPE_HTTP, "proxy.example.org", 8080, proxyAuthentication);
        RemoteRepository repository = new RemoteRepository.Builder(
                "secure", "default", "https://secure.example.org/repository")
                .setAuthentication(repositoryAuthentication)
                .setProxy(proxy)
                .build();
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession();

        AuthenticationContext repositoryContext = AuthenticationContext.forRepository(session, repository);
        AuthenticationContext proxyContext = AuthenticationContext.forProxy(session, repository);
        try {
            assertThat(repositoryContext.getSession()).isSameAs(session);
            assertThat(repositoryContext.getRepository()).isSameAs(repository);
            assertThat(repositoryContext.getProxy()).isNull();
            assertThat(repositoryContext.get(AuthenticationContext.USERNAME)).isEqualTo("repository-user");
            assertThat(repositoryContext.get(AuthenticationContext.USERNAME)).isEqualTo("repository-user");
            assertThat(repositoryAuthentication.fillRequests).isEqualTo(1);
            assertThat(repositoryContext.get(AuthenticationContext.PASSWORD)).isEqualTo("repository-secret");
            assertThat(repositoryContext.get(AuthenticationContext.PRIVATE_KEY_PATH, File.class))
                    .isEqualTo(new File("keys/repository.pem"));

            assertThat(proxyContext.getRepository()).isSameAs(repository);
            assertThat(proxyContext.getProxy()).isSameAs(proxy);
            assertThat(proxyContext.get(AuthenticationContext.USERNAME)).isEqualTo("proxy-user");
            assertThat(proxyContext.get(AuthenticationContext.PASSWORD, char[].class))
                    .containsExactly("proxy-secret".toCharArray());
        } finally {
            AuthenticationContext.close(repositoryContext);
            AuthenticationContext.close(proxyContext);
        }

        String repositoryDigest = AuthenticationDigest.forRepository(session, repository);
        String proxyDigest = AuthenticationDigest.forProxy(session, repository);
        assertThat(repositoryDigest).isNotEmpty();
        assertThat(repositoryDigest).isEqualTo(AuthenticationDigest.forRepository(session, repository));
        assertThat(proxyDigest).isNotEmpty();
        assertThat(proxyDigest).isEqualTo(AuthenticationDigest.forProxy(session, repository));
        assertThat(repositoryDigest).isNotEqualTo(proxyDigest);
        assertThat(repositoryAuthentication.digestRequests).isEqualTo(2);
        assertThat(proxyAuthentication.digestRequests).isEqualTo(2);
    }

    private static RemoteRepository centralRepository() {
        return new RemoteRepository.Builder("central", "default", "https://repo1.maven.org/maven2").build();
    }

    private static List<Byte> bytesFrom(ByteBuffer buffer) {
        ByteBuffer duplicate = buffer.asReadOnlyBuffer();
        List<Byte> bytes = new ArrayList<>();
        while (duplicate.hasRemaining()) {
            bytes.add(duplicate.get());
        }
        return bytes;
    }

    private static final class TestForwardingSession extends AbstractForwardingRepositorySystemSession {
        private final RepositorySystemSession session;

        private TestForwardingSession(RepositorySystemSession session) {
            this.session = session;
        }

        @Override
        protected RepositorySystemSession getSession() {
            return session;
        }
    }

    private static final class TestAuthentication implements Authentication {
        private final String username;
        private final char[] password;
        private final String privateKeyPath;
        private int fillRequests;
        private int digestRequests;

        private TestAuthentication(String username, char[] password, String privateKeyPath) {
            this.username = username;
            this.password = password;
            this.privateKeyPath = privateKeyPath;
        }

        @Override
        public void fill(AuthenticationContext context, String key, Map<String, String> data) {
            fillRequests++;
            if (AuthenticationContext.USERNAME.equals(key)) {
                context.put(key, username);
            } else if (AuthenticationContext.PASSWORD.equals(key)) {
                context.put(key, password.clone());
            } else if (AuthenticationContext.PRIVATE_KEY_PATH.equals(key)) {
                context.put(key, privateKeyPath);
            }
        }

        @Override
        public void digest(AuthenticationDigest digest) {
            digestRequests++;
            digest.update(username);
            digest.update(password);
            digest.update(privateKeyPath);
        }
    }
}
