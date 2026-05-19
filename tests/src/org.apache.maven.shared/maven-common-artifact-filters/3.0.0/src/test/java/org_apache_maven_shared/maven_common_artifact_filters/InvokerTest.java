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
    void invokeDispatchesSingleArgumentMethods() throws Throwable {
        MethodHandles.Lookup collectionLookup = MethodHandles.privateLookupIn(ArtifactTransitivityFilter.class,
                MethodHandles.lookup());
        Class<?> invokerClass = collectionLookup.findClass("org.apache.maven.shared.artifact.filter.collection.Invoker");
        MethodHandle invoke = collectionLookup.findStatic(invokerClass, "invoke", MethodType.methodType(Object.class,
                Object.class, String.class, Class.class, Object.class));

        Object result = invoke.invoke(new GreetingTarget(), "greet", String.class, "native image");

        assertThat(result).isEqualTo("Hello, native image");
    }

    @Test
    void artifactTransitivityFilterInvokesRepositoryUtilsWithDependencyArtifactArgument() {
        Artifact selected = artifact("com.acme", "platform", "1.0", Artifact.SCOPE_COMPILE, "jar");
        Dependency dependency = sonatypeDependency("com.acme", "api", "1.0", Artifact.SCOPE_COMPILE, "jar");
        RecordingProjectBuilder projectBuilder = new RecordingProjectBuilder(dependency);
        DefaultProjectBuildingRequest request = new DefaultProjectBuildingRequest();
        Thread currentThread = Thread.currentThread();
        ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();

        currentThread.setContextClassLoader(new HidingClassLoader(originalContextClassLoader,
                "org.eclipse.aether.artifact.Artifact"));
        try {
            assertThatThrownBy(() -> new ArtifactTransitivityFilter(selected, request, projectBuilder))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("toArtifact");
        } finally {
            currentThread.setContextClassLoader(originalContextClassLoader);
        }

        assertThat(projectBuilder.builtArtifact).isSameAs(selected);
        assertThat(projectBuilder.buildingRequest).isNotSameAs(request);
        assertThat(projectBuilder.buildingRequest.isResolveDependencies()).isTrue();
    }

    public static final class GreetingTarget {
        public String greet(String name) {
            return "Hello, " + name;
        }
    }

    private static Artifact artifact(String groupId, String artifactId, String version, String scope, String type) {
        DefaultArtifactHandler handler = new DefaultArtifactHandler(type);
        return new DefaultArtifact(groupId, artifactId, VersionRange.createFromVersion(version), scope, type, null,
                handler);
    }

    private static Dependency sonatypeDependency(String groupId, String artifactId, String version, String scope,
            String type) {
        org.sonatype.aether.artifact.Artifact artifact = new org.sonatype.aether.util.artifact.DefaultArtifact(
                groupId, artifactId, null, type, version);
        return new Dependency(artifact, scope);
    }

    private static final class HidingClassLoader extends ClassLoader {
        private final String hiddenClassName;

        private HidingClassLoader(ClassLoader parent, String hiddenClassName) {
            super(parent);
            this.hiddenClassName = hiddenClassName;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (hiddenClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }
    }

    private static final class RecordingProjectBuilder implements ProjectBuilder {
        private final List<Dependency> dependencies;
        private Artifact builtArtifact;
        private ProjectBuildingRequest buildingRequest;

        private RecordingProjectBuilder(Dependency dependency) {
            this.dependencies = Collections.singletonList(dependency);
        }

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
            return new TestProjectBuildingResult(new TestDependencyResolutionResult(dependencies));
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
