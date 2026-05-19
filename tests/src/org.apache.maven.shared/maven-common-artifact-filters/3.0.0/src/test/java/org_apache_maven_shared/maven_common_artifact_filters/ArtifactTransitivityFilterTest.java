/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_shared.maven_common_artifact_filters;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.Collections;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.model.building.ModelSource;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.shared.artifact.filter.collection.ArtifactTransitivityFilter;
import org.junit.jupiter.api.Test;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.graph.DependencyNode;

public class ArtifactTransitivityFilterTest {
    @Test
    void constructorChecksForMaven31CoreClassBeforeReadingResolvedDependencies() throws ProjectBuildingException {
        Artifact selected = artifact("com.acme", "platform", "1.0", Artifact.SCOPE_COMPILE, "jar");
        RecordingProjectBuilder projectBuilder = new RecordingProjectBuilder();
        DefaultProjectBuildingRequest request = new DefaultProjectBuildingRequest();
        Thread currentThread = Thread.currentThread();
        ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
        Maven31CoreClassLoader coreClassLoader = new Maven31CoreClassLoader(originalContextClassLoader);

        currentThread.setContextClassLoader(coreClassLoader);
        try {
            new ArtifactTransitivityFilter(selected, request, projectBuilder);
        } finally {
            currentThread.setContextClassLoader(originalContextClassLoader);
        }

        assertThat(coreClassLoader.maven31CoreClassRequested).isTrue();
        assertThat(projectBuilder.builtArtifact).isSameAs(selected);
        assertThat(projectBuilder.buildingRequest).isNotSameAs(request);
        assertThat(projectBuilder.buildingRequest.isResolveDependencies()).isTrue();
    }

    private static Artifact artifact(String groupId, String artifactId, String version, String scope, String type) {
        DefaultArtifactHandler handler = new DefaultArtifactHandler(type);
        return new DefaultArtifact(groupId, artifactId, VersionRange.createFromVersion(version), scope, type, null,
                handler);
    }

    private static final class Maven31CoreClassLoader extends ClassLoader {
        private static final String MAVEN31_CORE_CLASS = "org.eclipse.aether.artifact.Artifact";

        private boolean maven31CoreClassRequested;

        private Maven31CoreClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (MAVEN31_CORE_CLASS.equals(name)) {
                maven31CoreClassRequested = true;
                return Maven31CoreClassMarker.class;
            }
            return super.loadClass(name);
        }
    }

    public static final class Maven31CoreClassMarker {
        private Maven31CoreClassMarker() {
        }
    }

    private static final class RecordingProjectBuilder implements ProjectBuilder {
        private Artifact builtArtifact;
        private ProjectBuildingRequest buildingRequest;

        @Override
        public ProjectBuildingResult build(File pomFile, ProjectBuildingRequest request)
                throws ProjectBuildingException {
            throw new UnsupportedOperationException("Only artifact builds are expected in this test.");
        }

        @Override
        public ProjectBuildingResult build(Artifact artifact, ProjectBuildingRequest request)
                throws ProjectBuildingException {
            this.builtArtifact = artifact;
            this.buildingRequest = request;
            return new TestProjectBuildingResult(new EmptyDependencyResolutionResult());
        }

        @Override
        public ProjectBuildingResult build(Artifact artifact, boolean allowStubModel, ProjectBuildingRequest request)
                throws ProjectBuildingException {
            throw new UnsupportedOperationException(
                    "Only dependency-resolving artifact builds are expected in this test.");
        }

        @Override
        public ProjectBuildingResult build(ModelSource modelSource, ProjectBuildingRequest request)
                throws ProjectBuildingException {
            throw new UnsupportedOperationException("Only artifact builds are expected in this test.");
        }

        @Override
        public List<ProjectBuildingResult> build(List<File> pomFiles, boolean recursive, ProjectBuildingRequest request)
                throws ProjectBuildingException {
            throw new UnsupportedOperationException("Only artifact builds are expected in this test.");
        }
    }

    private static final class TestProjectBuildingResult implements ProjectBuildingResult {
        private final DependencyResolutionResult dependencyResolutionResult;

        private TestProjectBuildingResult(DependencyResolutionResult dependencyResolutionResult) {
            this.dependencyResolutionResult = dependencyResolutionResult;
        }

        @Override
        public String getProjectId() {
            return "com.acme:platform:jar:1.0";
        }

        @Override
        public File getPomFile() {
            return null;
        }

        @Override
        public MavenProject getProject() {
            return null;
        }

        @Override
        public List<ModelProblem> getProblems() {
            return Collections.emptyList();
        }

        @Override
        public DependencyResolutionResult getDependencyResolutionResult() {
            return dependencyResolutionResult;
        }
    }

    public static final class EmptyDependencyResolutionResult implements DependencyResolutionResult {
        @Override
        public DependencyNode getDependencyGraph() {
            return null;
        }

        @Override
        public List<Dependency> getDependencies() {
            return Collections.emptyList();
        }

        @Override
        public List<Dependency> getResolvedDependencies() {
            return Collections.emptyList();
        }

        @Override
        public List<Dependency> getUnresolvedDependencies() {
            return Collections.emptyList();
        }

        @Override
        public List<Exception> getCollectionErrors() {
            return Collections.emptyList();
        }

        @Override
        public List<Exception> getResolutionErrors(Dependency dependency) {
            return Collections.emptyList();
        }
    }
}
