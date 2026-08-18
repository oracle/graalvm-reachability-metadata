/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_shared.maven_common_artifact_filters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
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
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.shared.artifact.filter.collection.ArtifactTransitivityFilter;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.junit.jupiter.api.Test;

public class InvokerTest {
    @Test
    void parameterizedInvokerInvokesPublicMethodOnAccessibleTarget() throws Throwable {
        Class<?> invokerClass = Class.forName("org.apache.maven.shared.artifact.filter.collection.Invoker");
        MethodType methodType = MethodType.methodType(Object.class, Object.class, String.class, Class.class,
                Object.class);
        MethodHandle invoker = MethodHandles.privateLookupIn(invokerClass, MethodHandles.lookup())
                .findStatic(invokerClass, "invoke", methodType);

        Object result = invoker.invoke(new MethodTarget(), "appendSuffix", String.class, "common-artifact");

        assertThat(result).isEqualTo("common-artifact-filters");
    }

    @Test
    void artifactTransitivityFilterReflectivelyReadsResolvedDependenciesAndAttemptsArtifactConversion() {
        Artifact selectedArtifact = artifact("com.acme", "platform", "1.0", Artifact.SCOPE_COMPILE, "jar");
        List<Dependency> dependencies = Arrays.asList(
                dependency("com.acme", "api", "1.0", "jar", Artifact.SCOPE_COMPILE),
                dependency("org.other", "runtime", "1.0", "jar", Artifact.SCOPE_RUNTIME));
        DependencyResolutionResult resolutionResult = new TestDependencyResolutionResult(dependencies);
        ProjectBuilder projectBuilder = projectBuilderReturning(resolutionResult);

        assertThatThrownBy(() -> new ArtifactTransitivityFilter(selectedArtifact, new DefaultProjectBuildingRequest(),
                projectBuilder))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("java.lang.Class.toArtifact")
                .hasCauseInstanceOf(NoSuchMethodException.class);
    }

    private static Artifact artifact(String groupId, String artifactId, String version, String scope, String type) {
        DefaultArtifactHandler handler = new DefaultArtifactHandler(type);
        return new DefaultArtifact(groupId, artifactId, VersionRange.createFromVersion(version), scope, type, null,
                handler);
    }

    private static Dependency dependency(String groupId, String artifactId, String version, String extension,
            String scope) {
        org.eclipse.aether.artifact.DefaultArtifact artifact = new org.eclipse.aether.artifact.DefaultArtifact(groupId,
                artifactId, null, extension, version);
        return new Dependency(artifact, scope);
    }

    private static ProjectBuilder projectBuilderReturning(DependencyResolutionResult resolutionResult) {
        return new ProjectBuilder() {
            @Override
            public ProjectBuildingResult build(File file, ProjectBuildingRequest request) {
                throw new UnsupportedOperationException("Only artifact builds are expected in this test.");
            }

            @Override
            public ProjectBuildingResult build(Artifact artifact, ProjectBuildingRequest request) {
                assertThat(artifact.getArtifactId()).isEqualTo("platform");
                assertThat(request.isResolveDependencies()).isTrue();
                return projectBuildingResult(resolutionResult);
            }

            @Override
            public ProjectBuildingResult build(Artifact artifact, boolean allowStubModel,
                    ProjectBuildingRequest request) {
                throw new UnsupportedOperationException("Only dependency-resolving artifact builds are expected.");
            }

            @Override
            public ProjectBuildingResult build(ModelSource modelSource, ProjectBuildingRequest request) {
                throw new UnsupportedOperationException("Only artifact builds are expected in this test.");
            }

            @Override
            public List<ProjectBuildingResult> build(List<File> pomFiles, boolean recursive,
                    ProjectBuildingRequest request) {
                throw new UnsupportedOperationException("Only artifact builds are expected in this test.");
            }
        };
    }

    private static ProjectBuildingResult projectBuildingResult(DependencyResolutionResult resolutionResult) {
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
                return null;
            }

            @Override
            public List<ModelProblem> getProblems() {
                return Collections.emptyList();
            }

            @Override
            public DependencyResolutionResult getDependencyResolutionResult() {
                return resolutionResult;
            }
        };
    }

    public static final class MethodTarget {
        public String appendSuffix(String value) {
            return value + "-filters";
        }
    }

    public static final class TestDependencyResolutionResult implements DependencyResolutionResult {
        private final List<Dependency> dependencies;

        private TestDependencyResolutionResult(List<Dependency> dependencies) {
            this.dependencies = dependencies;
        }

        @Override
        public DependencyNode getDependencyGraph() {
            return null;
        }

        @Override
        public List<Dependency> getDependencies() {
            return dependencies;
        }

        @Override
        public List<Dependency> getResolvedDependencies() {
            return dependencies;
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
