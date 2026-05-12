/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_aether_provider;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.model.Profile;
import org.apache.maven.model.Repository;
import org.apache.maven.model.RepositoryPolicy;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingResult;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.repository.internal.DefaultArtifactDescriptorReader;
import org.apache.maven.repository.internal.MavenRepositorySystemSession;
import org.junit.jupiter.api.Test;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.impl.ArtifactResolver;
import org.sonatype.aether.impl.RemoteRepositoryManager;
import org.sonatype.aether.impl.VersionResolver;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.resolution.ArtifactDescriptorRequest;
import org.sonatype.aether.resolution.ArtifactDescriptorResult;
import org.sonatype.aether.resolution.ArtifactRequest;
import org.sonatype.aether.resolution.ArtifactResolutionException;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.resolution.VersionRequest;
import org.sonatype.aether.resolution.VersionResolutionException;
import org.sonatype.aether.resolution.VersionResult;
import org.sonatype.aether.spi.connector.RepositoryConnector;
import org.sonatype.aether.transfer.NoRepositoryConnectorException;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.sonatype.aether.util.artifact.DefaultArtifactType;
import org.sonatype.aether.util.artifact.DefaultArtifactTypeRegistry;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultArtifactDescriptorReaderTest {
    @Test
    void artifactDescriptorReaderConvertsEffectiveModelIntoAetherDescriptor() throws Exception {
        Model effectiveModel = effectiveModel();
        RecordingModelBuilder modelBuilder = new RecordingModelBuilder(effectiveModel);
        RecordingVersionResolver versionResolver = new RecordingVersionResolver("1.0");
        RecordingArtifactResolver artifactResolver = new RecordingArtifactResolver();
        RemoteRepositoryManager remoteRepositoryManager = new StaticRemoteRepositoryManager();
        DefaultArtifactDescriptorReader reader = new DefaultArtifactDescriptorReader()
                .setVersionResolver(versionResolver)
                .setArtifactResolver(artifactResolver)
                .setRemoteRepositoryManager(remoteRepositoryManager)
                .setModelBuilder(modelBuilder);

        MavenRepositorySystemSession session = new MavenRepositorySystemSession();
        session.setArtifactTypeRegistry(new DefaultArtifactTypeRegistry().add(new DefaultArtifactType("jar")));
        Artifact artifact = new DefaultArtifact("com.acme", "app", "", "jar", "1.0");
        RemoteRepository repository = new RemoteRepository("central", "default", "https://repo.example.test/maven2");
        ArtifactDescriptorRequest request = new ArtifactDescriptorRequest(
                artifact,
                Collections.singletonList(repository),
                "project");

        ArtifactDescriptorResult result = reader.readArtifactDescriptor(session, request);

        assertThat(versionResolver.requests).hasSize(1);
        assertThat(versionResolver.requests.get(0).getArtifact()).isSameAs(artifact);
        assertThat(artifactResolver.requests).hasSize(1);
        Artifact resolvedPom = artifactResolver.requests.get(0).getArtifact();
        assertThat(resolvedPom.getGroupId()).isEqualTo("com.acme");
        assertThat(resolvedPom.getArtifactId()).isEqualTo("app");
        assertThat(resolvedPom.getExtension()).isEqualTo("pom");
        assertThat(resolvedPom.getVersion()).isEqualTo("1.0");

        assertThat(modelBuilder.requests).hasSize(1);
        ModelBuildingRequest modelRequest = modelBuilder.requests.get(0);
        assertThat(modelRequest.getValidationLevel()).isEqualTo(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
        assertThat(modelRequest.isProcessPlugins()).isFalse();
        assertThat(modelRequest.isTwoPhaseBuilding()).isFalse();
        assertThat(modelRequest.getModelSource()).isNotNull();
        assertThat(modelRequest.getModelResolver()).isNotNull();

        assertThat(result.getRepositories()).hasSize(1);
        RemoteRepository modelRepository = result.getRepositories().get(0);
        assertThat(modelRepository.getId()).isEqualTo("project-repo");
        assertThat(modelRepository.getUrl()).isEqualTo("https://repo.example.test/project");
        assertThat(modelRepository.getPolicy(false).isEnabled()).isTrue();
        assertThat(modelRepository.getPolicy(false).getUpdatePolicy()).isEqualTo("daily");
        assertThat(modelRepository.getPolicy(false).getChecksumPolicy()).isEqualTo("warn");
        assertThat(modelRepository.getPolicy(true).isEnabled()).isFalse();
        assertThat(modelRepository.getPolicy(true).getUpdatePolicy()).isEqualTo("never");
        assertThat(modelRepository.getPolicy(true).getChecksumPolicy()).isEqualTo("fail");

        assertThat(result.getDependencies()).hasSize(1);
        org.sonatype.aether.graph.Dependency dependency = result.getDependencies().get(0);
        assertThat(dependency.getArtifact().getGroupId()).isEqualTo("com.acme.libs");
        assertThat(dependency.getArtifact().getArtifactId()).isEqualTo("api");
        assertThat(dependency.getArtifact().getVersion()).isEqualTo("2.1");
        assertThat(dependency.getArtifact().getExtension()).isEqualTo("jar");
        assertThat(dependency.getScope()).isEqualTo("runtime");
        assertThat(dependency.isOptional()).isTrue();
        assertThat(dependency.getExclusions()).singleElement().satisfies(exclusion -> {
            assertThat(exclusion.getGroupId()).isEqualTo("com.acme.excluded");
            assertThat(exclusion.getArtifactId()).isEqualTo("legacy");
            assertThat(exclusion.getClassifier()).isEqualTo("*");
            assertThat(exclusion.getExtension()).isEqualTo("*");
        });

        assertThat(result.getManagedDependencies()).hasSize(1);
        org.sonatype.aether.graph.Dependency managedDependency = result.getManagedDependencies().get(0);
        assertThat(managedDependency.getArtifact().getGroupId()).isEqualTo("com.acme.platform");
        assertThat(managedDependency.getArtifact().getArtifactId()).isEqualTo("bom-entry");
        assertThat(managedDependency.getArtifact().getVersion()).isEqualTo("3.0");
        assertThat(managedDependency.getScope()).isEqualTo("compile");

        assertThat(result.getProperties())
                .containsEntry("license.count", 1)
                .containsEntry("license.0.name", "Apache-2.0")
                .containsEntry("license.0.url", "https://www.apache.org/licenses/LICENSE-2.0.txt")
                .containsEntry("license.0.comments", "integration-test license")
                .containsEntry("license.0.distribution", "repo");
    }

    private static Model effectiveModel() {
        Model model = new Model();
        model.addRepository(projectRepository());
        model.addDependency(runtimeDependency());

        DependencyManagement dependencyManagement = new DependencyManagement();
        dependencyManagement.addDependency(managedDependency());
        model.setDependencyManagement(dependencyManagement);

        License license = new License();
        license.setName("Apache-2.0");
        license.setUrl("https://www.apache.org/licenses/LICENSE-2.0.txt");
        license.setComments("integration-test license");
        license.setDistribution("repo");
        model.addLicense(license);
        return model;
    }

    private static Repository projectRepository() {
        Repository repository = new Repository();
        repository.setId("project-repo");
        repository.setLayout("default");
        repository.setUrl("https://repo.example.test/project");

        RepositoryPolicy releases = new RepositoryPolicy();
        releases.setEnabled(true);
        releases.setUpdatePolicy("daily");
        releases.setChecksumPolicy("warn");
        repository.setReleases(releases);

        RepositoryPolicy snapshots = new RepositoryPolicy();
        snapshots.setEnabled(false);
        snapshots.setUpdatePolicy("never");
        snapshots.setChecksumPolicy("fail");
        repository.setSnapshots(snapshots);
        return repository;
    }

    private static Dependency runtimeDependency() {
        Dependency dependency = new Dependency();
        dependency.setGroupId("com.acme.libs");
        dependency.setArtifactId("api");
        dependency.setVersion("2.1");
        dependency.setType("jar");
        dependency.setScope("runtime");
        dependency.setOptional(true);

        Exclusion exclusion = new Exclusion();
        exclusion.setGroupId("com.acme.excluded");
        exclusion.setArtifactId("legacy");
        dependency.addExclusion(exclusion);
        return dependency;
    }

    private static Dependency managedDependency() {
        Dependency dependency = new Dependency();
        dependency.setGroupId("com.acme.platform");
        dependency.setArtifactId("bom-entry");
        dependency.setVersion("3.0");
        dependency.setType("jar");
        dependency.setScope("compile");
        return dependency;
    }

    private static final class RecordingVersionResolver implements VersionResolver {
        private final String version;
        private final List<VersionRequest> requests = new ArrayList<>();

        private RecordingVersionResolver(String version) {
            this.version = version;
        }

        @Override
        public VersionResult resolveVersion(RepositorySystemSession session, VersionRequest request)
                throws VersionResolutionException {
            requests.add(request);
            return new VersionResult(request).setVersion(version);
        }
    }

    private static final class RecordingArtifactResolver implements ArtifactResolver {
        private final List<ArtifactRequest> requests = new ArrayList<>();

        @Override
        public ArtifactResult resolveArtifact(RepositorySystemSession session, ArtifactRequest request)
                throws ArtifactResolutionException {
            requests.add(request);
            File pomFile = new File("build/generated-test-metadata/artifact-descriptor-reader/pom.xml");
            Artifact artifact = request.getArtifact().setFile(pomFile);
            return new ArtifactResult(request).setArtifact(artifact);
        }

        @Override
        public List<ArtifactResult> resolveArtifacts(
                RepositorySystemSession session,
                Collection<? extends ArtifactRequest> requests) throws ArtifactResolutionException {
            List<ArtifactResult> results = new ArrayList<>(requests.size());
            for (ArtifactRequest request : requests) {
                results.add(resolveArtifact(session, request));
            }
            return results;
        }
    }

    private static final class StaticRemoteRepositoryManager implements RemoteRepositoryManager {
        @Override
        public List<RemoteRepository> aggregateRepositories(
                RepositorySystemSession session,
                List<RemoteRepository> dominantRepositories,
                List<RemoteRepository> recessiveRepositories,
                boolean recessiveIsRaw) {
            List<RemoteRepository> repositories = new ArrayList<>(dominantRepositories);
            repositories.addAll(recessiveRepositories);
            return repositories;
        }

        @Override
        public org.sonatype.aether.repository.RepositoryPolicy getPolicy(
                RepositorySystemSession session,
                RemoteRepository repository,
                boolean releases,
                boolean snapshots) {
            return repository.getPolicy(releases);
        }

        @Override
        public RepositoryConnector getRepositoryConnector(RepositorySystemSession session, RemoteRepository repository)
                throws NoRepositoryConnectorException {
            throw new NoRepositoryConnectorException(repository);
        }
    }

    private static final class RecordingModelBuilder implements ModelBuilder {
        private final Model effectiveModel;
        private final List<ModelBuildingRequest> requests = new ArrayList<>();

        private RecordingModelBuilder(Model effectiveModel) {
            this.effectiveModel = effectiveModel;
        }

        @Override
        public ModelBuildingResult build(ModelBuildingRequest request) throws ModelBuildingException {
            requests.add(request);
            return new SimpleModelBuildingResult(effectiveModel);
        }

        @Override
        public ModelBuildingResult build(ModelBuildingRequest request, ModelBuildingResult result)
                throws ModelBuildingException {
            return build(request);
        }
    }

    private static final class SimpleModelBuildingResult implements ModelBuildingResult {
        private final Model effectiveModel;

        private SimpleModelBuildingResult(Model effectiveModel) {
            this.effectiveModel = effectiveModel;
        }

        @Override
        public List<String> getModelIds() {
            return Collections.emptyList();
        }

        @Override
        public Model getEffectiveModel() {
            return effectiveModel;
        }

        @Override
        public Model getRawModel() {
            return effectiveModel;
        }

        @Override
        public Model getRawModel(String modelId) {
            return null;
        }

        @Override
        public List<Profile> getActivePomProfiles(String modelId) {
            return Collections.emptyList();
        }

        @Override
        public List<Profile> getActiveExternalProfiles() {
            return Collections.emptyList();
        }

        @Override
        public List<org.apache.maven.model.building.ModelProblem> getProblems() {
            return Collections.emptyList();
        }
    }
}
