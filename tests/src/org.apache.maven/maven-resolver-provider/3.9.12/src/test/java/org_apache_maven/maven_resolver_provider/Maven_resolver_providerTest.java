/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_resolver_provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Prerequisites;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.ModelCache;
import org.apache.maven.repository.internal.ArtifactDescriptorReaderDelegate;
import org.apache.maven.repository.internal.ArtifactDescriptorUtils;
import org.apache.maven.repository.internal.DefaultModelCacheFactory;
import org.apache.maven.repository.internal.DefaultVersionRangeResolver;
import org.apache.maven.repository.internal.DefaultVersionResolver;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.maven.repository.internal.PluginsMetadataGeneratorFactory;
import org.apache.maven.repository.internal.RequestTraceHelper;
import org.apache.maven.repository.internal.SnapshotMetadataGeneratorFactory;
import org.apache.maven.repository.internal.VersionsMetadataGeneratorFactory;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryCache;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectStepData;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.impl.MetadataGenerator;
import org.eclipse.aether.impl.MetadataGeneratorFactory;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.metadata.MergeableMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.resolution.VersionRequest;
import org.eclipse.aether.resolution.VersionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Maven_resolver_providerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsMavenResolverSessionWithExpectedDefaults() {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        assertThat(session.getDependencyTraverser()).isNotNull();
        assertThat(session.getDependencyManager()).isNotNull();
        assertThat(session.getDependencySelector()).isNotNull();
        assertThat(session.getDependencyGraphTransformer()).isNotNull();
        assertThat(session.getArtifactDescriptorPolicy()).isNotNull();
        assertThat(session.getArtifactTypeRegistry().get("pom")).isNotNull();
        assertThat(session.getArtifactTypeRegistry().get("jar").getExtension()).isEqualTo("jar");
        assertThat(session.getArtifactTypeRegistry().get("test-jar").getClassifier()).isEqualTo("tests");
        assertThat(session.getArtifactTypeRegistry().get("war").getExtension()).isEqualTo("war");
    }

    @Test
    void serviceLocatorRegistersResolverProviderServices() {
        DefaultServiceLocator serviceLocator = MavenRepositorySystemUtils.newServiceLocator();

        List<MetadataGeneratorFactory> factories = serviceLocator.getServices(MetadataGeneratorFactory.class);

        assertThat(factories)
                .extracting(factory -> factory.getClass().getName())
                .contains(
                        "org.apache.maven.repository.internal.SnapshotMetadataGeneratorFactory",
                        "org.apache.maven.repository.internal.VersionsMetadataGeneratorFactory");
        assertThat(serviceLocator.getService(org.apache.maven.repository.internal.ModelCacheFactory.class))
                .isInstanceOf(DefaultModelCacheFactory.class);
    }

    @Test
    void convertsArtifactsAndMavenRepositoriesToResolverRepresentations() {
        Artifact jarArtifact = new DefaultArtifact("org.example", "demo", "tests", "jar", "1.2.3");
        Artifact pomArtifact = ArtifactDescriptorUtils.toPomArtifact(jarArtifact);
        Artifact alreadyPomArtifact = new DefaultArtifact("org.example", "demo", "pom", "1.2.3");

        Repository repository = new Repository();
        repository.setId("internal");
        repository.setLayout("default");
        repository.setUrl("https://repo.example.test/maven2");
        repository.setReleases(mavenRepositoryPolicy(false, "never", "fail"));
        repository.setSnapshots(mavenRepositoryPolicy(true, "always", "warn"));

        RemoteRepository remoteRepository = ArtifactDescriptorUtils.toRemoteRepository(repository);
        RepositoryPolicy defaultPolicy = ArtifactDescriptorUtils.toRepositoryPolicy(null);

        assertThat(pomArtifact.getGroupId()).isEqualTo("org.example");
        assertThat(pomArtifact.getArtifactId()).isEqualTo("demo");
        assertThat(pomArtifact.getClassifier()).isEmpty();
        assertThat(pomArtifact.getExtension()).isEqualTo("pom");
        assertThat(pomArtifact.getVersion()).isEqualTo("1.2.3");
        assertThat(ArtifactDescriptorUtils.toPomArtifact(alreadyPomArtifact)).isSameAs(alreadyPomArtifact);
        assertThat(remoteRepository.getId()).isEqualTo("internal");
        assertThat(remoteRepository.getContentType()).isEqualTo("default");
        assertThat(remoteRepository.getUrl()).isEqualTo("https://repo.example.test/maven2");
        assertThat(remoteRepository.getPolicy(false).isEnabled()).isFalse();
        assertThat(remoteRepository.getPolicy(false).getUpdatePolicy()).isEqualTo("never");
        assertThat(remoteRepository.getPolicy(false).getChecksumPolicy()).isEqualTo("fail");
        assertThat(remoteRepository.getPolicy(true).isEnabled()).isTrue();
        assertThat(remoteRepository.getPolicy(true).getUpdatePolicy()).isEqualTo("always");
        assertThat(defaultPolicy.isEnabled()).isTrue();
        assertThat(defaultPolicy.getUpdatePolicy()).isEqualTo("daily");
        assertThat(defaultPolicy.getChecksumPolicy()).isEqualTo("warn");
    }

    @Test
    void artifactDescriptorDelegatePopulatesDependenciesRepositoriesAndDescriptorProperties() {
        ArtifactDescriptorRequest request = new ArtifactDescriptorRequest();
        request.setArtifact(new DefaultArtifact("org.example", "root", "jar", "1.0.0"));
        ArtifactDescriptorResult result = new ArtifactDescriptorResult(request);
        result.setArtifact(request.getArtifact());

        Model model = new Model();
        model.addRepository(repository("central", "https://repo.example.test/repository"));
        model.addDependency(dependency("org.example", "runtime-lib", "1.1.0", "runtime", true));
        DependencyManagement dependencyManagement = new DependencyManagement();
        dependencyManagement.addDependency(dependency("org.example", "managed-lib", "2.0.0", "compile", false));
        model.setDependencyManagement(dependencyManagement);
        model.setPrerequisites(prerequisites("3.9.0"));
        model.addLicense(license("Apache-2.0", "https://www.apache.org/licenses/LICENSE-2.0.txt"));
        DistributionManagement distributionManagement = new DistributionManagement();
        distributionManagement.setDownloadUrl("https://downloads.example.test/root-1.0.0.jar");
        model.setDistributionManagement(distributionManagement);

        new ArtifactDescriptorReaderDelegate().populateResult(MavenRepositorySystemUtils.newSession(), result, model);

        assertThat(result.getRepositories()).hasSize(1);
        assertThat(result.getRepositories().get(0).getId()).isEqualTo("central");
        assertThat(result.getDependencies()).hasSize(1);
        Dependency dependency = result.getDependencies().get(0);
        assertThat(dependency.getArtifact().getGroupId()).isEqualTo("org.example");
        assertThat(dependency.getArtifact().getArtifactId()).isEqualTo("runtime-lib");
        assertThat(dependency.getArtifact().getVersion()).isEqualTo("1.1.0");
        assertThat(dependency.getScope()).isEqualTo("runtime");
        assertThat(dependency.getOptional()).isTrue();
        assertThat(dependency.getExclusions()).singleElement().satisfies(exclusion -> {
            assertThat(exclusion.getGroupId()).isEqualTo("org.excluded");
            assertThat(exclusion.getArtifactId()).isEqualTo("excluded-lib");
            assertThat(exclusion.getExtension()).isEqualTo("*");
            assertThat(exclusion.getClassifier()).isEqualTo("*");
        });
        assertThat(result.getManagedDependencies()).hasSize(1);
        assertThat(result.getManagedDependencies().get(0).getArtifact().getArtifactId()).isEqualTo("managed-lib");
        assertThat(result.getProperties()).containsEntry("prerequisites.maven", "3.9.0");
        assertThat(result.getProperties()).containsEntry("license.count", 1);
        assertThat(result.getProperties()).containsEntry("license.0.name", "Apache-2.0");
        assertThat(result.getArtifact().getProperty("downloadUrl", null))
                .isEqualTo("https://downloads.example.test/root-1.0.0.jar");
    }

    @Test
    void artifactDescriptorDelegatePreservesSystemDependencyLocalPathAndArtifactType() {
        ArtifactDescriptorRequest request = new ArtifactDescriptorRequest();
        request.setArtifact(new DefaultArtifact("org.example", "system-root", "jar", "1.0.0"));
        ArtifactDescriptorResult result = new ArtifactDescriptorResult(request);
        result.setArtifact(request.getArtifact());
        Path localJar = temporaryDirectory.resolve("local-tests.jar").toAbsolutePath();

        org.apache.maven.model.Dependency systemDependency = new org.apache.maven.model.Dependency();
        systemDependency.setGroupId("org.example");
        systemDependency.setArtifactId("system-tests");
        systemDependency.setVersion("1.0.0");
        systemDependency.setType("test-jar");
        systemDependency.setScope("system");
        systemDependency.setSystemPath(localJar.toString());
        Model model = new Model();
        model.addDependency(systemDependency);

        new ArtifactDescriptorReaderDelegate().populateResult(MavenRepositorySystemUtils.newSession(), result, model);

        assertThat(result.getDependencies()).singleElement().satisfies(dependency -> {
            assertThat(dependency.getScope()).isEqualTo("system");
            assertThat(dependency.getArtifact().getGroupId()).isEqualTo("org.example");
            assertThat(dependency.getArtifact().getArtifactId()).isEqualTo("system-tests");
            assertThat(dependency.getArtifact().getVersion()).isEqualTo("1.0.0");
            assertThat(dependency.getArtifact().getClassifier()).isEqualTo("tests");
            assertThat(dependency.getArtifact().getExtension()).isEqualTo("jar");
            assertThat(dependency.getArtifact().getProperty(ArtifactProperties.LOCAL_PATH, null))
                    .isEqualTo(localJar.toString());
        });
    }

    @Test
    void modelCacheSeparatesEntriesByCoordinatesAndTag() {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        session.setCache(new InMemoryRepositoryCache());
        ModelCache cache = new DefaultModelCacheFactory().createCache(session);
        Model firstModel = new Model();
        Model secondModel = new Model();

        cache.put("org.example", "demo", "1.0.0", "raw", firstModel);
        cache.put("org.example", "demo", "1.0.0", "effective", secondModel);

        assertThat(cache.get("org.example", "demo", "1.0.0", "raw")).isSameAs(firstModel);
        assertThat(cache.get("org.example", "demo", "1.0.0", "effective")).isSameAs(secondModel);
        assertThat(cache.get("org.example", "demo", "2.0.0", "raw")).isNull();
        assertThat(cache.get("org.example", "other", "1.0.0", "raw")).isNull();
    }

    private static final class InMemoryRepositoryCache implements RepositoryCache {
        private final Map<Object, Object> entries = new ConcurrentHashMap<>();

        @Override
        public void put(RepositorySystemSession session, Object key, Object data) {
            entries.put(key, data);
        }

        @Override
        public Object get(RepositorySystemSession session, Object key) {
            return entries.get(key);
        }
    }

    @Test
    void versionResolversHandleFixedVersionsWithoutMetadataResolution() throws Exception {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        Artifact artifact = new DefaultArtifact("org.example", "demo", "jar", "1.2.3");

        VersionResult versionResult = new DefaultVersionResolver()
                .resolveVersion(session, new VersionRequest().setArtifact(artifact));
        VersionRangeResult rangeResult = new DefaultVersionRangeResolver()
                .resolveVersionRange(session, new VersionRangeRequest().setArtifact(artifact));

        assertThat(versionResult.getVersion()).isEqualTo("1.2.3");
        assertThat(rangeResult.getVersionConstraint().getVersion().toString()).isEqualTo("1.2.3");
        assertThat(rangeResult.getVersions()).extracting(Object::toString).containsExactly("1.2.3");
        assertThat(rangeResult.getLowestVersion().toString()).isEqualTo("1.2.3");
        assertThat(rangeResult.getHighestVersion().toString()).isEqualTo("1.2.3");
    }

    @Test
    void metadataGeneratorsCreateSnapshotVersionAndPluginMetadata() throws Exception {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        Artifact snapshotArtifact = new DefaultArtifact("org.example", "demo", "jar", "1.0.0-SNAPSHOT");
        Artifact releaseArtifact = new DefaultArtifact("org.example", "demo", "jar", "1.0.0");
        Artifact pluginArtifact = new DefaultArtifact("org.example.plugins", "demo-maven-plugin", "jar", "1.0.0")
                .setFile(createPluginJar().toFile());

        SnapshotMetadataGeneratorFactory snapshotFactory = new SnapshotMetadataGeneratorFactory();
        VersionsMetadataGeneratorFactory versionsFactory = new VersionsMetadataGeneratorFactory();
        PluginsMetadataGeneratorFactory pluginsFactory = new PluginsMetadataGeneratorFactory();
        MetadataGenerator snapshotGenerator = snapshotFactory.newInstance(session, new InstallRequest());
        MetadataGenerator versionsGenerator = versionsFactory.newInstance(session, new InstallRequest());
        MetadataGenerator pluginsGenerator = pluginsFactory.newInstance(session, new InstallRequest());

        assertThat(snapshotFactory.getPriority()).isEqualTo(30.0f);
        assertThat(versionsFactory.getPriority()).isEqualTo(20.0f);
        assertThat(pluginsFactory.getPriority()).isEqualTo(10.0f);
        assertThat(snapshotGenerator.prepare(List.of(snapshotArtifact, releaseArtifact))).isEmpty();
        assertThat(snapshotGenerator.transformArtifact(snapshotArtifact)).isSameAs(snapshotArtifact);

        Metadata snapshotMetadata = singleMetadata(
                snapshotGenerator.finish(List.of(snapshotArtifact, releaseArtifact)));
        Metadata versionsMetadata = singleMetadata(versionsGenerator.finish(List.of(releaseArtifact)));
        Metadata pluginMetadata = singleMetadata(pluginsGenerator.finish(List.of(pluginArtifact)));

        assertThat(snapshotMetadata.getGroupId()).isEqualTo("org.example");
        assertThat(snapshotMetadata.getArtifactId()).isEqualTo("demo");
        assertThat(snapshotMetadata.getVersion()).isEqualTo("1.0.0-SNAPSHOT");
        assertThat(snapshotMetadata.getType()).isEqualTo("maven-metadata.xml");
        assertThat(snapshotMetadata.getNature()).isEqualTo(Metadata.Nature.SNAPSHOT);
        assertThat(versionsMetadata.getGroupId()).isEqualTo("org.example");
        assertThat(versionsMetadata.getArtifactId()).isEqualTo("demo");
        assertThat(versionsMetadata.getVersion()).isEmpty();
        assertThat(versionsMetadata.getNature()).isEqualTo(Metadata.Nature.RELEASE);
        assertThat(pluginMetadata.getGroupId()).isEqualTo("org.example.plugins");
        assertThat(pluginMetadata.getArtifactId()).isEmpty();
        assertThat(pluginMetadata.getType()).isEqualTo("maven-metadata.xml");
        assertThat(pluginMetadata.getNature()).isEqualTo(Metadata.Nature.RELEASE_OR_SNAPSHOT);
    }

    @Test
    void remoteSnapshotDeploymentMetadataExpandsSnapshotVersions() throws Exception {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        session.setConfigProperty("maven.startTime", new Date(0L));
        session.setConfigProperty("maven.buildNumber", 7);
        Artifact mainArtifact = new DefaultArtifact("org.example", "demo", "jar", "1.0.0-SNAPSHOT");
        Artifact sourcesArtifact = new DefaultArtifact("org.example", "demo", "sources", "jar", "1.0.0-SNAPSHOT");
        String expandedVersion = mainArtifact.getBaseVersion().replace("SNAPSHOT", "19700101.000000-7");
        MetadataGenerator generator = new SnapshotMetadataGeneratorFactory().newInstance(session, new DeployRequest());

        Metadata metadata = singleMetadata(generator.prepare(List.of(mainArtifact, sourcesArtifact)));
        assertThat(generator.finish(List.of(mainArtifact, sourcesArtifact))).isEmpty();
        MergeableMetadata mergeableMetadata = (MergeableMetadata) metadata;
        Path currentMetadata = temporaryDirectory.resolve("empty-maven-metadata.xml");
        Path generatedMetadata = temporaryDirectory.resolve("generated/maven-metadata.xml");
        mergeableMetadata.merge(currentMetadata.toFile(), generatedMetadata.toFile());

        Artifact transformedMainArtifact = generator.transformArtifact(mainArtifact);
        Artifact transformedSourcesArtifact = generator.transformArtifact(sourcesArtifact);
        String generatedXml = Files.readString(generatedMetadata, StandardCharsets.UTF_8);

        assertThat(metadata.getGroupId()).isEqualTo("org.example");
        assertThat(metadata.getArtifactId()).isEqualTo("demo");
        assertThat(metadata.getVersion()).isEqualTo(mainArtifact.getBaseVersion());
        assertThat(metadata.getNature()).isEqualTo(Metadata.Nature.SNAPSHOT);
        assertThat(mergeableMetadata.isMerged()).isTrue();
        assertThat(transformedMainArtifact.getVersion()).isEqualTo(expandedVersion);
        assertThat(transformedSourcesArtifact.getVersion()).isEqualTo(expandedVersion);
        assertThat(generatedXml)
                .contains("<timestamp>19700101.000000</timestamp>")
                .contains("<buildNumber>7</buildNumber>")
                .contains("<extension>jar</extension>")
                .contains("<classifier>sources</classifier>")
                .contains("<value>" + expandedVersion + "</value>");
    }

    @Test
    void requestTraceHelperExplainsKnownTraceObjectsAndCollectionPaths() {
        Artifact artifact = new DefaultArtifact("org.example", "demo", "jar", "1.0.0");
        ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest().setArtifact(artifact);
        Plugin plugin = new Plugin();
        plugin.setGroupId("org.example.plugins");
        plugin.setArtifactId("demo-maven-plugin");
        plugin.setVersion("1.0.0");
        Dependency rootDependency = new Dependency(
                new DefaultArtifact("org.example", "root", "jar", "1.0.0"), "compile");
        Dependency offendingDependency = new Dependency(artifact, "runtime");
        CollectStepData stepData = new CollectStepData() {
            @Override
            public String getContext() {
                return "runtime";
            }

            @Override
            public List<DependencyNode> getPath() {
                return List.of(new DefaultDependencyNode(rootDependency));
            }

            @Override
            public Dependency getNode() {
                return offendingDependency;
            }
        };

        assertThat(RequestTraceHelper.interpretTrace(false, new RequestTrace(descriptorRequest)))
                .contains("artifact descriptor request for")
                .contains("org.example:demo:jar:1.0.0");
        assertThat(RequestTraceHelper.interpretTrace(false, new RequestTrace(plugin)))
                .contains("plugin request")
                .contains("org.example.plugins:demo-maven-plugin:1.0.0");
        assertThat(RequestTraceHelper.interpretTrace(true, new RequestTrace(stepData)))
                .contains("dependency collection step for")
                .contains("runtime")
                .contains("Path to offending node from root")
                .contains("org.example:root:jar:1.0.0")
                .contains("org.example:demo:jar:1.0.0");
        RequestTrace traceWithUnknownChild = RequestTrace.newChild(new RequestTrace(descriptorRequest), "unknown");
        assertThat(RequestTraceHelper.interpretTrace(false, traceWithUnknownChild))
                .contains("artifact descriptor request for")
                .contains("org.example:demo:jar:1.0.0");
        assertThat(RequestTraceHelper.interpretTrace(false, null)).isEqualTo("n/a");
    }

    private static org.apache.maven.model.RepositoryPolicy mavenRepositoryPolicy(
            boolean enabled, String updatePolicy, String checksumPolicy) {
        org.apache.maven.model.RepositoryPolicy policy = new org.apache.maven.model.RepositoryPolicy();
        policy.setEnabled(enabled);
        policy.setUpdatePolicy(updatePolicy);
        policy.setChecksumPolicy(checksumPolicy);
        return policy;
    }

    private static Repository repository(String id, String url) {
        Repository repository = new Repository();
        repository.setId(id);
        repository.setLayout("default");
        repository.setUrl(url);
        repository.setReleases(mavenRepositoryPolicy(true, "daily", "warn"));
        repository.setSnapshots(mavenRepositoryPolicy(false, "never", "fail"));
        return repository;
    }

    private static org.apache.maven.model.Dependency dependency(
            String groupId, String artifactId, String version, String scope, boolean optional) {
        org.apache.maven.model.Dependency dependency = new org.apache.maven.model.Dependency();
        dependency.setGroupId(groupId);
        dependency.setArtifactId(artifactId);
        dependency.setVersion(version);
        dependency.setType("jar");
        dependency.setScope(scope);
        dependency.setOptional(optional);
        Exclusion exclusion = new Exclusion();
        exclusion.setGroupId("org.excluded");
        exclusion.setArtifactId("excluded-lib");
        dependency.addExclusion(exclusion);
        return dependency;
    }

    private static Prerequisites prerequisites(String mavenVersion) {
        Prerequisites prerequisites = new Prerequisites();
        prerequisites.setMaven(mavenVersion);
        return prerequisites;
    }

    private static License license(String name, String url) {
        License license = new License();
        license.setName(name);
        license.setUrl(url);
        license.setComments("permissive license");
        license.setDistribution("repo");
        return license;
    }

    private static Metadata singleMetadata(Iterable<? extends Metadata> metadata) {
        assertThat(metadata).hasSize(1);
        return metadata.iterator().next();
    }

    private Path createPluginJar() throws IOException {
        Path jar = temporaryDirectory.resolve("demo-maven-plugin.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry("META-INF/maven/plugin.xml"));
            output.write("""
                    <plugin>
                      <groupId>org.example.plugins</groupId>
                      <artifactId>demo-maven-plugin</artifactId>
                      <goalPrefix>demo</goalPrefix>
                      <name>Demo Plugin</name>
                    </plugin>
                    """.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return jar;
    }
}
