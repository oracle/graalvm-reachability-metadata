/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_resolver.maven_resolver_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.DefaultSessionData;
import org.eclipse.aether.MultiRuntimeException;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.SessionData;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.artifact.DefaultArtifactType;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.installation.InstallRequest;
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
import org.eclipse.aether.resolution.VersionRequest;
import org.eclipse.aether.resolution.VersionResult;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Maven_resolver_apiTest {
    @Test
    void defaultArtifactParsesCoordinatesAndPreservesImmutableValueSemantics(@TempDir Path tempDir) {
        Map<String, String> inputProperties = Map.of("build", "main");
        Artifact artifact = new DefaultArtifact("org.example:demo:tests:classifier:1.0-20240101.123456-7",
                inputProperties);

        assertThat(artifact.getGroupId()).isEqualTo("org.example");
        assertThat(artifact.getArtifactId()).isEqualTo("demo");
        assertThat(artifact.getExtension()).isEqualTo("tests");
        assertThat(artifact.getClassifier()).isEqualTo("classifier");
        assertThat(artifact.getVersion()).isEqualTo("1.0-20240101.123456-7");
        assertThat(artifact.isSnapshot()).isTrue();
        assertThat(artifact.getBaseVersion()).isEqualTo("1.0-SNAPSHOT");
        assertThat(artifact.getProperty("build", "fallback")).isEqualTo("main");
        assertThat(artifact.getProperty("missing", "fallback")).isEqualTo("fallback");
        assertThat(artifact).hasToString("org.example:demo:tests:classifier:1.0-20240101.123456-7");
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> artifact.getProperties().put("x", "y"));

        File artifactFile = tempDir.resolve("demo-tests.jar").toFile();
        Artifact withFile = artifact.setVersion("1.0.0").setFile(artifactFile).setProperties(Map.of("runtime", "true"));

        assertThat(withFile).isNotSameAs(artifact);
        assertThat(withFile.getVersion()).isEqualTo("1.0.0");
        assertThat(withFile.getFile()).isEqualTo(artifactFile);
        assertThat(withFile.getProperties()).containsExactly(Map.entry("runtime", "true"));
        assertThat(artifact.getFile()).isNull();
        assertThat(artifact.getVersion()).isEqualTo("1.0-20240101.123456-7");
        assertThat(withFile.setFile(artifactFile)).isSameAs(withFile);

        Artifact defaulted = new DefaultArtifact("org.example:plain:1.2.3");
        assertThat(defaulted.getExtension()).isEqualTo("jar");
        assertThat(defaulted.getClassifier()).isEmpty();
        assertThat(defaulted.isSnapshot()).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> new DefaultArtifact("bad coordinates"));
    }

    @Test
    void artifactTypesSupplyDefaultsAndMergeProperties() {
        DefaultArtifactType javaSourceType = new DefaultArtifactType("java-source", "jar", "sources", "java");
        Artifact typedArtifact = new DefaultArtifact("org.example", "sources-demo", null, null, "2.0.0",
                Map.of(ArtifactProperties.LANGUAGE, "kotlin", "custom", "present"), javaSourceType);

        assertThat(javaSourceType.getId()).isEqualTo("java-source");
        assertThat(javaSourceType.getExtension()).isEqualTo("jar");
        assertThat(javaSourceType.getClassifier()).isEqualTo("sources");
        assertThat(javaSourceType.getProperties()).containsEntry(ArtifactProperties.TYPE, "java-source")
                .containsEntry(ArtifactProperties.CONSTITUTES_BUILD_PATH, "true")
                .containsEntry(ArtifactProperties.INCLUDES_DEPENDENCIES, "false");
        assertThat(typedArtifact.getExtension()).isEqualTo("jar");
        assertThat(typedArtifact.getClassifier()).isEqualTo("sources");
        assertThat(typedArtifact.getProperties()).containsEntry(ArtifactProperties.TYPE, "java-source")
                .containsEntry(ArtifactProperties.LANGUAGE, "kotlin")
                .containsEntry("custom", "present");

        DefaultArtifactType binaryType = new DefaultArtifactType("binary", "bin", "", Map.of("format", "native"));
        assertThat(binaryType.getProperties()).containsExactly(Map.entry("format", "native"));
        assertThatNullPointerException().isThrownBy(() -> new DefaultArtifactType(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new DefaultArtifactType(""));
    }

    @Test
    void dependencyGraphNodesTrackDependenciesAndVisitorTraversal() {
        Artifact rootArtifact = new DefaultArtifact("org.example:root:1.0.0");
        Artifact childArtifact = new DefaultArtifact("org.example:child:jar:tests:1.1.0");
        Exclusion exclusion = new Exclusion("org.unwanted", "legacy", "", "jar");
        Dependency dependency = new Dependency(rootArtifact, "compile", false, List.of(exclusion));
        Dependency optionalRuntime = dependency.setArtifact(childArtifact).setScope("runtime").setOptional(true);

        assertThat(dependency.getArtifact()).isEqualTo(rootArtifact);
        assertThat(dependency.getScope()).isEqualTo("compile");
        assertThat(dependency.isOptional()).isFalse();
        assertThat(dependency.getOptional()).isFalse();
        assertThat(dependency.getExclusions()).containsExactly(exclusion);
        assertThat(dependency.setScope("compile")).isSameAs(dependency);
        assertThat(optionalRuntime.getArtifact()).isEqualTo(childArtifact);
        assertThat(optionalRuntime.getScope()).isEqualTo("runtime");
        assertThat(optionalRuntime.isOptional()).isTrue();
        assertThat(optionalRuntime).hasToString("org.example:child:jar:tests:1.1.0 (runtime?)");
        assertThat(exclusion).hasToString("org.unwanted:legacy:jar");
        assertThatThrownBy(() -> new Dependency(null, "compile")).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("artifact");

        DefaultDependencyNode root = new DefaultDependencyNode(dependency);
        DefaultDependencyNode child = new DefaultDependencyNode(optionalRuntime);
        root.setChildren(new ArrayList<>(List.of(child)));
        root.setManagedBits(0xFF);
        root.setRequestContext(new String("project"));
        root.setData("scope", "managed");
        child.setData(Map.of("depth", 1));

        assertThat(root.getArtifact()).isEqualTo(rootArtifact);
        assertThat(root.getManagedBits()).isEqualTo(0x1F);
        assertThat(root.getRequestContext()).isEqualTo("project");
        assertThat(root.getData()).containsEntry("scope", "managed");
        root.setScope("test");
        root.setOptional(null);
        root.setArtifact(childArtifact);
        assertThat(root.getDependency().getScope()).isEqualTo("test");
        assertThat(root.getDependency().getOptional()).isNull();
        assertThat(root.getArtifact()).isEqualTo(childArtifact);

        List<String> visits = new ArrayList<>();
        boolean completed = root.accept(new RecordingVisitor(visits));
        assertThat(completed).isTrue();
        assertThat(visits).containsExactly("enter:child", "enter:child", "leave:child", "leave:child");

        DefaultDependencyNode copied = new DefaultDependencyNode(root);
        assertThat(copied.getChildren()).isEmpty();
        assertThat(copied.getDependency()).isEqualTo(root.getDependency());
        assertThat(copied.getData()).containsEntry("scope", "managed");

        DefaultDependencyNode labelOnlyRoot = new DefaultDependencyNode(rootArtifact);
        assertThatIllegalStateException().isThrownBy(() -> labelOnlyRoot.setScope("compile"));
    }

    @Test
    void remoteAndLocalRepositoriesExposePoliciesIdentityAndBuilderCopies() {
        RepositoryPolicy releases = new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_ALWAYS,
                RepositoryPolicy.CHECKSUM_POLICY_FAIL);
        RepositoryPolicy snapshots = new RepositoryPolicy(false, RepositoryPolicy.UPDATE_POLICY_NEVER,
                RepositoryPolicy.CHECKSUM_POLICY_IGNORE);
        Proxy proxy = new Proxy(Proxy.TYPE_HTTPS, "proxy.example.org", 8443);
        RemoteRepository central = new RemoteRepository.Builder("central", "default",
                "https://repo.maven.apache.org/maven2")
                .setReleasePolicy(releases)
                .setSnapshotPolicy(snapshots)
                .setProxy(proxy)
                .setRepositoryManager(true)
                .build();

        assertThat(central.getId()).isEqualTo("central");
        assertThat(central.getContentType()).isEqualTo("default");
        assertThat(central.getUrl()).isEqualTo("https://repo.maven.apache.org/maven2");
        assertThat(central.getProtocol()).isEqualTo("https");
        assertThat(central.getHost()).isEqualTo("repo.maven.apache.org");
        assertThat(central.getPolicy(false)).isEqualTo(releases);
        assertThat(central.getPolicy(true)).isEqualTo(snapshots);
        assertThat(central.getProxy()).isEqualTo(proxy);
        assertThat(central.isRepositoryManager()).isTrue();
        assertThat(central.isBlocked()).isFalse();
        assertThat(central).hasToString(
                "central (https://repo.maven.apache.org/maven2, default, releases, managed)");

        RemoteRepository mirrorOfCentral = new RemoteRepository.Builder("mirror", "default",
                "http://user@mirror.example.org/repository")
                .addMirroredRepository(central)
                .setBlocked(true)
                .build();
        assertThat(mirrorOfCentral.getProtocol()).isEqualTo("http");
        assertThat(mirrorOfCentral.getHost()).isEqualTo("mirror.example.org");
        assertThat(mirrorOfCentral.getMirroredRepositories()).containsExactly(central);
        assertThat(mirrorOfCentral.isBlocked()).isTrue();
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> mirrorOfCentral.getMirroredRepositories().add(central));

        RemoteRepository sameInstance = new RemoteRepository.Builder(central).build();
        RemoteRepository changedUrl = new RemoteRepository.Builder(central).setUrl("file:/tmp/repo").build();
        assertThat(sameInstance).isSameAs(central);
        assertThat(changedUrl).isNotEqualTo(central);
        assertThat(changedUrl.getProtocol()).isEqualTo("file");
        assertThat(changedUrl.getHost()).isEmpty();

        LocalRepository localRepository = new LocalRepository(new File("target/local-repo"), "enhanced");
        assertThat(localRepository.getId()).isEqualTo("local");
        assertThat(localRepository.getContentType()).isEqualTo("enhanced");
        assertThat(localRepository).hasToString("target/local-repo (enhanced)");
        assertThat(new Proxy(Proxy.TYPE_HTTPS, "proxy.example.org", 8443)).isEqualTo(proxy);
    }

    @Test
    void requestObjectsAccumulateArtifactsMetadataRepositoriesAndTraces() {
        Artifact artifact = new DefaultArtifact("org.example:request-demo:1.0.0");
        Dependency dependency = new Dependency(artifact, "compile");
        DefaultDependencyNode node = new DefaultDependencyNode(dependency);
        RemoteRepository repository = new RemoteRepository.Builder("central", "default", "https://repo.example.org")
                .build();
        Metadata metadata = new DefaultMetadata("org.example", "request-demo", "1.0.0", "maven-metadata.xml",
                Metadata.Nature.RELEASE);
        RequestTrace trace = new RequestTrace("root-operation").newChild("resolve-artifact");
        node.setRepositories(List.of(repository));
        node.setRequestContext("build");

        CollectRequest collectRequest = new CollectRequest()
                .setRoot(dependency)
                .setRootArtifact(artifact)
                .setTrace(trace)
                .setRequestContext("collection")
                .addDependency(new Dependency(new DefaultArtifact("org.example:direct:1.0.0"), "runtime"))
                .addManagedDependency(new Dependency(new DefaultArtifact("org.example:managed:2.0.0"), "compile"))
                .addRepository(repository);
        assertThat(collectRequest.getRoot()).isEqualTo(dependency);
        assertThat(collectRequest.getRootArtifact()).isEqualTo(artifact);
        assertThat(collectRequest.getDependencies()).hasSize(1);
        assertThat(collectRequest.getManagedDependencies()).hasSize(1);
        assertThat(collectRequest.getRepositories()).containsExactly(repository);
        assertThat(collectRequest.getTrace()).isSameAs(trace);
        assertThat(collectRequest.getRequestContext()).isEqualTo("collection");

        ArtifactRequest artifactRequest = new ArtifactRequest(node).setTrace(trace);
        assertThat(artifactRequest.getArtifact()).isEqualTo(artifact);
        assertThat(artifactRequest.getDependencyNode()).isSameAs(node);
        assertThat(artifactRequest.getRepositories()).containsExactly(repository);
        assertThat(artifactRequest.getRequestContext()).isEqualTo("build");
        assertThat(artifactRequest.getTrace()).isSameAs(trace);

        VersionRequest versionRequest = new VersionRequest(artifact, null, null)
                .addRepository(repository)
                .setTrace(trace);
        assertThat(versionRequest.getArtifact()).isEqualTo(artifact);
        assertThat(versionRequest.getRepositories()).containsExactly(repository);
        assertThat(versionRequest.getRequestContext()).isEmpty();
        assertThat(versionRequest.getTrace()).isSameAs(trace);

        InstallRequest installRequest = new InstallRequest()
                .addArtifact(artifact)
                .addMetadata(metadata)
                .setTrace(trace);
        DeployRequest deployRequest = new DeployRequest().addArtifact(artifact).addMetadata(metadata)
                .setRepository(repository)
                .setTrace(trace);
        assertThat(installRequest.getArtifacts()).containsExactly(artifact);
        assertThat(installRequest.getMetadata()).containsExactly(metadata);
        assertThat(installRequest.getTrace()).isSameAs(trace);
        assertThat(deployRequest.getArtifacts()).containsExactly(artifact);
        assertThat(deployRequest.getMetadata()).containsExactly(metadata);
        assertThat(deployRequest.getRepository()).isSameAs(repository);
        assertThat(deployRequest.getTrace()).isSameAs(trace);
    }

    @Test
    void artifactDescriptorResultsExposeRelocationsAliasesManagedDependenciesAndProperties() {
        Artifact requestedArtifact = new DefaultArtifact("org.example:descriptor-demo:1.0.0");
        Artifact describedArtifact = new DefaultArtifact("org.example:descriptor-demo:jar:classes:1.0.0");
        Artifact relocatedArtifact = new DefaultArtifact("org.relocated:descriptor-demo:1.0.0");
        Artifact aliasArtifact = new DefaultArtifact("org.alias:descriptor-demo:1.0.0");
        Dependency runtimeDependency = new Dependency(new DefaultArtifact("org.example:runtime-lib:1.1.0"), "runtime");
        Dependency managedDependency = new Dependency(new DefaultArtifact("org.example:managed-lib:2.0.0"), "compile");
        RemoteRepository requestRepository = new RemoteRepository.Builder("central", "default", "https://repo.example.org")
                .build();
        RemoteRepository descriptorRepository = new RemoteRepository.Builder("descriptor", "default",
                "https://descriptor.example.org")
                .build();
        RequestTrace trace = new RequestTrace("descriptor-read");
        ArtifactDescriptorRequest request = new ArtifactDescriptorRequest(requestedArtifact, List.of(requestRepository),
                "descriptor")
                .setTrace(trace);

        ArtifactDescriptorResult result = new ArtifactDescriptorResult(request)
                .setArtifact(describedArtifact)
                .setRepository(requestRepository)
                .addRelocation(relocatedArtifact)
                .addAlias(aliasArtifact)
                .addDependency(runtimeDependency)
                .addManagedDependency(managedDependency)
                .addRepository(descriptorRepository)
                .setProperties(Map.of("packaging", "jar"))
                .addException(new IllegalStateException("optional descriptor warning"));

        assertThat(request.getArtifact()).isSameAs(requestedArtifact);
        assertThat(request.getRepositories()).containsExactly(requestRepository);
        assertThat(request.getRequestContext()).isEqualTo("descriptor");
        assertThat(request.getTrace()).isSameAs(trace);
        assertThat(result.getRequest()).isSameAs(request);
        assertThat(result.getArtifact()).isSameAs(describedArtifact);
        assertThat(result.getRepository()).isSameAs(requestRepository);
        assertThat(result.getRelocations()).containsExactly(relocatedArtifact);
        assertThat(result.getAliases()).containsExactly(aliasArtifact);
        assertThat(result.getDependencies()).containsExactly(runtimeDependency);
        assertThat(result.getManagedDependencies()).containsExactly(managedDependency);
        assertThat(result.getRepositories()).containsExactly(descriptorRepository);
        assertThat(result.getProperties()).containsExactly(Map.entry("packaging", "jar"));
        assertThat(result.getExceptions()).hasOnlyElementsOfType(IllegalStateException.class);
    }

    @Test
    void metadataAndResolutionResultsModelFilesPropertiesFailuresAndRepositories(@TempDir Path tempDir) {
        File metadataFile = tempDir.resolve("maven-metadata.xml").toFile();
        Metadata metadata = new DefaultMetadata("org.example", "demo", "1.0.0", "maven-metadata.xml",
                Metadata.Nature.RELEASE_OR_SNAPSHOT, Map.of("checksum", "sha1"), (Path) null);

        assertThat(metadata.getGroupId()).isEqualTo("org.example");
        assertThat(metadata.getArtifactId()).isEqualTo("demo");
        assertThat(metadata.getVersion()).isEqualTo("1.0.0");
        assertThat(metadata.getType()).isEqualTo("maven-metadata.xml");
        assertThat(metadata.getNature()).isEqualTo(Metadata.Nature.RELEASE_OR_SNAPSHOT);
        assertThat(metadata.getProperty("checksum", "missing")).isEqualTo("sha1");
        assertThat(metadata).hasToString("org.example:demo:1.0.0/maven-metadata.xml");
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> metadata.getProperties().put("x", "y"));

        Metadata withFile = metadata.setFile(metadataFile).setProperties(Map.of("state", "resolved"));
        assertThat(withFile).isNotSameAs(metadata);
        assertThat(withFile.getFile()).isEqualTo(metadataFile);
        assertThat(withFile.getProperties()).containsExactly(Map.entry("state", "resolved"));
        assertThat(metadata.getFile()).isNull();
        assertThat(metadata.setProperties(null)).isNotSameAs(metadata).extracting(Metadata::getProperties)
                .isEqualTo(Collections.emptyMap());

        RemoteRepository repository = new RemoteRepository.Builder("central", "default", "https://repo.example.org")
                .build();
        Artifact artifact = new DefaultArtifact("org.example:demo:1.0.0");
        ArtifactRequest artifactRequest = new ArtifactRequest(artifact, List.of(repository), "resolve");
        ArtifactResult unresolved = new ArtifactResult(artifactRequest)
                .setRepository(repository)
                .addException(new ArtifactNotFoundException(artifact, repository));
        assertThat(unresolved.isResolved()).isFalse();
        assertThat(unresolved.isMissing()).isTrue();
        assertThat(unresolved.getExceptions()).hasSize(1);
        assertThat(unresolved.getRepository()).isSameAs(repository);

        Artifact resolvedArtifact = artifact.setFile(tempDir.resolve("demo-1.0.0.jar").toFile());
        ArtifactResult resolved = new ArtifactResult(artifactRequest)
                .setArtifact(resolvedArtifact)
                .setRepository(repository);
        assertThat(resolved.isResolved()).isTrue();
        assertThat(resolved.isMissing()).isFalse();
        assertThat(resolved).hasToString(resolvedArtifact + " < " + repository);

        VersionResult versionResult = new VersionResult(new VersionRequest(artifact, List.of(repository), "version"))
                .setVersion("1.0.0")
                .setRepository(repository)
                .addException(new IllegalStateException("ignored mirror"));
        assertThat(versionResult.getVersion()).isEqualTo("1.0.0");
        assertThat(versionResult.getRepository()).isSameAs(repository);
        assertThat(versionResult.getExceptions()).hasOnlyElementsOfType(IllegalStateException.class);
        assertThat(versionResult).hasToString("1.0.0 @ " + repository);
    }

    @Test
    void sessionsStoreConfigurationDataAndBecomeReadOnlyOnDemand() {
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession()
                .setOffline(true)
                .setIgnoreArtifactDescriptorRepositories(true)
                .setChecksumPolicy(RepositoryPolicy.CHECKSUM_POLICY_FAIL)
                .setUpdatePolicy(RepositoryPolicy.UPDATE_POLICY_DAILY)
                .setSystemProperty("java.version", "21")
                .setUserProperty("profile", "native")
                .setConfigProperty("threads", 4);

        assertThat(session.isOffline()).isTrue();
        assertThat(session.isIgnoreArtifactDescriptorRepositories()).isTrue();
        assertThat(session.getChecksumPolicy()).isEqualTo(RepositoryPolicy.CHECKSUM_POLICY_FAIL);
        assertThat(session.getUpdatePolicy()).isEqualTo(RepositoryPolicy.UPDATE_POLICY_DAILY);
        assertThat(session.getSystemProperties()).containsEntry("java.version", "21");
        assertThat(session.getUserProperties()).containsEntry("profile", "native");
        assertThat(session.getConfigProperties()).containsEntry("threads", 4);
        assertThat(session.getMirrorSelector().getMirror(new RemoteRepository.Builder("id", "default", "file:/x")
                .build())).isNull();

        SessionData data = session.getData();
        data.set("artifact", "cached");
        assertThat(data.get("artifact")).isEqualTo("cached");
        assertThat(data.set("artifact", "cached", "updated")).isTrue();
        assertThat(data.get("artifact")).isEqualTo("updated");
        assertThat(data.set("artifact", "cached", null)).isFalse();

        DefaultSessionData standaloneData = new DefaultSessionData();
        AtomicInteger computations = new AtomicInteger();
        Object first = standaloneData.computeIfAbsent("key", () -> "value-" + computations.incrementAndGet());
        Object second = standaloneData.computeIfAbsent("key", () -> "value-" + computations.incrementAndGet());
        assertThat(first).isEqualTo("value-1");
        assertThat(second).isEqualTo("value-1");
        assertThat(computations).hasValue(1);
        assertThatNullPointerException().isThrownBy(() -> standaloneData.set(null, "value"));

        DefaultRepositorySystemSession copy = new DefaultRepositorySystemSession(session);
        copy.setReadOnly();
        assertThatIllegalStateException().isThrownBy(() -> copy.setOffline(false))
                .withMessage("repository system session is read-only");
    }

    @Test
    void authenticationContextsExposeRepositoryAndProxyCredentialsAndClearSecrets() {
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession()
                .setConfigProperty("realm", "build");
        CredentialAuthentication repositoryAuthentication = new CredentialAuthentication("repository-user",
                new char[] {'r', 'e', 'p', 'o'}, new File("keys/repository.pem"));
        CredentialAuthentication proxyAuthentication = new CredentialAuthentication("proxy-user",
                new char[] {'p', 'r', 'o', 'x', 'y'}, new File("keys/proxy.pem"));
        Proxy proxy = new Proxy(Proxy.TYPE_HTTP, "proxy.example.org", 8080, proxyAuthentication);
        RemoteRepository repository = new RemoteRepository.Builder("secure", "default", "https://repo.example.org")
                .setAuthentication(repositoryAuthentication)
                .setProxy(proxy)
                .build();

        AuthenticationContext repositoryContext = AuthenticationContext.forRepository(session, repository);
        assertThat(repositoryContext).isNotNull();
        assertThat(repositoryContext.getSession()).isSameAs(session);
        assertThat(repositoryContext.getRepository()).isSameAs(repository);
        assertThat(repositoryContext.getProxy()).isNull();
        assertThat(repositoryContext.get(AuthenticationContext.USERNAME)).isEqualTo("repository-user");
        char[] repositoryPassword = repositoryContext.get(AuthenticationContext.PASSWORD, char[].class);
        assertThat(repositoryPassword).containsExactly('r', 'e', 'p', 'o');
        assertThat(repositoryContext.get(AuthenticationContext.PASSWORD)).isEqualTo("repo");
        assertThat(repositoryContext.get(AuthenticationContext.PRIVATE_KEY_PATH, File.class))
                .isEqualTo(new File("keys/repository.pem"));
        assertThat(repositoryContext.get(AuthenticationContext.PRIVATE_KEY_PATH))
                .isEqualTo(new File("keys/repository.pem").getPath());

        AuthenticationContext proxyContext = AuthenticationContext.forProxy(session, repository);
        assertThat(proxyContext).isNotNull();
        assertThat(proxyContext.getSession()).isSameAs(session);
        assertThat(proxyContext.getRepository()).isSameAs(repository);
        assertThat(proxyContext.getProxy()).isSameAs(proxy);
        assertThat(proxyContext.get(AuthenticationContext.USERNAME)).isEqualTo("proxy-user");
        assertThat(proxyContext.get(AuthenticationContext.PASSWORD, char[].class)).containsExactly('p', 'r', 'o', 'x', 'y');

        String repositoryDigest = AuthenticationDigest.forRepository(session, repository);
        String proxyDigest = AuthenticationDigest.forProxy(session, repository);
        assertThat(repositoryDigest).isNotBlank();
        assertThat(proxyDigest).isNotBlank().isNotEqualTo(repositoryDigest);
        assertThat(AuthenticationDigest.forRepository(session, new RemoteRepository.Builder("anonymous", "default",
                "https://repo.example.org").build())).isEmpty();

        repositoryContext.close();
        assertThat(repositoryPassword).containsOnly('\0');
        AuthenticationContext.close(proxyContext);
        AuthenticationContext.close(null);
        assertThat(AuthenticationContext.forProxy(session, new RemoteRepository.Builder("direct", "default",
                "https://repo.example.org").build())).isNull();
    }

    @Test
    void repositoryAndTransferEventsExposeImmutableSnapshots() {
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession();
        Artifact artifact = new DefaultArtifact("org.example:event-demo:1.0.0");
        Metadata metadata = new DefaultMetadata("org.example", "event-demo", "maven-metadata.xml",
                Metadata.Nature.RELEASE);
        RemoteRepository repository = new RemoteRepository.Builder("central", "default", "https://repo.example.org")
                .build();
        RequestTrace trace = RequestTrace.newChild(new RequestTrace("root"), "download");
        Exception descriptorFailure = new IllegalArgumentException("invalid descriptor");

        RepositoryEvent event = new RepositoryEvent.Builder(session, RepositoryEvent.EventType.ARTIFACT_DOWNLOADED)
                .setArtifact(artifact)
                .setMetadata(metadata)
                .setRepository(repository)
                .setFile(new File("event-demo-1.0.0.jar"))
                .setException(descriptorFailure)
                .setTrace(trace)
                .build();

        assertThat(event.getType()).isEqualTo(RepositoryEvent.EventType.ARTIFACT_DOWNLOADED);
        assertThat(event.getSession()).isSameAs(session);
        assertThat(event.getArtifact()).isSameAs(artifact);
        assertThat(event.getMetadata()).isSameAs(metadata);
        assertThat(event.getRepository()).isSameAs(repository);
        assertThat(event.getException()).isSameAs(descriptorFailure);
        assertThat(event.getExceptions()).containsExactly(descriptorFailure);
        assertThat(event.getTrace()).isSameAs(trace);
        assertThat(event).hasToString("ARTIFACT_DOWNLOADED org.example:event-demo:jar:1.0.0 "
                + "org.example:event-demo/maven-metadata.xml (event-demo-1.0.0.jar) @ " + repository);

        TransferResource resource = new TransferResource("central", "https://repo.example.org/maven2",
                "/org/example/event-demo/1.0.0/event-demo-1.0.0.jar", new File("target/event.jar"), trace)
                .setContentLength(6L)
                .setResumeOffset(2L);
        byte[] bytes = new byte[] {0, 1, 2, 3, 4, 5};
        TransferEvent transferEvent = new TransferEvent.Builder(session, resource)
                .setRequestType(TransferEvent.RequestType.GET)
                .setType(TransferEvent.EventType.PROGRESSED)
                .setTransferredBytes(2L)
                .addTransferredBytes(3L)
                .setDataBuffer(bytes, 1, 3)
                .build();

        assertThat(resource.getRepositoryId()).isEqualTo("central");
        assertThat(resource.getRepositoryUrl()).isEqualTo("https://repo.example.org/maven2/");
        assertThat(resource.getResourceName()).isEqualTo("org/example/event-demo/1.0.0/event-demo-1.0.0.jar");
        assertThat(resource.getContentLength()).isEqualTo(6L);
        assertThat(resource.getResumeOffset()).isEqualTo(2L);
        assertThat(resource.getTrace()).isSameAs(trace);
        assertThat(resource.getTransferStartTime()).isLessThanOrEqualTo(System.currentTimeMillis());
        assertThatIllegalArgumentException().isThrownBy(() -> resource.setResumeOffset(-1L));

        assertThat(transferEvent.getRequestType()).isEqualTo(TransferEvent.RequestType.GET);
        assertThat(transferEvent.getType()).isEqualTo(TransferEvent.EventType.PROGRESSED);
        assertThat(transferEvent.getTransferredBytes()).isEqualTo(5L);
        assertThat(transferEvent.getDataLength()).isEqualTo(3);
        ByteBuffer dataBuffer = transferEvent.getDataBuffer();
        assertThat(dataBuffer.isReadOnly()).isTrue();
        assertThat(dataBuffer.remaining()).isEqualTo(3);
        assertThat(dataBuffer.get()).isEqualTo((byte) 1);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new TransferEvent.Builder(session, resource).setTransferredBytes(-1L));
        assertThatNullPointerException().isThrownBy(() -> new TransferEvent.Builder(null, resource));
    }

    @Test
    void multiRuntimeExceptionWrapsAllFailuresOnlyWhenFailuresExist() {
        MultiRuntimeException.mayThrow("nothing failed", Collections.emptyList());

        List<RuntimeException> failures = Arrays.asList(
                new IllegalStateException("first"),
                new IllegalArgumentException("second"));

        assertThatThrownBy(() -> MultiRuntimeException.mayThrow("two failures", failures))
                .isInstanceOf(MultiRuntimeException.class)
                .hasMessage("two failures")
                .satisfies(throwable -> {
                    MultiRuntimeException exception = (MultiRuntimeException) throwable;
                    assertThat(exception.getThrowables()).hasSize(2);
                    assertThat(exception.getThrowables().get(0)).isSameAs(failures.get(0));
                    assertThat(exception.getThrowables().get(1)).isSameAs(failures.get(1));
                    assertThat(exception.getSuppressed()).containsExactly(failures.toArray(new Throwable[0]));
                });
        assertThatNullPointerException().isThrownBy(() -> MultiRuntimeException.mayThrow(null, failures));
    }

    private static final class CredentialAuthentication implements Authentication {
        private final String username;
        private final char[] password;
        private final File privateKey;

        private CredentialAuthentication(String username, char[] password, File privateKey) {
            this.username = username;
            this.password = password.clone();
            this.privateKey = privateKey;
        }

        @Override
        public void fill(AuthenticationContext context, String key, Map<String, String> data) {
            String mappedKey = data != null ? data.getOrDefault(key, key) : key;
            if (AuthenticationContext.USERNAME.equals(mappedKey)) {
                context.put(key, username);
            } else if (AuthenticationContext.PASSWORD.equals(mappedKey)) {
                context.put(key, password.clone());
            } else if (AuthenticationContext.PRIVATE_KEY_PATH.equals(mappedKey)) {
                context.put(key, privateKey);
            }
        }

        @Override
        public void digest(AuthenticationDigest digest) {
            digest.update(username);
            digest.update(password);
            digest.update(privateKey.getPath());
        }
    }

    private static final class RecordingVisitor implements DependencyVisitor {
        private final Collection<String> visits;

        private RecordingVisitor(Collection<String> visits) {
            this.visits = visits;
        }

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
    }
}
