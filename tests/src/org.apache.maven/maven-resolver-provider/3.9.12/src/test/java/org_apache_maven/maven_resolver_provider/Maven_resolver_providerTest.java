/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_resolver_provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.maven.model.Model;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.maven.repository.internal.MavenWorkspaceReader;
import org.apache.maven.repository.internal.RelocatedArtifact;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.repository.WorkspaceRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Maven_resolver_providerTest {
    @TempDir
    Path tempDirectory;

    @Test
    void serviceLocatorResolvesMavenArtifactDescriptorFromHermeticFileRepository() throws Exception {
        Path remoteRepositoryDirectory = tempDirectory.resolve("remote-descriptor-repository");
        Path localRepositoryDirectory = tempDirectory.resolve("local-descriptor-repository");
        String remoteRepositoryUrl = remoteRepositoryDirectory.toUri().toString();
        writePom(remoteRepositoryDirectory, "com.acme", "demo", "1.0.0", """
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.acme</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <name>Fixture descriptor</name>
                  <properties>
                    <runtime.version>1.1.0</runtime.version>
                  </properties>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.acme</groupId>
                        <artifactId>managed-lib</artifactId>
                        <version>2.0.0</version>
                        <scope>compile</scope>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>com.acme</groupId>
                      <artifactId>runtime-lib</artifactId>
                      <version>${runtime.version}</version>
                      <scope>runtime</scope>
                      <optional>true</optional>
                    </dependency>
                  </dependencies>
                  <repositories>
                    <repository>
                      <id>fixture-child</id>
                      <url>%s</url>
                    </repository>
                  </repositories>
                </project>
                """.formatted(remoteRepositoryUrl));

        RepositorySystem repositorySystem = newRepositorySystem();
        DefaultRepositorySystemSession session = newSession(repositorySystem, localRepositoryDirectory);
        RemoteRepository remoteRepository = repository("fixture", remoteRepositoryUrl);
        Artifact artifact = new DefaultArtifact("com.acme:demo:jar:1.0.0");
        ArtifactDescriptorRequest request = new ArtifactDescriptorRequest(
                artifact, List.of(remoteRepository), "project");

        ArtifactDescriptorResult result = repositorySystem.readArtifactDescriptor(session, request);

        assertThat(result.getArtifact().getGroupId()).isEqualTo("com.acme");
        assertThat(result.getArtifact().getArtifactId()).isEqualTo("demo");
        assertThat(result.getArtifact().getVersion()).isEqualTo("1.0.0");
        assertThat(result.getDependencies())
                .extracting(Dependency::getArtifact)
                .extracting(Artifact::toString)
                .containsExactly("com.acme:runtime-lib:jar:1.1.0");
        assertThat(result.getDependencies())
                .singleElement()
                .satisfies(dependency -> {
                    assertThat(dependency.getScope()).isEqualTo("runtime");
                    assertThat(dependency.isOptional()).isTrue();
                });
        assertThat(result.getManagedDependencies())
                .extracting(Dependency::getArtifact)
                .extracting(Artifact::toString)
                .containsExactly("com.acme:managed-lib:jar:2.0.0");
        assertThat(result.getRepositories()).extracting(RemoteRepository::getId).contains("fixture-child");
        assertThat(localRepositoryDirectory.resolve("com/acme/demo/1.0.0/demo-1.0.0.pom")).exists();
    }

    @Test
    void artifactDescriptorFollowsMavenRelocationToTargetCoordinates() throws Exception {
        Path remoteRepositoryDirectory = tempDirectory.resolve("remote-relocation-repository");
        Path localRepositoryDirectory = tempDirectory.resolve("local-relocation-repository");
        writePom(remoteRepositoryDirectory, "com.acme", "old-demo", "1.0.0", """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.acme</groupId>
                  <artifactId>old-demo</artifactId>
                  <version>1.0.0</version>
                  <distributionManagement>
                    <relocation>
                      <groupId>com.acme.relocated</groupId>
                      <artifactId>new-demo</artifactId>
                      <version>2.0.0</version>
                      <message>Use the relocated artifact</message>
                    </relocation>
                  </distributionManagement>
                </project>
                """);
        writePom(remoteRepositoryDirectory, "com.acme.relocated", "new-demo", "2.0.0", """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.acme.relocated</groupId>
                  <artifactId>new-demo</artifactId>
                  <version>2.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.acme.relocated</groupId>
                      <artifactId>relocated-dependency</artifactId>
                      <version>2.1.0</version>
                      <scope>compile</scope>
                    </dependency>
                  </dependencies>
                </project>
                """);

        RepositorySystem repositorySystem = newRepositorySystem();
        DefaultRepositorySystemSession session = newSession(repositorySystem, localRepositoryDirectory);
        RemoteRepository remoteRepository = repository("fixture", remoteRepositoryDirectory.toUri().toString());
        Artifact artifact = new DefaultArtifact("com.acme:old-demo:jar:1.0.0");
        ArtifactDescriptorRequest request = new ArtifactDescriptorRequest(
                artifact, List.of(remoteRepository), "relocation-test");

        ArtifactDescriptorResult result = repositorySystem.readArtifactDescriptor(session, request);

        assertThat(result.getRelocations())
                .extracting(Artifact::toString)
                .containsExactly("com.acme:old-demo:jar:1.0.0");
        assertThat(result.getArtifact()).isInstanceOf(RelocatedArtifact.class);
        RelocatedArtifact relocatedArtifact = (RelocatedArtifact) result.getArtifact();
        assertThat(relocatedArtifact.getGroupId()).isEqualTo("com.acme.relocated");
        assertThat(relocatedArtifact.getArtifactId()).isEqualTo("new-demo");
        assertThat(relocatedArtifact.getVersion()).isEqualTo("2.0.0");
        assertThat(relocatedArtifact.getMessage()).isEqualTo("Use the relocated artifact");
        assertThat(result.getDependencies())
                .extracting(Dependency::getArtifact)
                .extracting(Artifact::toString)
                .containsExactly("com.acme.relocated:relocated-dependency:jar:2.1.0");
        assertThat(localRepositoryDirectory.resolve("com/acme/old-demo/1.0.0/old-demo-1.0.0.pom")).exists();
        assertThat(localRepositoryDirectory.resolve("com/acme/relocated/new-demo/2.0.0/new-demo-2.0.0.pom"))
                .exists();
    }

    @Test
    void versionRangeResolutionReadsMavenMetadataFromFileRepository() throws Exception {
        Path remoteRepositoryDirectory = tempDirectory.resolve("remote-version-repository");
        Path localRepositoryDirectory = tempDirectory.resolve("local-version-repository");
        writeMetadata(remoteRepositoryDirectory, "com.acme", "demo", """
                <metadata>
                  <groupId>com.acme</groupId>
                  <artifactId>demo</artifactId>
                  <versioning>
                    <latest>2.1.0</latest>
                    <release>2.1.0</release>
                    <versions>
                      <version>0.9.0</version>
                      <version>1.0.0</version>
                      <version>1.1.0</version>
                      <version>2.1.0</version>
                    </versions>
                    <lastUpdated>20240628000000</lastUpdated>
                  </versioning>
                </metadata>
                """);

        RepositorySystem repositorySystem = newRepositorySystem();
        DefaultRepositorySystemSession session = newSession(repositorySystem, localRepositoryDirectory);
        RemoteRepository remoteRepository = repository("fixture", remoteRepositoryDirectory.toUri().toString());
        Artifact rangeArtifact = new DefaultArtifact("com.acme:demo:jar:[1.0.0,2.0.0)");
        VersionRangeRequest request = new VersionRangeRequest(rangeArtifact, List.of(remoteRepository), "range-test");

        VersionRangeResult result = repositorySystem.resolveVersionRange(session, request);

        assertThat(result.getVersions()).extracting(Object::toString).containsExactly("1.0.0", "1.1.0");
        assertThat(result.getRepository(result.getVersions().get(0))).isEqualTo(remoteRepository);
        assertThat(localRepositoryDirectory.resolve("com/acme/demo/maven-metadata-fixture.xml")).exists();
    }

    @Test
    void dependencyCollectionBuildsTransitiveGraphAndHonorsMavenExclusions() throws Exception {
        Path remoteRepositoryDirectory = tempDirectory.resolve("remote-collection-repository");
        Path localRepositoryDirectory = tempDirectory.resolve("local-collection-repository");
        writePom(remoteRepositoryDirectory, "com.acme", "app", "1.0.0", """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.acme</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.acme</groupId>
                      <artifactId>direct-lib</artifactId>
                      <version>1.0.0</version>
                      <scope>compile</scope>
                      <exclusions>
                        <exclusion>
                          <groupId>com.acme</groupId>
                          <artifactId>excluded-lib</artifactId>
                        </exclusion>
                      </exclusions>
                    </dependency>
                  </dependencies>
                </project>
                """);
        writePom(remoteRepositoryDirectory, "com.acme", "direct-lib", "1.0.0", """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.acme</groupId>
                  <artifactId>direct-lib</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.acme</groupId>
                      <artifactId>transitive-lib</artifactId>
                      <version>1.0.0</version>
                      <scope>runtime</scope>
                    </dependency>
                    <dependency>
                      <groupId>com.acme</groupId>
                      <artifactId>excluded-lib</artifactId>
                      <version>1.0.0</version>
                      <scope>compile</scope>
                    </dependency>
                  </dependencies>
                </project>
                """);
        writePom(remoteRepositoryDirectory, "com.acme", "transitive-lib", "1.0.0", """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.acme</groupId>
                  <artifactId>transitive-lib</artifactId>
                  <version>1.0.0</version>
                </project>
                """);
        writePom(remoteRepositoryDirectory, "com.acme", "excluded-lib", "1.0.0", """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.acme</groupId>
                  <artifactId>excluded-lib</artifactId>
                  <version>1.0.0</version>
                </project>
                """);

        RepositorySystem repositorySystem = newRepositorySystem();
        DefaultRepositorySystemSession session = newSession(repositorySystem, localRepositoryDirectory);
        RemoteRepository remoteRepository = repository("fixture", remoteRepositoryDirectory.toUri().toString());
        Dependency rootDependency = new Dependency(new DefaultArtifact("com.acme:app:jar:1.0.0"), "compile");
        CollectRequest request = new CollectRequest();
        request.setRoot(rootDependency);
        request.setRepositories(List.of(remoteRepository));

        CollectResult result = repositorySystem.collectDependencies(session, request);

        DependencyNode root = result.getRoot();
        assertThat(root.getDependency().getArtifact().toString()).isEqualTo("com.acme:app:jar:1.0.0");
        assertThat(root.getChildren())
                .extracting(node -> node.getDependency().getArtifact().toString())
                .containsExactly("com.acme:direct-lib:jar:1.0.0");
        DependencyNode directDependency = root.getChildren().get(0);
        assertThat(directDependency.getDependency().getScope()).isEqualTo("compile");
        assertThat(directDependency.getChildren())
                .extracting(node -> node.getDependency().getArtifact().toString())
                .containsExactly("com.acme:transitive-lib:jar:1.0.0");
        assertThat(directDependency.getChildren()).singleElement().satisfies(node -> {
            assertThat(node.getDependency().getScope()).isEqualTo("runtime");
            assertThat(node.getChildren()).isEmpty();
        });
        assertThat(localRepositoryDirectory.resolve("com/acme/excluded-lib/1.0.0/excluded-lib-1.0.0.pom"))
                .doesNotExist();
    }

    @Test
    void artifactResolutionDownloadsUsingMavenLayoutAndCachesInLocalRepository() throws Exception {
        Path remoteRepositoryDirectory = tempDirectory.resolve("remote-artifact-repository");
        Path localRepositoryDirectory = tempDirectory.resolve("local-artifact-repository");
        writeArtifact(remoteRepositoryDirectory, "com.acme", "demo", "1.0.0", "jar", "fixture jar content");
        writePom(remoteRepositoryDirectory, "com.acme", "demo", "1.0.0", """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.acme</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                </project>
                """);

        RepositorySystem repositorySystem = newRepositorySystem();
        DefaultRepositorySystemSession session = newSession(repositorySystem, localRepositoryDirectory);
        RemoteRepository remoteRepository = repository("fixture", remoteRepositoryDirectory.toUri().toString());
        Artifact artifact = new DefaultArtifact("com.acme:demo:jar:1.0.0");
        ArtifactRequest request = new ArtifactRequest(artifact, List.of(remoteRepository), "artifact-test");

        ArtifactResult result = repositorySystem.resolveArtifact(session, request);

        assertThat(result.isResolved()).isTrue();
        assertThat(result.getRepository()).isEqualTo(remoteRepository);
        assertThat(result.getArtifact().getFile()).isFile();
        assertThat(Files.readString(result.getArtifact().getFile().toPath(), StandardCharsets.UTF_8))
                .isEqualTo("fixture jar content");
        String localPath = session.getLocalRepositoryManager().getPathForLocalArtifact(artifact);
        assertThat(localPath).isEqualTo("com/acme/demo/1.0.0/demo-1.0.0.jar");
        assertThat(localRepositoryDirectory.resolve(localPath)).exists();
    }

    @Test
    void mavenWorkspaceReaderExposesWorkspaceRepositoryModelsAndVersions() {
        Model workspaceModel = model("com.acme", "workspace-demo", "1.0.0");
        RecordingWorkspaceReader workspaceReader = new RecordingWorkspaceReader(workspaceModel);
        Artifact matchingArtifact = new DefaultArtifact("com.acme:workspace-demo:pom:1.0.0");
        Artifact differentArtifact = new DefaultArtifact("com.acme:other:pom:1.0.0");

        assertThat(workspaceReader.getRepository().getContentType()).isEqualTo("fixture-workspace");
        assertThat(workspaceReader.getRepository().getId()).isEqualTo("workspace");
        assertThat(workspaceReader.findModel(matchingArtifact)).isSameAs(workspaceModel);
        assertThat(workspaceReader.findVersions(matchingArtifact)).containsExactly("1.0.0");
        assertThat(workspaceReader.findArtifact(matchingArtifact)).isNull();
        assertThat(workspaceReader.findModel(differentArtifact)).isNull();
        assertThat(workspaceReader.findVersions(differentArtifact)).isEmpty();
        assertThat(workspaceReader.findModelCalls().get()).isEqualTo(2);
    }

    private static RepositorySystem newRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        RepositorySystem repositorySystem = locator.getService(RepositorySystem.class);
        assertThat(repositorySystem).isNotNull();
        return repositorySystem;
    }

    private static DefaultRepositorySystemSession newSession(
            RepositorySystem repositorySystem, Path localRepositoryDirectory) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        session.setChecksumPolicy(RepositoryPolicy.CHECKSUM_POLICY_IGNORE);
        session.setUpdatePolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
        LocalRepository localRepository = new LocalRepository(localRepositoryDirectory.toFile());
        session.setLocalRepositoryManager(repositorySystem.newLocalRepositoryManager(session, localRepository));
        return session;
    }

    private static RemoteRepository repository(String id, String url) {
        return new RemoteRepository.Builder(id, "default", url).build();
    }

    private static void writePom(
            Path repositoryDirectory,
            String groupId,
            String artifactId,
            String version,
            String content) throws Exception {
        Path artifactDirectory = artifactDirectory(repositoryDirectory, groupId, artifactId, version);
        Files.createDirectories(artifactDirectory);
        Path pom = artifactDirectory.resolve(artifactId + "-" + version + ".pom");
        Files.writeString(pom, content, StandardCharsets.UTF_8);
    }

    private static void writeArtifact(
            Path repositoryDirectory,
            String groupId,
            String artifactId,
            String version,
            String extension,
            String content) throws Exception {
        Path artifactDirectory = artifactDirectory(repositoryDirectory, groupId, artifactId, version);
        Files.createDirectories(artifactDirectory);
        Files.writeString(
                artifactDirectory.resolve(artifactId + "-" + version + "." + extension),
                content,
                StandardCharsets.UTF_8);
    }

    private static void writeMetadata(Path repositoryDirectory, String groupId, String artifactId, String content)
            throws Exception {
        Path metadataDirectory = repositoryDirectory
                .resolve(groupId.replace('.', File.separatorChar))
                .resolve(artifactId);
        Files.createDirectories(metadataDirectory);
        Files.writeString(metadataDirectory.resolve("maven-metadata.xml"), content, StandardCharsets.UTF_8);
    }

    private static Path artifactDirectory(Path repositoryDirectory, String groupId, String artifactId, String version) {
        return repositoryDirectory
                .resolve(groupId.replace('.', File.separatorChar))
                .resolve(artifactId)
                .resolve(version);
    }

    private static Model model(String groupId, String artifactId, String version) {
        Model model = new Model();
        model.setModelVersion("4.0.0");
        model.setGroupId(groupId);
        model.setArtifactId(artifactId);
        model.setVersion(version);
        return model;
    }

    private static final class RecordingWorkspaceReader implements MavenWorkspaceReader {
        private final Model model;
        private final AtomicInteger findModelCalls = new AtomicInteger();
        private final WorkspaceRepository repository = new WorkspaceRepository("fixture-workspace");

        private RecordingWorkspaceReader(Model model) {
            this.model = model;
        }

        AtomicInteger findModelCalls() {
            return findModelCalls;
        }

        @Override
        public WorkspaceRepository getRepository() {
            return repository;
        }

        @Override
        public File findArtifact(Artifact artifact) {
            return null;
        }

        @Override
        public List<String> findVersions(Artifact artifact) {
            if (matches(artifact)) {
                return List.of(model.getVersion());
            }
            return List.of();
        }

        @Override
        public Model findModel(Artifact artifact) {
            findModelCalls.incrementAndGet();
            if (matches(artifact)) {
                return model;
            }
            return null;
        }

        private boolean matches(Artifact artifact) {
            return model.getGroupId().equals(artifact.getGroupId())
                    && model.getArtifactId().equals(artifact.getArtifactId())
                    && model.getVersion().equals(artifact.getBaseVersion());
        }
    }
}
