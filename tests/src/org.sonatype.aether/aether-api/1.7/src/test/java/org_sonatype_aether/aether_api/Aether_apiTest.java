/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_aether.aether_api;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.sonatype.aether.RepositoryException;
import org.sonatype.aether.collection.CollectRequest;
import org.sonatype.aether.collection.CollectResult;
import org.sonatype.aether.collection.DependencyManagement;
import org.sonatype.aether.deployment.DeployRequest;
import org.sonatype.aether.deployment.DeployResult;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.graph.DependencyNode;
import org.sonatype.aether.graph.DependencyVisitor;
import org.sonatype.aether.graph.Exclusion;
import org.sonatype.aether.installation.InstallRequest;
import org.sonatype.aether.installation.InstallResult;
import org.sonatype.aether.metadata.Metadata;
import org.sonatype.aether.repository.Authentication;
import org.sonatype.aether.repository.LocalArtifactRegistration;
import org.sonatype.aether.repository.LocalArtifactRequest;
import org.sonatype.aether.repository.LocalArtifactResult;
import org.sonatype.aether.repository.LocalRepository;
import org.sonatype.aether.repository.Proxy;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.repository.RepositoryPolicy;
import org.sonatype.aether.repository.WorkspaceRepository;
import org.sonatype.aether.resolution.ArtifactDescriptorRequest;
import org.sonatype.aether.resolution.ArtifactDescriptorResult;
import org.sonatype.aether.resolution.ArtifactRequest;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.resolution.MetadataRequest;
import org.sonatype.aether.resolution.MetadataResult;
import org.sonatype.aether.resolution.VersionRangeRequest;
import org.sonatype.aether.resolution.VersionRangeResult;
import org.sonatype.aether.resolution.VersionRequest;
import org.sonatype.aether.resolution.VersionResult;
import org.sonatype.aether.transfer.ArtifactNotFoundException;
import org.sonatype.aether.transfer.MetadataNotFoundException;
import org.sonatype.aether.version.Version;
import org.sonatype.aether.version.VersionConstraint;
import org.sonatype.aether.version.VersionRange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class Aether_apiTest {

    @Test
    void repositoryValueObjectsAreImmutableAndComparable() {
        Authentication baseAuthentication = new Authentication("alice", "secret");
        Authentication withKey = baseAuthentication
                .setPrivateKeyFile("/keys/id_rsa")
                .setPassphrase("changeit");
        Authentication sameAsWithKey = new Authentication("alice", "secret", "/keys/id_rsa", "changeit");

        assertThat(baseAuthentication.getUsername()).isEqualTo("alice");
        assertThat(baseAuthentication.getPrivateKeyFile()).isNull();
        assertThat(withKey.getPrivateKeyFile()).isEqualTo("/keys/id_rsa");
        assertThat(withKey.getPassphrase()).isEqualTo("changeit");
        assertThat(withKey).isEqualTo(sameAsWithKey);
        assertThat(withKey.hashCode()).isEqualTo(sameAsWithKey.hashCode());
        assertThat(withKey.toString()).isEqualTo("alice");

        RepositoryPolicy snapshots = new RepositoryPolicy(true,
                RepositoryPolicy.UPDATE_POLICY_DAILY,
                RepositoryPolicy.CHECKSUM_POLICY_WARN);
        RepositoryPolicy disabledSnapshots = snapshots.setEnabled(false);
        RepositoryPolicy alwaysUpdate = snapshots.setUpdatePolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
        RepositoryPolicy ignoredChecksums = snapshots.setChecksumPolicy(RepositoryPolicy.CHECKSUM_POLICY_IGNORE);

        assertThat(disabledSnapshots.isEnabled()).isFalse();
        assertThat(snapshots.isEnabled()).isTrue();
        assertThat(alwaysUpdate.getUpdatePolicy()).isEqualTo(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
        assertThat(ignoredChecksums.getChecksumPolicy()).isEqualTo(RepositoryPolicy.CHECKSUM_POLICY_IGNORE);
        assertThat(snapshots.toString()).contains("enabled=true", "checksums=warn", "updates=daily");

        Proxy proxy = new Proxy(Proxy.TYPE_HTTPS, "proxy.example.org", 9443, withKey);
        Proxy retargetedProxy = proxy.setHost("mirror.example.org").setPort(8443);

        assertThat(proxy.getAuthentication()).isEqualTo(withKey);
        assertThat(proxy.getType()).isEqualTo(Proxy.TYPE_HTTPS);
        assertThat(proxy.toString()).isEqualTo("proxy.example.org:9443");
        assertThat(retargetedProxy.getHost()).isEqualTo("mirror.example.org");
        assertThat(retargetedProxy.getPort()).isEqualTo(8443);
        assertThat(retargetedProxy).isEqualTo(new Proxy(Proxy.TYPE_HTTPS, "mirror.example.org", 8443, withKey));
        assertThat(retargetedProxy.hashCode())
                .isEqualTo(new Proxy(Proxy.TYPE_HTTPS, "mirror.example.org", 8443, withKey).hashCode());
    }

    @Test
    void remoteRepositoryTracksPoliciesMirrorsAndConnectivityState() {
        RepositoryPolicy releases = new RepositoryPolicy(true,
                RepositoryPolicy.UPDATE_POLICY_NEVER,
                RepositoryPolicy.CHECKSUM_POLICY_FAIL);
        RepositoryPolicy snapshots = new RepositoryPolicy(true,
                RepositoryPolicy.UPDATE_POLICY_DAILY,
                RepositoryPolicy.CHECKSUM_POLICY_WARN);
        Authentication authentication = new Authentication("deploy", "s3cr3t");
        Proxy proxy = new Proxy(Proxy.TYPE_HTTP, "proxy.example.org", 8080, authentication);
        RemoteRepository mirror = new RemoteRepository("mirror", "default", "https://mirror.example.org/maven2");
        RemoteRepository repository = new RemoteRepository("central", "default", "https://repo.example.org/releases")
                .setPolicy(true, releases)
                .setPolicy(false, snapshots)
                .setAuthentication(authentication)
                .setProxy(proxy)
                .setMirroredRepositories(List.of(mirror))
                .setRepositoryManager(true);

        assertThat(repository.getId()).isEqualTo("central");
        assertThat(repository.getContentType()).isEqualTo("default");
        assertThat(repository.getUrl()).isEqualTo("https://repo.example.org/releases");
        assertThat(repository.getProtocol()).isEqualTo("https");
        assertThat(repository.getHost()).isEqualTo("repo.example.org");
        assertThat(repository.getPolicy(true)).isEqualTo(releases);
        assertThat(repository.getPolicy(false)).isEqualTo(snapshots);
        assertThat(repository.getAuthentication()).isEqualTo(authentication);
        assertThat(repository.getProxy()).isEqualTo(proxy);
        assertThat(repository.getMirroredRepositories()).containsExactly(mirror);
        assertThat(repository.isRepositoryManager()).isTrue();

        RemoteRepository copy = new RemoteRepository(repository);
        copy.setUrl("https://repo.example.org/snapshots");

        assertThat(new RemoteRepository(repository)).isEqualTo(repository);
        assertThat(copy.getUrl()).isEqualTo("https://repo.example.org/snapshots");
        assertThat(repository.getUrl()).isEqualTo("https://repo.example.org/releases");
        assertThat(repository.toString()).contains("central", "https://repo.example.org/releases");
    }

    @Test
    void localAndWorkspaceRepositoriesExposeStableIdentity() {
        File localDir = Path.of("build", "aether-local-repo").toFile();
        LocalRepository localRepository = new LocalRepository(localDir, "enhanced");
        LocalRepository equivalentLocalRepository = new LocalRepository(localDir, "enhanced");
        WorkspaceRepository workspaceRepository = new WorkspaceRepository("reactor", "build-42");
        WorkspaceRepository equivalentWorkspaceRepository = new WorkspaceRepository("reactor", "build-42");

        assertThat(localRepository.getId()).isEqualTo("local");
        assertThat(localRepository.getContentType()).isEqualTo("enhanced");
        assertThat(localRepository.getBasedir()).isEqualTo(localDir);
        assertThat(localRepository).isEqualTo(equivalentLocalRepository);
        assertThat(localRepository.hashCode()).isEqualTo(equivalentLocalRepository.hashCode());
        assertThat(localRepository.toString()).contains(localDir.getAbsolutePath(), "(enhanced)");

        assertThat(workspaceRepository.getId()).isEqualTo("workspace");
        assertThat(workspaceRepository.getContentType()).isEqualTo("reactor");
        assertThat(workspaceRepository.getKey()).isEqualTo("build-42");
        assertThat(workspaceRepository).isEqualTo(equivalentWorkspaceRepository);
        assertThat(workspaceRepository.hashCode()).isEqualTo(equivalentWorkspaceRepository.hashCode());
        assertThat(workspaceRepository.toString()).isEqualTo("(reactor)");
    }

    @Test
    void localArtifactCacheLookupAndRegistrationRetainRepositoryOriginAndAvailability() {
        TestArtifact cachedArtifact = artifact("org.example", "demo", "1.0.0");
        RemoteRepository repository = new RemoteRepository("central", "default", "https://repo.example.org/maven2");
        File cachedFile = Path.of("build", "aether-local-repo", "org", "example", "demo.jar").toFile();

        LocalArtifactRequest request = new LocalArtifactRequest(cachedArtifact, List.of(repository), null);

        assertThat(request.getArtifact()).isEqualTo(cachedArtifact);
        assertThat(request.getRepositories()).containsExactly(repository);
        assertThat(request.getContext()).isEmpty();
        assertThat(request.toString()).contains(cachedArtifact.toString(), repository.toString());

        request.setContext("runtime");
        request.setRepositories(null);

        assertThat(request.getContext()).isEqualTo("runtime");
        assertThat(request.getRepositories()).isEmpty();

        LocalArtifactResult result = new LocalArtifactResult(request)
                .setFile(cachedFile)
                .setAvailable(true);

        assertThat(result.getRequest()).isSameAs(request);
        assertThat(result.getFile()).isEqualTo(cachedFile);
        assertThat(result.isAvailable()).isTrue();
        assertThat(result.toString()).isEqualTo(cachedFile + "(available)");

        result.setAvailable(false);

        assertThat(result.isAvailable()).isFalse();
        assertThat(result.toString()).isEqualTo(cachedFile + "(unavailable)");

        LocalArtifactRegistration registration = new LocalArtifactRegistration(cachedArtifact, repository,
                List.of("compile", "runtime"));

        assertThat(registration.getArtifact()).isEqualTo(cachedArtifact);
        assertThat(registration.getRepository()).isEqualTo(repository);
        assertThat(registration.getContexts()).containsExactly("compile", "runtime");

        registration.setContexts(null);

        assertThat(registration.getContexts()).isEmpty();
    }

    @Test
    void dependencyAndDependencyManagementModelArtifactStateWithoutReflection() {
        TestArtifact originalArtifact = artifact("org.example", "demo", "1.0.0");
        TestArtifact relocatedArtifact = artifact("org.example", "demo", "1.1.0");
        Exclusion firstExclusion = new Exclusion("org.legacy", "old-lib", "sources", "jar");
        Exclusion secondExclusion = new Exclusion("org.legacy", "other-lib", "", "jar");
        Dependency dependency = new Dependency(originalArtifact, "runtime", true, List.of(firstExclusion, secondExclusion));
        Dependency reorderedDependency = new Dependency(originalArtifact, "runtime", true, List.of(secondExclusion, firstExclusion));
        Dependency mutatedDependency = dependency
                .setArtifact(relocatedArtifact)
                .setScope("compile")
                .setOptional(false)
                .setExclusions(List.of(firstExclusion));

        assertThat(dependency.getArtifact()).isEqualTo(originalArtifact);
        assertThat(dependency.getScope()).isEqualTo("runtime");
        assertThat(dependency.isOptional()).isTrue();
        assertThat(dependency.getExclusions()).containsExactly(firstExclusion, secondExclusion);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> dependency.getExclusions().add(firstExclusion));
        assertThat(dependency).isEqualTo(reorderedDependency);
        assertThat(dependency.hashCode()).isEqualTo(reorderedDependency.hashCode());
        assertThat(dependency.toString()).isEqualTo(originalArtifact + " (runtime?)");

        assertThat(mutatedDependency.getArtifact()).isEqualTo(relocatedArtifact);
        assertThat(mutatedDependency.getScope()).isEqualTo("compile");
        assertThat(mutatedDependency.isOptional()).isFalse();
        assertThat(mutatedDependency.getExclusions()).containsExactly(firstExclusion);
        assertThat(dependency.getArtifact()).isEqualTo(originalArtifact);

        DependencyManagement management = new DependencyManagement()
                .setVersion("2.0.0")
                .setScope("test")
                .setExclusions(List.of(firstExclusion))
                .setProperties(Map.of("classifier", "tests"));

        assertThat(management.getVersion()).isEqualTo("2.0.0");
        assertThat(management.getScope()).isEqualTo("test");
        assertThat(management.getExclusions()).containsExactly(firstExclusion);
        assertThat(management.getProperties()).containsEntry("classifier", "tests");
    }

    @Test
    void requestAndResultContainersAggregateArtifactsMetadataDependenciesAndRepositories() {
        TestArtifact rootArtifact = artifact("org.example", "root", "1.0.0");
        TestArtifact childArtifact = artifact("org.example", "child", "1.1.0");
        TestMetadata metadata = metadata("org.example", "root", "1.0.0", "maven-metadata.xml");
        Dependency rootDependency = new Dependency(rootArtifact, "compile");
        Dependency childDependency = new Dependency(childArtifact, "runtime");
        RemoteRepository repository = new RemoteRepository("central", "default", "https://repo.example.org/maven2");
        TestDependencyNode dependencyNode = new TestDependencyNode(rootDependency)
                .withRepositories(List.of(repository))
                .withRequestContext("compile")
                .withChildren(List.of(new TestDependencyNode(childDependency)));

        CollectRequest collectRequest = new CollectRequest()
                .setRoot(rootDependency)
                .addDependency(childDependency)
                .addManagedDependency(new Dependency(childArtifact, "test"))
                .addRepository(repository)
                .setRequestContext("plugin");
        CollectResult collectResult = new CollectResult(collectRequest)
                .setRoot(dependencyNode)
                .addException(new IllegalStateException("partial graph"));

        ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest(rootArtifact, List.of(repository), "compile");
        ArtifactDescriptorResult descriptorResult = new ArtifactDescriptorResult(descriptorRequest)
                .setArtifact(rootArtifact)
                .addRelocation(childArtifact)
                .addAlias(rootArtifact)
                .addDependency(childDependency)
                .addManagedDependency(new Dependency(childArtifact, "test"))
                .addRepository(repository)
                .setRepository(repository)
                .setProperties(Map.of("packaging", "jar"));

        InstallRequest installRequest = new InstallRequest()
                .addArtifact(rootArtifact)
                .addMetadata(metadata);
        InstallResult installResult = new InstallResult(installRequest)
                .addArtifact(rootArtifact)
                .addMetadata(metadata);

        DeployRequest deployRequest = new DeployRequest()
                .addArtifact(rootArtifact)
                .addMetadata(metadata)
                .setRepository(repository);
        DeployResult deployResult = new DeployResult(deployRequest)
                .addArtifact(rootArtifact)
                .addMetadata(metadata);

        assertThat(collectRequest.getRoot()).isEqualTo(rootDependency);
        assertThat(collectRequest.getDependencies()).containsExactly(childDependency);
        assertThat(collectRequest.getManagedDependencies()).hasSize(1);
        assertThat(collectRequest.getRepositories()).containsExactly(repository);
        assertThat(collectRequest.getRequestContext()).isEqualTo("plugin");
        assertThat(collectResult.getRequest()).isSameAs(collectRequest);
        assertThat(collectResult.getRoot()).isSameAs(dependencyNode);
        assertThat(collectResult.getExceptions()).singleElement().isInstanceOf(IllegalStateException.class);

        assertThat(descriptorResult.getRequest()).isSameAs(descriptorRequest);
        assertThat(descriptorResult.getArtifact()).isEqualTo(rootArtifact);
        assertThat(descriptorResult.getRelocations()).containsExactly(childArtifact);
        assertThat(descriptorResult.getAliases()).containsExactly(rootArtifact);
        assertThat(descriptorResult.getDependencies()).containsExactly(childDependency);
        assertThat(descriptorResult.getManagedDependencies()).hasSize(1);
        assertThat(descriptorResult.getRepositories()).containsExactly(repository);
        assertThat(descriptorResult.getRepository()).isEqualTo(repository);
        assertThat(descriptorResult.getProperties()).containsEntry("packaging", "jar");

        assertThat(installRequest.getArtifacts()).containsExactly(rootArtifact);
        assertThat(installRequest.getMetadata()).containsExactly(metadata);
        assertThat(installResult.getRequest()).isSameAs(installRequest);
        assertThat(installResult.getArtifacts()).containsExactly(rootArtifact);
        assertThat(installResult.getMetadata()).containsExactly(metadata);

        assertThat(deployRequest.getArtifacts()).containsExactly(rootArtifact);
        assertThat(deployRequest.getMetadata()).containsExactly(metadata);
        assertThat(deployRequest.getRepository()).isEqualTo(repository);
        assertThat(deployResult.getRequest()).isSameAs(deployRequest);
        assertThat(deployResult.getArtifacts()).containsExactly(rootArtifact);
        assertThat(deployResult.getMetadata()).containsExactly(metadata);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ArtifactDescriptorResult(descriptorRequest).addDependency(null))
                .withMessageContaining("no dependency specified");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ArtifactDescriptorResult(descriptorRequest).addRepository(null))
                .withMessageContaining("no repository specified");
    }

    @Test
    void resolutionModelsTrackResolvedAndMissingStateAcrossArtifactsMetadataAndVersions() {
        TestArtifact resolvedArtifact = artifact("org.example", "demo", "1.0.0")
                .setFile(Path.of("build", "demo-1.0.0.jar").toFile());
        TestArtifact unresolvedArtifact = artifact("org.example", "demo", "1.0.1");
        TestMetadata resolvedMetadata = metadata("org.example", "demo", "1.0.0", "maven-metadata.xml")
                .setFile(Path.of("build", "maven-metadata.xml").toFile());
        TestMetadata missingMetadata = metadata("org.example", "demo", "1.0.1", "maven-metadata.xml");
        RemoteRepository repository = new RemoteRepository("central", "default", "https://repo.example.org/maven2");
        TestDependencyNode dependencyNode = new TestDependencyNode(new Dependency(resolvedArtifact, "compile"))
                .withRepositories(List.of(repository))
                .withRequestContext("runtime");

        ArtifactRequest requestFromNode = new ArtifactRequest(dependencyNode);
        ArtifactResult resolvedResult = new ArtifactResult(requestFromNode)
                .setArtifact(resolvedArtifact)
                .setRepository(repository);
        ArtifactResult missingResult = new ArtifactResult(new ArtifactRequest(unresolvedArtifact, List.of(repository), "runtime"))
                .addException(new ArtifactNotFoundException(unresolvedArtifact, repository, "not present"));

        MetadataRequest metadataRequest = new MetadataRequest(resolvedMetadata, repository, "metadata")
                .setDeleteLocalCopyIfMissing(true)
                .setFavorLocalRepository(true);
        MetadataResult metadataResult = new MetadataResult(metadataRequest)
                .setMetadata(resolvedMetadata)
                .setUpdated(true);
        MetadataResult missingMetadataResult = new MetadataResult(new MetadataRequest(missingMetadata, repository, "metadata"))
                .setException(new MetadataNotFoundException(missingMetadata, repository, "missing"));

        VersionRequest versionRequest = new VersionRequest(resolvedArtifact, List.of(repository), "version");
        VersionResult versionResult = new VersionResult(versionRequest)
                .setVersion("1.2.3")
                .setRepository(repository)
                .addException(new IllegalStateException("fallback consulted"));

        TestVersion lower = new TestVersion("1.0.0");
        TestVersion upper = new TestVersion("2.0.0");
        VersionRangeRequest versionRangeRequest = new VersionRangeRequest(resolvedArtifact, List.of(repository), "range");
        VersionRangeResult versionRangeResult = new VersionRangeResult(versionRangeRequest)
                .addVersion(lower)
                .addVersion(upper)
                .setRepository(lower, repository)
                .setRepository(upper, repository)
                .setVersionConstraint(new TestVersionConstraint(lower, List.of(version -> version.compareTo(lower) >= 0)));

        assertThat(requestFromNode.getArtifact()).isEqualTo(resolvedArtifact);
        assertThat(requestFromNode.getDependencyNode()).isSameAs(dependencyNode);
        assertThat(requestFromNode.getRepositories()).containsExactly(repository);
        assertThat(requestFromNode.getRequestContext()).isEqualTo("runtime");

        assertThat(resolvedResult.getRequest()).isSameAs(requestFromNode);
        assertThat(resolvedResult.getArtifact()).isEqualTo(resolvedArtifact);
        assertThat(resolvedResult.getRepository()).isEqualTo(repository);
        assertThat(resolvedResult.isResolved()).isTrue();
        assertThat(resolvedResult.isMissing()).isFalse();

        assertThat(missingResult.isResolved()).isFalse();
        assertThat(missingResult.isMissing()).isTrue();
        assertThat(missingResult.getExceptions()).singleElement().isInstanceOf(ArtifactNotFoundException.class);

        assertThat(metadataRequest.isDeleteLocalCopyIfMissing()).isTrue();
        assertThat(metadataRequest.isFavorLocalRepository()).isTrue();
        assertThat(metadataResult.getRequest()).isSameAs(metadataRequest);
        assertThat(metadataResult.isResolved()).isTrue();
        assertThat(metadataResult.isUpdated()).isTrue();
        assertThat(metadataResult.isMissing()).isFalse();
        assertThat(missingMetadataResult.isResolved()).isFalse();
        assertThat(missingMetadataResult.isMissing()).isTrue();

        assertThat(versionResult.getRequest()).isSameAs(versionRequest);
        assertThat(versionResult.getVersion()).isEqualTo("1.2.3");
        assertThat(versionResult.getRepository()).isEqualTo(repository);
        assertThat(versionResult.getExceptions()).singleElement().isInstanceOf(IllegalStateException.class);

        assertThat(versionRangeResult.getRequest()).isSameAs(versionRangeRequest);
        assertThat(versionRangeResult.getVersions()).containsExactly(lower, upper);
        assertThat(versionRangeResult.getRepository(lower)).isEqualTo(repository);
        assertThat(versionRangeResult.getRepository(upper)).isEqualTo(repository);
        assertThat(versionRangeResult.getVersionConstraint().getVersion()).isEqualTo(lower);
        assertThat(versionRangeResult.getVersionConstraint().containsVersion(upper)).isTrue();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ArtifactResult(null))
                .withMessageContaining("resolution request has not been specified");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MetadataResult(null))
                .withMessageContaining("metadata request has not been specified");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new VersionRangeResult(null))
                .withMessageContaining("version range request has not been specified");
    }

    @Test
    void repositoryExceptionsComposeMessagesFromCausesAndSupportVersionHelpers() {
        IllegalStateException explicitCause = new IllegalStateException("boom");
        IllegalArgumentException unnamedCause = new IllegalArgumentException();
        RepositoryException repositoryException = new RepositoryException("prefix: ", explicitCause);

        assertThat(RepositoryException.getMessage("prefix: ", explicitCause)).isEqualTo("prefix: boom");
        assertThat(RepositoryException.getMessage("prefix: ", unnamedCause)).isEqualTo("prefix: IllegalArgumentException");
        assertThat(RepositoryException.getMessage("prefix: ", null)).isEmpty();
        assertThat(repositoryException).hasCause(explicitCause);
        assertThat(repositoryException).hasMessage("prefix: ");

        assertThat(Metadata.Nature.values())
                .containsExactly(Metadata.Nature.RELEASE, Metadata.Nature.SNAPSHOT, Metadata.Nature.RELEASE_OR_SNAPSHOT);
    }

    private static TestArtifact artifact(String groupId, String artifactId, String version) {
        return new TestArtifact(groupId, artifactId, version, version, false, "", "jar", null, Map.of());
    }

    private static TestMetadata metadata(String groupId, String artifactId, String version, String type) {
        return new TestMetadata(groupId, artifactId, version, type, Metadata.Nature.RELEASE, null);
    }

    private static final class TestArtifact implements org.sonatype.aether.artifact.Artifact {
        private final String groupId;
        private final String artifactId;
        private final String version;
        private final String baseVersion;
        private final boolean snapshot;
        private final String classifier;
        private final String extension;
        private final File file;
        private final Map<String, String> properties;

        private TestArtifact(
                String groupId,
                String artifactId,
                String version,
                String baseVersion,
                boolean snapshot,
                String classifier,
                String extension,
                File file,
                Map<String, String> properties) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.baseVersion = baseVersion;
            this.snapshot = snapshot;
            this.classifier = classifier;
            this.extension = extension;
            this.file = file;
            this.properties = Map.copyOf(properties);
        }

        @Override
        public String getGroupId() {
            return groupId;
        }

        @Override
        public String getArtifactId() {
            return artifactId;
        }

        @Override
        public String getVersion() {
            return version;
        }

        @Override
        public TestArtifact setVersion(String newVersion) {
            boolean newSnapshot = newVersion != null && newVersion.endsWith("SNAPSHOT");
            return new TestArtifact(groupId, artifactId, newVersion, newVersion, newSnapshot, classifier, extension, file, properties);
        }

        @Override
        public String getBaseVersion() {
            return baseVersion;
        }

        @Override
        public boolean isSnapshot() {
            return snapshot;
        }

        @Override
        public String getClassifier() {
            return classifier;
        }

        @Override
        public String getExtension() {
            return extension;
        }

        @Override
        public File getFile() {
            return file;
        }

        @Override
        public TestArtifact setFile(File newFile) {
            return new TestArtifact(groupId, artifactId, version, baseVersion, snapshot, classifier, extension, newFile, properties);
        }

        @Override
        public String getProperty(String key, String defaultValue) {
            return properties.getOrDefault(key, defaultValue);
        }

        @Override
        public Map<String, String> getProperties() {
            return properties;
        }

        @Override
        public TestArtifact setProperties(Map<String, String> newProperties) {
            return new TestArtifact(groupId, artifactId, version, baseVersion, snapshot, classifier, extension, file,
                    newProperties == null ? Map.of() : newProperties);
        }

        @Override
        public String toString() {
            return groupId + ':' + artifactId + ':' + extension + ':' + version;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TestArtifact that)) {
                return false;
            }
            return snapshot == that.snapshot
                    && Objects.equals(groupId, that.groupId)
                    && Objects.equals(artifactId, that.artifactId)
                    && Objects.equals(version, that.version)
                    && Objects.equals(baseVersion, that.baseVersion)
                    && Objects.equals(classifier, that.classifier)
                    && Objects.equals(extension, that.extension)
                    && Objects.equals(file, that.file)
                    && Objects.equals(properties, that.properties);
        }

        @Override
        public int hashCode() {
            return Objects.hash(groupId, artifactId, version, baseVersion, snapshot, classifier, extension, file, properties);
        }
    }

    private static final class TestMetadata implements Metadata {
        private final String groupId;
        private final String artifactId;
        private final String version;
        private final String type;
        private final Nature nature;
        private final File file;

        private TestMetadata(String groupId, String artifactId, String version, String type, Nature nature, File file) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.type = type;
            this.nature = nature;
            this.file = file;
        }

        @Override
        public String getGroupId() {
            return groupId;
        }

        @Override
        public String getArtifactId() {
            return artifactId;
        }

        @Override
        public String getVersion() {
            return version;
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public Nature getNature() {
            return nature;
        }

        @Override
        public File getFile() {
            return file;
        }

        @Override
        public TestMetadata setFile(File newFile) {
            return new TestMetadata(groupId, artifactId, version, type, nature, newFile);
        }

        @Override
        public String toString() {
            return groupId + ':' + artifactId + ':' + type + ':' + version;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TestMetadata that)) {
                return false;
            }
            return Objects.equals(groupId, that.groupId)
                    && Objects.equals(artifactId, that.artifactId)
                    && Objects.equals(version, that.version)
                    && Objects.equals(type, that.type)
                    && nature == that.nature
                    && Objects.equals(file, that.file);
        }

        @Override
        public int hashCode() {
            return Objects.hash(groupId, artifactId, version, type, nature, file);
        }
    }

    private static final class TestVersion implements Version {
        private final String value;

        private TestVersion(String value) {
            this.value = value;
        }

        @Override
        public int compareTo(Version other) {
            return value.compareTo(other.toString());
        }

        @Override
        public String toString() {
            return value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TestVersion that)) {
                return false;
            }
            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    private static final class TestVersionConstraint implements VersionConstraint {
        private final Version version;
        private final Collection<VersionRange> ranges;

        private TestVersionConstraint(Version version, Collection<VersionRange> ranges) {
            this.version = version;
            this.ranges = List.copyOf(ranges);
        }

        @Override
        public Collection<VersionRange> getRanges() {
            return ranges;
        }

        @Override
        public Version getVersion() {
            return version;
        }

        @Override
        public boolean containsVersion(Version candidate) {
            if (ranges.isEmpty()) {
                return Objects.equals(version, candidate);
            }
            for (VersionRange range : ranges) {
                if (range.containsVersion(candidate)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class TestDependencyNode implements DependencyNode {
        private final Dependency dependency;
        private List<DependencyNode> children = new ArrayList<>();
        private List<org.sonatype.aether.artifact.Artifact> relocations = new ArrayList<>();
        private Collection<org.sonatype.aether.artifact.Artifact> aliases = new ArrayList<>();
        private VersionConstraint versionConstraint;
        private Version version;
        private String premanagedVersion = "";
        private String premanagedScope = "";
        private List<RemoteRepository> repositories = new ArrayList<>();
        private String requestContext = "";

        private TestDependencyNode(Dependency dependency) {
            this.dependency = dependency;
        }

        private TestDependencyNode withChildren(List<DependencyNode> newChildren) {
            this.children = new ArrayList<>(newChildren);
            return this;
        }

        private TestDependencyNode withRepositories(List<RemoteRepository> newRepositories) {
            this.repositories = new ArrayList<>(newRepositories);
            return this;
        }

        private TestDependencyNode withRequestContext(String newRequestContext) {
            this.requestContext = newRequestContext;
            return this;
        }

        @Override
        public List<DependencyNode> getChildren() {
            return children;
        }

        @Override
        public Dependency getDependency() {
            return dependency;
        }

        @Override
        public void setArtifact(org.sonatype.aether.artifact.Artifact artifact) {
            throw new UnsupportedOperationException("not needed in test fixture");
        }

        @Override
        public List<org.sonatype.aether.artifact.Artifact> getRelocations() {
            return relocations;
        }

        @Override
        public Collection<org.sonatype.aether.artifact.Artifact> getAliases() {
            return aliases;
        }

        @Override
        public VersionConstraint getVersionConstraint() {
            return versionConstraint;
        }

        @Override
        public Version getVersion() {
            return version;
        }

        @Override
        public void setScope(String scope) {
            this.premanagedScope = scope;
        }

        @Override
        public String getPremanagedVersion() {
            return premanagedVersion;
        }

        @Override
        public String getPremanagedScope() {
            return premanagedScope;
        }

        @Override
        public List<RemoteRepository> getRepositories() {
            return repositories;
        }

        @Override
        public String getRequestContext() {
            return requestContext;
        }

        @Override
        public void setRequestContext(String context) {
            this.requestContext = context == null ? "" : context;
        }

        @Override
        public boolean accept(DependencyVisitor visitor) {
            return visitor.visitEnter(this) && visitor.visitLeave(this);
        }
    }
}
