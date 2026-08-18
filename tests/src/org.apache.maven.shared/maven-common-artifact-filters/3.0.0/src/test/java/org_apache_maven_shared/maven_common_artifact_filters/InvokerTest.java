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

public class InvokerTest {
    @Test
    void artifactTransitivityFilterReadsResolvedAetherDependencies() throws ProjectBuildingException {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        CapturingProjectBuilder projectBuilder = new CapturingProjectBuilder(projectBuildingResultWithDependencies());

        Thread.currentThread().setContextClassLoader(
                new HidingClassLoader(originalClassLoader, "org.eclipse.aether.artifact.Artifact"));
        try {
            Artifact selected = artifact("com.acme", "platform", "1.0", Artifact.SCOPE_COMPILE, "jar", null);
            new ArtifactTransitivityFilter(selected, new DefaultProjectBuildingRequest(), projectBuilder);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }

        assertThat(projectBuilder.builtArtifact.getArtifactId()).isEqualTo("platform");
        assertThat(projectBuilder.resolveDependencies).isTrue();
    }

    private static Artifact artifact(String groupId, String artifactId, String version, String scope, String type,
            String classifier) {
        DefaultArtifactHandler handler = new DefaultArtifactHandler(type);
        VersionRange versionRange = VersionRange.createFromVersion(version);
        return new DefaultArtifact(groupId, artifactId, versionRange, scope, type, classifier, handler);
    }

    private static ProjectBuildingResult projectBuildingResultWithDependencies(Dependency... dependencies) {
        return new ProjectBuildingResult() {
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
                return new MavenProject();
            }

            @Override
            public List<ModelProblem> getProblems() {
                return Collections.emptyList();
            }

            @Override
            public DependencyResolutionResult getDependencyResolutionResult() {
                return new DependencyResolutionResultWithDependencies(dependencies);
            }
        };
    }

    public static final class DependencyResolutionResultWithDependencies implements DependencyResolutionResult {
        private final Dependency[] dependencies;

        private DependencyResolutionResultWithDependencies(Dependency[] dependencies) {
            this.dependencies = dependencies;
        }

        @Override
        public DependencyNode getDependencyGraph() {
            return null;
        }

        @Override
        public List<Dependency> getDependencies() {
            return List.of(dependencies);
        }

        @Override
        public List<Dependency> getResolvedDependencies() {
            return getDependencies();
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

    private static final class CapturingProjectBuilder implements ProjectBuilder {
        private final ProjectBuildingResult result;
        private Artifact builtArtifact;
        private boolean resolveDependencies;

        private CapturingProjectBuilder(ProjectBuildingResult result) {
            this.result = result;
        }

        @Override
        public ProjectBuildingResult build(File pomFile, ProjectBuildingRequest request)
                throws ProjectBuildingException {
            throw new UnsupportedOperationException("Only artifact builds are expected in this test.");
        }

        @Override
        public ProjectBuildingResult build(Artifact artifact, ProjectBuildingRequest request)
                throws ProjectBuildingException {
            builtArtifact = artifact;
            resolveDependencies = request.isResolveDependencies();
            return result;
        }

        @Override
        public ProjectBuildingResult build(Artifact artifact, boolean allowStubModel, ProjectBuildingRequest request)
                throws ProjectBuildingException {
            return build(artifact, request);
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


    private static final class HidingClassLoader extends ClassLoader {
        private final String hiddenClassName;

        private HidingClassLoader(ClassLoader parent, String hiddenClassName) {
            super(parent);
            this.hiddenClassName = hiddenClassName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (hiddenClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }
}
