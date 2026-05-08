/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_core;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.ArtifactCoordinates;
import org.apache.maven.api.Dependency;
import org.apache.maven.api.DependencyCoordinates;
import org.apache.maven.api.DependencyScope;
import org.apache.maven.api.DownloadedArtifact;
import org.apache.maven.api.Exclusion;
import org.apache.maven.api.JavaPathType;
import org.apache.maven.api.Language;
import org.apache.maven.api.Lifecycle;
import org.apache.maven.api.Listener;
import org.apache.maven.api.LocalRepository;
import org.apache.maven.api.Node;
import org.apache.maven.api.NodeVisitor;
import org.apache.maven.api.Packaging;
import org.apache.maven.api.PathScope;
import org.apache.maven.api.PathType;
import org.apache.maven.api.ProducedArtifact;
import org.apache.maven.api.Project;
import org.apache.maven.api.ProjectScope;
import org.apache.maven.api.ProtoSession;
import org.apache.maven.api.RemoteRepository;
import org.apache.maven.api.Service;
import org.apache.maven.api.Session;
import org.apache.maven.api.SessionData;
import org.apache.maven.api.Type;
import org.apache.maven.api.Version;
import org.apache.maven.api.VersionConstraint;
import org.apache.maven.api.VersionRange;
import org.apache.maven.api.cache.CacheMetadata;
import org.apache.maven.api.cache.CacheRetention;
import org.apache.maven.api.cache.RequestResult;
import org.apache.maven.api.model.Plugin;
import org.apache.maven.api.model.Repository;
import org.apache.maven.api.services.ArtifactCoordinatesFactoryRequest;
import org.apache.maven.api.services.ArtifactDeployerRequest;
import org.apache.maven.api.services.ArtifactFactoryRequest;
import org.apache.maven.api.services.ArtifactInstallerRequest;
import org.apache.maven.api.services.ArtifactResolverRequest;
import org.apache.maven.api.services.BuilderProblem;
import org.apache.maven.api.services.DependencyCoordinatesFactoryRequest;
import org.apache.maven.api.services.DependencyResolverRequest;
import org.apache.maven.api.services.ModelBuilderRequest;
import org.apache.maven.api.services.ModelSource;
import org.apache.maven.api.services.ProblemCollector;
import org.apache.maven.api.services.Request;
import org.apache.maven.api.services.RequestTrace;
import org.apache.maven.api.services.Result;
import org.apache.maven.api.services.Source;
import org.apache.maven.api.services.Sources;
import org.apache.maven.api.services.VersionRangeResolverRequest;
import org.apache.maven.api.services.VersionResolverException;
import org.apache.maven.api.services.VersionResolverRequest;
import org.apache.maven.api.services.xml.XmlReaderRequest;
import org.apache.maven.api.services.xml.XmlWriterRequest;
import org.apache.maven.api.settings.Settings;
import org.apache.maven.api.toolchain.ToolchainModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.StandardLocation;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

public class Maven_api_coreTest {
    @TempDir
    Path tempDirectory;

    @Test
    void dependencyScopesExposeStableIdsAndTransitivity() {
        assertThat(DependencyScope.COMPILE.id()).isEqualTo("compile");
        assertThat(DependencyScope.UNDEFINED.id()).isEmpty();
        assertThat(DependencyScope.forId("runtime")).isSameAs(DependencyScope.RUNTIME);
        assertThat(DependencyScope.forId("does-not-exist")).isNull();
        assertThat(DependencyScope.RUNTIME.isTransitive()).isTrue();
        assertThat(DependencyScope.COMPILE.isTransitive()).isTrue();
        assertThat(DependencyScope.PROVIDED.isTransitive()).isFalse();
        assertThat(DependencyScope.TEST.is("test")).isTrue();
        assertThat(DependencyScope.TEST.is("TEST")).isFalse();
    }

    @Test
    void builtInPathScopesDescribeProjectAndDependencyBoundaries() {
        assertThat(ProjectScope.MAIN.id()).isEqualTo("main");
        assertThat(ProjectScope.TEST.id()).isEqualTo("test");

        assertThat(PathScope.MAIN_COMPILE.id()).isEqualTo("main-compile");
        assertThat(PathScope.MAIN_COMPILE.projectScope()).isEqualTo(ProjectScope.MAIN);
        assertThat(PathScope.MAIN_COMPILE.dependencyScopes())
                .containsExactlyInAnyOrder(
                        DependencyScope.COMPILE_ONLY, DependencyScope.COMPILE, DependencyScope.PROVIDED);

        assertThat(PathScope.TEST_RUNTIME.projectScope()).isEqualTo(ProjectScope.TEST);
        assertThat(PathScope.TEST_RUNTIME.dependencyScopes())
                .containsExactlyInAnyOrder(
                        DependencyScope.COMPILE,
                        DependencyScope.RUNTIME,
                        DependencyScope.PROVIDED,
                        DependencyScope.TEST,
                        DependencyScope.TEST_RUNTIME);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> PathScope.TEST_RUNTIME.dependencyScopes().add(DependencyScope.SYSTEM));
    }

    @Test
    void javaPathTypesMapToolLocationsAndFormatCommandLineOptions() {
        Path first = Path.of("lib", "first.jar");
        Path second = Path.of("lib", "second.jar");

        assertThat(JavaPathType.valueOf(StandardLocation.CLASS_PATH)).contains(JavaPathType.CLASSES);
        assertThat(JavaPathType.CLASSES.location()).contains(StandardLocation.CLASS_PATH);
        assertThat(JavaPathType.CLASSES.option()).contains("--class-path");
        assertThat(JavaPathType.CLASSES.option(List.of(first, second)))
                .containsExactly("--class-path", first + java.io.File.pathSeparator + second);
        assertThat(JavaPathType.MODULES.toString()).isEqualTo("PathType[MODULES]");
        assertThat(JavaPathType.MODULES.id()).isEqualTo("MODULES");
        assertThat(JavaPathType.MODULES.name()).isEqualTo("MODULES");
        assertThat(JavaPathType.AGENT.location()).isEmpty();
        assertThat(JavaPathType.AGENT.option()).contains("-agentpath");
        assertThat(JavaPathType.CLASSES.option(List.of())).isEmpty();

        PathType unresolved = PathType.UNRESOLVED;
        assertThat(unresolved.id()).isEqualTo("UNRESOLVED");
        assertThat(unresolved.option()).isEmpty();
        assertThat(unresolved.option(List.of(first))).isEmpty();
    }

    @Test
    void patchModulePathTypesCarryModuleNamesAndValueSemantics() {
        JavaPathType.Modular module = JavaPathType.patchModule("com.example.app");
        JavaPathType.Modular sameModule = JavaPathType.patchModule("com.example.app");
        JavaPathType.Modular otherModule = JavaPathType.patchModule("com.example.other");

        assertThat(module.rawType()).isEqualTo(JavaPathType.PATCH_MODULE);
        assertThat(module.moduleName()).isEqualTo("com.example.app");
        assertThat(module.id()).isEqualTo("PATCH_MODULE:com.example.app");
        assertThat(module.name()).isEqualTo("PATCH_MODULE");
        assertThat(module.toString()).isEqualTo("PathType[PATCH_MODULE:com.example.app]");
        Path classes = Path.of("target", "classes");
        assertThat(module.option(List.of(classes)))
                .containsExactly("--patch-module", "com.example.app=" + classes);
        assertThat(module).isEqualTo(sameModule).hasSameHashCodeAs(sameModule).isNotEqualTo(otherModule);
    }

    @Test
    void protoSessionCopiesInputsAndOverlaysEffectiveProperties() {
        Map<String, String> userProperties = new HashMap<>(Map.of("mode", "user", "userOnly", "yes"));
        Map<String, String> systemProperties = new HashMap<>(Map.of("mode", "system", "java.home", "/jdk"));
        Instant startTime = Instant.parse("2024-01-02T03:04:05Z");

        ProtoSession session = ProtoSession.newBuilder()
                .withUserProperties(userProperties)
                .withSystemProperties(systemProperties)
                .withStartTime(startTime)
                .withTopDirectory(tempDirectory.resolve("top"))
                .withRootDirectory(tempDirectory)
                .build();
        userProperties.put("mode", "changed");
        systemProperties.put("added", "ignored");

        assertThat(session.getUserProperties()).containsEntry("mode", "user");
        assertThat(session.getSystemProperties()).containsEntry("mode", "system");
        assertThat(session.getEffectiveProperties())
                .containsEntry("mode", "user")
                .containsEntry("java.home", "/jdk")
                .containsEntry("userOnly", "yes")
                .doesNotContainKey("added");
        assertThat(session.getStartTime()).isEqualTo(startTime);
        assertThat(session.getRootDirectory()).isEqualTo(tempDirectory);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> session.getUserProperties().put("new", "value"));
    }

    @Test
    void protoSessionToBuilderPreservesSessionsWithoutRootDirectory() {
        ProtoSession session = ProtoSession.newBuilder()
                .withUserProperties(Map.of())
                .withSystemProperties(Map.of())
                .withTopDirectory(tempDirectory)
                .withRootDirectory(null)
                .build();

        assertThatIllegalStateException().isThrownBy(session::getRootDirectory);
        ProtoSession copy = session.toBuilder().withUserProperties(Map.of("copied", "true")).build();
        assertThat(copy.getTopDirectory()).isEqualTo(tempDirectory);
        assertThat(copy.getUserProperties()).containsEntry("copied", "true");
        assertThatIllegalStateException().isThrownBy(copy::getRootDirectory);
    }

    @Test
    void sessionDataKeysDistinguishTypeAndQualifier() {
        SessionData.Key<String> defaultStringKey = SessionData.key(String.class);
        SessionData.Key<String> sameDefaultStringKey = SessionData.key(String.class);
        SessionData.Key<String> namedStringKey = SessionData.key(String.class, "name");
        SessionData.Key<Integer> integerKey = SessionData.key(Integer.class);

        assertThat(defaultStringKey.type()).isEqualTo(String.class);
        assertThat(defaultStringKey).isEqualTo(sameDefaultStringKey).hasSameHashCodeAs(sameDefaultStringKey);
        assertThat(defaultStringKey).isNotEqualTo(namedStringKey).isNotEqualTo(integerKey);
    }

    @Test
    void sourcesReadFilesResolveRelativesAndExposeCacheMetadata() throws IOException {
        Path pom = tempDirectory.resolve("project").resolve("pom.xml");
        Path modulePom = tempDirectory.resolve("project").resolve("module").resolve("pom.xml");
        Files.createDirectories(modulePom.getParent());
        Files.writeString(pom, "<project/>", StandardCharsets.UTF_8);
        Files.writeString(modulePom, "<module/>", StandardCharsets.UTF_8);

        Source source = Sources.fromPath(pom);
        assertThat(source.getPath()).isEqualTo(pom.normalize());
        assertThat(source.getLocation()).endsWith("pom.xml");
        try (InputStream stream = source.openStream()) {
            assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("<project/>");
        }
        assertThat(source.resolve("module/pom.xml").getPath()).isEqualTo(pom.resolve("module/pom.xml").normalize());

        ModelSource buildSource = Sources.buildSource(pom);
        assertThat(buildSource).isInstanceOf(CacheMetadata.class);
        assertThat(((CacheMetadata) buildSource).getCacheRetention()).isEqualTo(CacheRetention.REQUEST_SCOPED);
        ModelSource resolvedModule = buildSource.resolve(candidate -> Files.exists(candidate) ? candidate : null,
                "module/pom.xml");
        assertThat(resolvedModule).isNotNull();
        assertThat(resolvedModule.getPath()).isEqualTo(modulePom.normalize());

        ModelSource resolvedSource = Sources.resolvedSource(pom, "com.example:demo:pom:1");
        assertThat(resolvedSource.getPath()).isNull();
        assertThat(resolvedSource.getLocation()).isEqualTo("com.example:demo:pom:1");
        assertThat(resolvedSource.resolve("other.xml")).isNull();
        assertThat(resolvedSource.resolve(candidate -> candidate, "other.xml")).isNull();
    }

    @Test
    void problemCollectorCountsOverflowsAndKeepsMoreSevereProblems() {
        ProblemCollector<SimpleProblem> collector = ProblemCollector.create(2);
        SimpleProblem warning = new SimpleProblem(BuilderProblem.Severity.WARNING, "warning");
        SimpleProblem error = new SimpleProblem(BuilderProblem.Severity.ERROR, "error");
        SimpleProblem fatal = new SimpleProblem(BuilderProblem.Severity.FATAL, "fatal");

        assertThat(collector.reportProblem(warning)).isTrue();
        assertThat(collector.reportProblem(error)).isTrue();
        assertThat(collector.reportProblem(fatal)).isTrue();

        assertThat(collector.totalProblemsReported()).isEqualTo(3);
        assertThat(collector.problemsReportedFor(BuilderProblem.Severity.WARNING)).isEqualTo(1);
        assertThat(collector.problemsReportedFor(BuilderProblem.Severity.ERROR, BuilderProblem.Severity.FATAL))
                .isEqualTo(2);
        assertThat(collector.hasWarningProblems()).isTrue();
        assertThat(collector.hasErrorProblems()).isTrue();
        assertThat(collector.hasFatalProblems()).isTrue();
        assertThat(collector.problemsOverflow()).isTrue();
        assertThat(collector.problems().map(BuilderProblem::getMessage)).containsExactly("fatal", "error");
        assertThat(collector.problems(BuilderProblem.Severity.WARNING)).isEmpty();
    }

    @Test
    void emptyProblemCollectorReportsNoProblemsAndRejectsWrites() {
        ProblemCollector<SimpleProblem> collector = ProblemCollector.empty();

        assertThat(collector.totalProblemsReported()).isZero();
        assertThat(collector.hasWarningProblems()).isFalse();
        assertThat(collector.problemsOverflow()).isFalse();
        assertThat(collector.problems()).isEmpty();
        assertThatIllegalStateException()
                .isThrownBy(() -> collector.reportProblem(
                        new SimpleProblem(BuilderProblem.Severity.WARNING, "ignored")));
    }

    @Test
    void artifactAndDependencyDefaultIdentifiersUseMavenCoordinateShape() {
        SimpleVersion version = new SimpleVersion("1.2.3");
        SimpleVersionConstraint versionConstraint = new SimpleVersionConstraint(version);
        SimpleArtifactCoordinates coordinates = new SimpleArtifactCoordinates(
                "org.example", "demo", "", versionConstraint, "jar");
        SimpleArtifactCoordinates classifiedCoordinates = new SimpleArtifactCoordinates(
                "org.example", "demo", "tests", versionConstraint, "jar");
        SimpleProducedArtifact artifact = new SimpleProducedArtifact(
                "org.example", "demo", version, version, "", "jar", false, coordinates);
        SimpleDependency dependency = new SimpleDependency(
                "org.example", "demo", version, version, "tests", "jar", true,
                classifiedCoordinates, new SimpleType("test-jar"), DependencyScope.TEST, true);

        assertThat(coordinates.getId()).isEqualTo("org.example:demo:jar:1.2.3");
        assertThat(classifiedCoordinates.getId()).isEqualTo("org.example:demo:jar:tests:1.2.3");
        assertThat(artifact.key()).isEqualTo("org.example:demo:jar:1.2.3");
        assertThat(dependency.key()).isEqualTo("org.example:demo:jar:tests:1.2.3");
        assertThat(dependency.toCoordinates()).isSameAs(classifiedCoordinates);
        assertThat(dependency.getType().id()).isEqualTo("test-jar");
        assertThat(dependency.getScope()).isEqualTo(DependencyScope.TEST);
        assertThat(dependency.isOptional()).isTrue();
    }

    @Test
    void lifecycleDefaultMethodsFlattenNestedPhases() {
        SimplePhase compile = new SimplePhase("compile", List.of());
        SimplePhase test = new SimplePhase("test", List.of());
        SimplePhase build = new SimplePhase("build", List.of(compile, test));
        SimpleLifecycle lifecycle = new SimpleLifecycle(List.of(build));

        assertThat(lifecycle.v3phases()).containsExactly(build);
        assertThat(lifecycle.allPhases().map(Lifecycle.Phase::name))
                .containsExactly("build", "compile", "test");
    }

    @Test
    void nodeStreamTraversesTheWholeTreeDepthFirst() {
        SimpleNode grandchild = new SimpleNode("grandchild", List.of());
        SimpleNode child = new SimpleNode("child", List.of(grandchild));
        SimpleNode sibling = new SimpleNode("sibling", List.of());
        SimpleNode root = new SimpleNode("root", List.of(child, sibling));

        assertThat(root.stream().map(Node::asString)).containsExactly("root", "child", "grandchild", "sibling");
    }

    @Test
    void xmlReaderAndWriterRequestsPreserveAllConfiguredInputs() throws Exception {
        ByteArrayInputStream inputStream = new ByteArrayInputStream("<project/>".getBytes(StandardCharsets.UTF_8));
        StringReader reader = new StringReader("<project/>");
        XmlReaderRequest.Transformer transformer = (source, fieldName) -> fieldName + '=' + source;
        XmlReaderRequest readerRequest = XmlReaderRequest.builder()
                .path(tempDirectory.resolve("pom.xml"))
                .rootDirectory(tempDirectory)
                .url(tempDirectory.toUri().toURL())
                .inputStream(inputStream)
                .reader(reader)
                .transformer(transformer)
                .strict(true)
                .modelId("org.example:demo")
                .location("memory")
                .addDefaultEntities(false)
                .build();

        assertThat(readerRequest.getPath()).isEqualTo(tempDirectory.resolve("pom.xml"));
        assertThat(readerRequest.getRootDirectory()).isEqualTo(tempDirectory);
        assertThat(readerRequest.getURL()).isEqualTo(tempDirectory.toUri().toURL());
        assertThat(readerRequest.getInputStream()).isSameAs(inputStream);
        assertThat(readerRequest.getReader()).isSameAs(reader);
        assertThat(readerRequest.getTransformer().transform("value", "name")).isEqualTo("name=value");
        assertThat(readerRequest.isStrict()).isTrue();
        assertThat(readerRequest.getModelId()).isEqualTo("org.example:demo");
        assertThat(readerRequest.getLocation()).isEqualTo("memory");
        assertThat(readerRequest.isAddDefaultEntities()).isFalse();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        StringWriter writer = new StringWriter();
        XmlWriterRequest<String> writerRequest = XmlWriterRequest.<String>builder()
                .path(tempDirectory.resolve("out.xml"))
                .outputStream(outputStream)
                .writer(writer)
                .content("content")
                .inputLocationFormatter(location -> "at " + location)
                .build();
        assertThat(writerRequest.getPath()).isEqualTo(tempDirectory.resolve("out.xml"));
        assertThat(writerRequest.getOutputStream()).isSameAs(outputStream);
        assertThat(writerRequest.getWriter()).isSameAs(writer);
        assertThat(writerRequest.getContent()).isEqualTo("content");
        assertThat(writerRequest.getInputLocationFormatter().apply("line 1")).isEqualTo("at line 1");
    }

    @Test
    void requestBuildersCreateImmutableValueObjects() {
        StubSession session = new StubSession(tempDirectory);
        RequestTrace trace = new RequestTrace(RequestTrace.CONTEXT_PROJECT, null, "demo");
        SimpleProducedArtifact artifact = producedArtifact("demo");
        StubRemoteRepository repository = new StubRemoteRepository(
                "central", "default", "https://repo.maven.apache.org/maven2");

        ArtifactFactoryRequest artifactFactoryRequest = ArtifactFactoryRequest.builder()
                .session(session)
                .trace(trace)
                .groupId("org.example")
                .artifactId("demo")
                .version("1.0")
                .classifier("sources")
                .extension("jar")
                .type(Type.JAVA_SOURCE)
                .build();
        assertThat(artifactFactoryRequest.getSession()).isSameAs(session);
        assertThat(artifactFactoryRequest.getTrace()).isSameAs(trace);
        assertThat(artifactFactoryRequest.getGroupId()).isEqualTo("org.example");
        assertThat(artifactFactoryRequest.getClassifier()).isEqualTo("sources");
        assertThat(artifactFactoryRequest.toString()).contains("org.example", "demo", "sources");

        ArtifactInstallerRequest installerRequest = ArtifactInstallerRequest.builder()
                .session(session)
                .trace(trace)
                .artifacts(List.<ProducedArtifact>of(artifact))
                .build();
        assertThat(installerRequest.getArtifacts()).containsExactly(artifact);

        ArtifactDeployerRequest deployerRequest = ArtifactDeployerRequest.builder()
                .session(session)
                .repository(repository)
                .artifacts(List.<ProducedArtifact>of(artifact))
                .retryFailedDeploymentCount(3)
                .build();
        assertThat(deployerRequest.getRepository()).isEqualTo(repository);
        assertThat(deployerRequest.getArtifacts()).containsExactly(artifact);
        assertThat(deployerRequest.getRetryFailedDeploymentCount()).isEqualTo(3);
    }

    @Test
    void coordinatesFactoryRequestsCaptureCoordinateStringAndDependencyDetails() {
        StubSession session = new StubSession(tempDirectory);
        SimpleVersion version = new SimpleVersion("1.0");
        SimpleVersionConstraint constraint = new SimpleVersionConstraint(version);
        SimpleArtifactCoordinates coordinates = new SimpleArtifactCoordinates(
                "org.example", "demo", "", constraint, "jar");
        SimpleExclusion exclusion = new SimpleExclusion("org.slf4j", "slf4j-api");
        SimpleDependency dependency = new SimpleDependency(
                "org.example", "demo", version, version, "", "jar", false, coordinates,
                new SimpleType(Type.JAR), DependencyScope.RUNTIME, false);

        ArtifactCoordinatesFactoryRequest artifactRequest = ArtifactCoordinatesFactoryRequest.builder()
                .session(session)
                .coordinateString("org.example:demo:1.0")
                .extension("jar")
                .build();
        assertThat(artifactRequest.getCoordinatesString()).isEqualTo("org.example:demo:1.0");
        assertThat(artifactRequest.getExtension()).isEqualTo("jar");

        DependencyCoordinatesFactoryRequest dependencyRequest = DependencyCoordinatesFactoryRequest.builder()
                .session(session)
                .groupId("org.example")
                .artifactId("demo")
                .version("1.0")
                .extension("jar")
                .scope("runtime")
                .optional(true)
                .exclusion(exclusion)
                .build();
        assertThat(dependencyRequest.getScope()).isEqualTo("runtime");
        assertThat(dependencyRequest.isOptional()).isTrue();
        assertThat(dependencyRequest.getExclusions()).containsExactly(exclusion);

        DependencyCoordinatesFactoryRequest fromDependency = DependencyCoordinatesFactoryRequest.build(
                session, dependency);
        assertThat(fromDependency.getGroupId()).isEqualTo("org.example");
        assertThat(fromDependency.getScope()).isEqualTo("runtime");
        assertThat(fromDependency.isOptional()).isFalse();
    }

    @Test
    void resolverRequestsValidateRepositoriesAndUseDefaults() {
        StubSession session = new StubSession(tempDirectory);
        SimpleVersion version = new SimpleVersion("1.0");
        SimpleVersionConstraint constraint = new SimpleVersionConstraint(version);
        SimpleArtifactCoordinates coordinates = new SimpleArtifactCoordinates(
                "org.example", "demo", "", constraint, "jar");
        StubRemoteRepository central = new StubRemoteRepository(
                "central", "default", "https://repo.maven.apache.org/maven2");
        StubRemoteRepository internal = new StubRemoteRepository(
                "internal", "default", "https://repo.example.test/maven2");

        ArtifactResolverRequest artifactResolverRequest = ArtifactResolverRequest.builder()
                .session(session)
                .coordinates(List.of(coordinates))
                .repositories(List.of(central, internal))
                .build();
        assertThat(artifactResolverRequest.getCoordinates()).hasSize(1);
        assertThat(artifactResolverRequest.getCoordinates().iterator().next()).isEqualTo(coordinates);
        assertThat(artifactResolverRequest.getRepositories()).containsExactly(central, internal);

        DependencyResolverRequest dependencyResolverRequest = DependencyResolverRequest.builder()
                .session(session)
                .requestType(DependencyResolverRequest.RequestType.COLLECT)
                .root(coordinates)
                .dependency(coordinates)
                .managedDependency(coordinates)
                .verbose(true)
                .pathScope(PathScope.MAIN_RUNTIME)
                .pathTypeFilter(List.of(JavaPathType.CLASSES))
                .targetVersion(version)
                .build();
        assertThat(dependencyResolverRequest.getRoot()).contains(coordinates);
        assertThat(dependencyResolverRequest.getDependencies()).containsExactly(coordinates);
        assertThat(dependencyResolverRequest.getManagedDependencies()).containsExactly(coordinates);
        assertThat(dependencyResolverRequest.getVerbose()).isTrue();
        assertThat(dependencyResolverRequest.getPathTypeFilter().test(JavaPathType.CLASSES)).isTrue();
        assertThat(dependencyResolverRequest.getPathTypeFilter().test(JavaPathType.MODULES)).isFalse();
        assertThat(dependencyResolverRequest.getTargetVersion()).isEqualTo(version);
        assertThat(dependencyResolverRequest.getRepositories()).isNull();

        assertThatIllegalArgumentException().isThrownBy(() -> ArtifactResolverRequest.builder()
                .session(session)
                .coordinates(List.of(coordinates))
                .repositories(List.of(central, central))
                .build());
        assertThatIllegalArgumentException().isThrownBy(() -> DependencyResolverRequest.builder()
                .session(session)
                .requestType(DependencyResolverRequest.RequestType.RESOLVE)
                .dependency(coordinates)
                .verbose(true)
                .pathScope(PathScope.MAIN_RUNTIME)
                .build());
    }

    @Test
    void modelAndVersionRequestsPreserveSourcesAndRepositories() {
        StubSession session = new StubSession(tempDirectory);
        SimpleVersion version = new SimpleVersion("1.0");
        SimpleArtifactCoordinates coordinates = new SimpleArtifactCoordinates(
                "org.example", "demo", "", new SimpleVersionConstraint(version), "pom");
        ModelSource source = Sources.buildSource(tempDirectory.resolve("pom.xml"));
        StubRemoteRepository repository = new StubRemoteRepository(
                "central", "default", "https://repo.maven.apache.org/maven2");

        ModelBuilderRequest request = ModelBuilderRequest.builder()
                .session(session)
                .requestType(ModelBuilderRequest.RequestType.BUILD_PROJECT)
                .source(source)
                .locationTracking(true)
                .recursive(true)
                .activeProfileIds(List.of("dev"))
                .inactiveProfileIds(List.of("prod"))
                .systemProperties(Map.of("os.name", "test"))
                .userProperties(Map.of("skipTests", "true"))
                .repositoryMerging(ModelBuilderRequest.RepositoryMerging.REQUEST_DOMINANT)
                .repositories(List.of(repository))
                .build();

        assertThat(request.getSource()).isEqualTo(source);
        assertThat(request.getRequestType()).isEqualTo(ModelBuilderRequest.RequestType.BUILD_PROJECT);
        assertThat(request.isLocationTracking()).isTrue();
        assertThat(request.isRecursive()).isTrue();
        assertThat(request.getActiveProfileIds()).containsExactly("dev");
        assertThat(request.getInactiveProfileIds()).containsExactly("prod");
        assertThat(request.getSystemProperties()).containsEntry("os.name", "test");
        assertThat(request.getUserProperties()).containsEntry("skipTests", "true");
        assertThat(request.getRepositoryMerging()).isEqualTo(ModelBuilderRequest.RepositoryMerging.REQUEST_DOMINANT);
        assertThat(request.getRepositories()).containsExactly(repository);

        ModelBuilderRequest copied = ModelBuilderRequest.build(
                request, Sources.resolvedSource(tempDirectory.resolve("parent.pom"), "parent"));
        assertThat(copied.getSource().getLocation()).isEqualTo("parent");
        assertThat(copied.getActiveProfileIds()).containsExactly("dev");

        VersionResolverRequest versionRequest = VersionResolverRequest.builder()
                .session(session)
                .artifactCoordinates(coordinates)
                .repositories(List.of(repository))
                .build();
        VersionRangeResolverRequest rangeRequest = VersionRangeResolverRequest.builder()
                .session(session)
                .artifactCoordinates(coordinates)
                .repositories(List.of(repository))
                .build();
        assertThat(versionRequest.getArtifactCoordinates()).isEqualTo(coordinates);
        assertThat(versionRequest.getRepositories()).containsExactly(repository);
        assertThat(rangeRequest.getArtifactCoordinates()).isEqualTo(coordinates);
        assertThat(rangeRequest.getRepositories()).containsExactly(repository);
    }

    @Test
    void requestResultRecordReportsSuccessOnlyWhenResultIsPresentWithoutError() {
        StubSession session = new StubSession(tempDirectory);
        ArtifactFactoryRequest request = ArtifactFactoryRequest.build(session, "org.example", "demo", "1", "jar");
        RequestResult<ArtifactFactoryRequest, SimpleResult<ArtifactFactoryRequest>> success =
                new RequestResult<>(request, new SimpleResult<>(request), null);
        RequestResult<ArtifactFactoryRequest, SimpleResult<ArtifactFactoryRequest>> failure =
                new RequestResult<>(request, null, new IllegalStateException("boom"));

        assertThat(success.isSuccess()).isTrue();
        assertThat(success.request()).isSameAs(request);
        assertThat(success.result().getRequest()).isSameAs(request);
        assertThat(success.error()).isNull();
        assertThat(failure.isSuccess()).isFalse();
        assertThat(failure.error()).hasMessage("boom");
    }

    private static SimpleProducedArtifact producedArtifact(String artifactId) {
        SimpleVersion version = new SimpleVersion("1.0");
        SimpleArtifactCoordinates coordinates = new SimpleArtifactCoordinates(
                "org.example", artifactId, "", new SimpleVersionConstraint(version), "jar");
        return new SimpleProducedArtifact("org.example", artifactId, version, version, "", "jar", false, coordinates);
    }

    private record SimpleVersion(String value) implements Version {
        @Override
        public int compareTo(Version other) {
            return value.compareTo(other.toString());
        }

        @Override
        public String toString() {
            return value;
        }
    }

    private record SimpleVersionConstraint(Version version) implements VersionConstraint {
        @Override
        public VersionRange getVersionRange() {
            return null;
        }

        @Override
        public Version getRecommendedVersion() {
            return version;
        }

        @Override
        public boolean contains(Version candidate) {
            return version.equals(candidate);
        }

        @Override
        public String toString() {
            return version.toString();
        }
    }

    private record SimpleArtifactCoordinates(
            String groupId,
            String artifactId,
            String classifier,
            VersionConstraint versionConstraint,
            String extension) implements DependencyCoordinates {
        @Override
        public String getGroupId() {
            return groupId;
        }

        @Override
        public String getArtifactId() {
            return artifactId;
        }

        @Override
        public String getClassifier() {
            return classifier;
        }

        @Override
        public VersionConstraint getVersionConstraint() {
            return versionConstraint;
        }

        @Override
        public String getExtension() {
            return extension;
        }

        @Override
        public Type getType() {
            return new SimpleType(Type.JAR);
        }

        @Override
        public DependencyScope getScope() {
            return DependencyScope.UNDEFINED;
        }

        @Override
        public Boolean getOptional() {
            return null;
        }

        @Override
        public Collection<Exclusion> getExclusions() {
            return List.of();
        }
    }

    private record SimpleProducedArtifact(
            String groupId,
            String artifactId,
            Version version,
            Version baseVersion,
            String classifier,
            String extension,
            boolean snapshot,
            ArtifactCoordinates coordinates) implements ProducedArtifact {
        @Override
        public String getGroupId() {
            return groupId;
        }

        @Override
        public String getArtifactId() {
            return artifactId;
        }

        @Override
        public Version getVersion() {
            return version;
        }

        @Override
        public Version getBaseVersion() {
            return baseVersion;
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
        public boolean isSnapshot() {
            return snapshot;
        }

        @Override
        public ArtifactCoordinates toCoordinates() {
            return coordinates;
        }
    }

    private record SimpleDependency(
            String groupId,
            String artifactId,
            Version version,
            Version baseVersion,
            String classifier,
            String extension,
            boolean snapshot,
            DependencyCoordinates coordinates,
            Type type,
            DependencyScope scope,
            boolean optional) implements Dependency {
        @Override
        public String getGroupId() {
            return groupId;
        }

        @Override
        public String getArtifactId() {
            return artifactId;
        }

        @Override
        public Version getVersion() {
            return version;
        }

        @Override
        public Version getBaseVersion() {
            return baseVersion;
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
        public boolean isSnapshot() {
            return snapshot;
        }

        @Override
        public DependencyCoordinates toCoordinates() {
            return coordinates;
        }

        @Override
        public Type getType() {
            return type;
        }

        @Override
        public DependencyScope getScope() {
            return scope;
        }

        @Override
        public boolean isOptional() {
            return optional;
        }
    }

    private record SimpleType(String id) implements Type {
        @Override
        public String getExtension() {
            return "jar";
        }

        @Override
        public String getClassifier() {
            return "";
        }

        @Override
        public Language getLanguage() {
            return null;
        }

        @Override
        public boolean isIncludesDependencies() {
            return false;
        }

        @Override
        public Set<PathType> getPathTypes() {
            return Set.of(JavaPathType.CLASSES);
        }
    }

    private record SimpleExclusion(String groupId, String artifactId) implements Exclusion {
        @Override
        public String getGroupId() {
            return groupId;
        }

        @Override
        public String getArtifactId() {
            return artifactId;
        }
    }

    private record SimpleProblem(BuilderProblem.Severity severity, String message) implements BuilderProblem {
        @Override
        public String getSource() {
            return "pom.xml";
        }

        @Override
        public int getLineNumber() {
            return 1;
        }

        @Override
        public int getColumnNumber() {
            return 2;
        }

        @Override
        public String getLocation() {
            return "pom.xml:1:2";
        }

        @Override
        public Exception getException() {
            return null;
        }

        @Override
        public String getMessage() {
            return message;
        }

        @Override
        public BuilderProblem.Severity getSeverity() {
            return severity;
        }
    }

    private record SimpleLifecycle(Collection<Lifecycle.Phase> phases) implements Lifecycle {
        @Override
        public String id() {
            return Lifecycle.DEFAULT;
        }

        @Override
        public Collection<Lifecycle.Phase> phases() {
            return phases;
        }

        @Override
        public Collection<Lifecycle.Alias> aliases() {
            return List.of();
        }
    }

    private record SimplePhase(String name, List<Lifecycle.Phase> phases) implements Lifecycle.Phase {
        @Override
        public List<Plugin> plugins() {
            return List.of();
        }

        @Override
        public Collection<Lifecycle.Link> links() {
            return List.of();
        }

        @Override
        public Stream<Lifecycle.Phase> allPhases() {
            return Stream.concat(Stream.of(this), phases.stream().flatMap(Lifecycle.Phase::allPhases));
        }
    }

    private record SimpleNode(String value, List<Node> children) implements Node {
        @Override
        public Artifact getArtifact() {
            return null;
        }

        @Override
        public Dependency getDependency() {
            return null;
        }

        @Override
        public List<Node> getChildren() {
            return children;
        }

        @Override
        public List<RemoteRepository> getRemoteRepositories() {
            return List.of();
        }

        @Override
        public Optional<RemoteRepository> getRepository() {
            return Optional.empty();
        }

        @Override
        public boolean accept(NodeVisitor visitor) {
            if (!visitor.enter(this)) {
                return false;
            }
            for (Node child : children) {
                if (!child.accept(visitor)) {
                    return false;
                }
            }
            return visitor.leave(this);
        }

        @Override
        public Node filter(java.util.function.Predicate<Node> filter) {
            return new SimpleNode(value, children.stream().filter(filter).toList());
        }

        @Override
        public String asString() {
            return value;
        }
    }

    private record SimpleResult<REQ extends Request<?>>(REQ request) implements Result<REQ> {
        @Override
        public REQ getRequest() {
            return request;
        }
    }

    private record StubRemoteRepository(String id, String type, String url) implements RemoteRepository {
        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public String getUrl() {
            return url;
        }

        @Override
        public String getProtocol() {
            return url.substring(0, url.indexOf(':'));
        }
    }

    private static final class InMemorySessionData implements SessionData {
        private final Map<Key<?>, Object> values = new ConcurrentHashMap<>();

        @Override
        public <T> void set(Key<T> key, T value) {
            if (value == null) {
                values.remove(key);
            } else {
                values.put(key, value);
            }
        }

        @Override
        public <T> boolean replace(Key<T> key, T oldValue, T newValue) {
            if (!Objects.equals(values.get(key), oldValue)) {
                return false;
            }
            set(key, newValue);
            return true;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(Key<T> key) {
            return (T) values.get(key);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T computeIfAbsent(Key<T> key, Supplier<T> supplier) {
            return (T) values.computeIfAbsent(key, ignored -> supplier.get());
        }
    }

    private static final class StubSession implements Session {
        private final Path directory;
        private final SessionData data = new InMemorySessionData();

        private StubSession(Path directory) {
            this.directory = directory;
        }

        @Override
        public Map<String, String> getUserProperties() {
            return Map.of("user", "value");
        }

        @Override
        public Map<String, String> getSystemProperties() {
            return Map.of("system", "value");
        }

        @Override
        public Instant getStartTime() {
            return Instant.EPOCH;
        }

        @Override
        public Path getTopDirectory() {
            return directory;
        }

        @Override
        public Path getRootDirectory() {
            return directory;
        }

        @Override
        public Version getMavenVersion() {
            return new SimpleVersion("4.0.0");
        }

        @Override
        public Settings getSettings() {
            return null;
        }

        @Override
        public Collection<ToolchainModel> getToolchains() {
            return List.of();
        }

        @Override
        public LocalRepository getLocalRepository() {
            return null;
        }

        @Override
        public List<RemoteRepository> getRemoteRepositories() {
            return List.of();
        }

        @Override
        public SessionData getData() {
            return data;
        }

        @Override
        public Map<String, String> getEffectiveProperties(Project project) {
            Map<String, String> effective = new HashMap<>(getSystemProperties());
            effective.putAll(getUserProperties());
            return Collections.unmodifiableMap(effective);
        }

        @Override
        public int getDegreeOfConcurrency() {
            return 1;
        }

        @Override
        public List<Project> getProjects() {
            return List.of();
        }

        @Override
        public Map<String, Object> getPluginContext(Project project) {
            return Map.of();
        }

        @Override
        public <T extends Service> T getService(Class<T> type) {
            throw unsupported();
        }

        @Override
        public Session withLocalRepository(LocalRepository localRepository) {
            return this;
        }

        @Override
        public Session withRemoteRepositories(List<RemoteRepository> repositories) {
            return this;
        }

        @Override
        public void registerListener(Listener listener) {
            // This lightweight session stub does not keep listener state.
        }

        @Override
        public void unregisterListener(Listener listener) {
            // This lightweight session stub does not keep listener state.
        }

        @Override
        public Collection<Listener> getListeners() {
            return List.of();
        }

        @Override
        public LocalRepository createLocalRepository(Path path) {
            throw unsupported();
        }

        @Override
        public RemoteRepository createRemoteRepository(String id, String url) {
            throw unsupported();
        }

        @Override
        public RemoteRepository createRemoteRepository(Repository repository) {
            throw unsupported();
        }

        @Override
        public ArtifactCoordinates createArtifactCoordinates(String coordinates) {
            throw unsupported();
        }

        @Override
        public ArtifactCoordinates createArtifactCoordinates(
                String groupId, String artifactId, String version, String extension) {
            throw unsupported();
        }

        @Override
        public ArtifactCoordinates createArtifactCoordinates(
                String groupId, String artifactId, String version, String classifier, String extension, String type) {
            throw unsupported();
        }

        @Override
        public ArtifactCoordinates createArtifactCoordinates(Artifact artifact) {
            throw unsupported();
        }

        @Override
        public DependencyCoordinates createDependencyCoordinates(ArtifactCoordinates coordinates) {
            throw unsupported();
        }

        @Override
        public DependencyCoordinates createDependencyCoordinates(Dependency dependency) {
            throw unsupported();
        }

        @Override
        public Artifact createArtifact(String groupId, String artifactId, String version, String extension) {
            throw unsupported();
        }

        @Override
        public Artifact createArtifact(
                String groupId, String artifactId, String version, String classifier, String extension, String type) {
            throw unsupported();
        }

        @Override
        public ProducedArtifact createProducedArtifact(
                String groupId, String artifactId, String version, String extension) {
            throw unsupported();
        }

        @Override
        public ProducedArtifact createProducedArtifact(
                String groupId, String artifactId, String version, String classifier, String extension, String type) {
            throw unsupported();
        }

        @Override
        public DownloadedArtifact resolveArtifact(ArtifactCoordinates coordinates) {
            throw unsupported();
        }

        @Override
        public DownloadedArtifact resolveArtifact(
                ArtifactCoordinates coordinates, List<RemoteRepository> repositories) {
            throw unsupported();
        }

        @Override
        public Collection<DownloadedArtifact> resolveArtifacts(ArtifactCoordinates... coordinates) {
            throw unsupported();
        }

        @Override
        public Collection<DownloadedArtifact> resolveArtifacts(Collection<? extends ArtifactCoordinates> coordinates) {
            throw unsupported();
        }

        @Override
        public Collection<DownloadedArtifact> resolveArtifacts(
                Collection<? extends ArtifactCoordinates> coordinates, List<RemoteRepository> repositories) {
            throw unsupported();
        }

        @Override
        public DownloadedArtifact resolveArtifact(Artifact artifact) {
            throw unsupported();
        }

        @Override
        public DownloadedArtifact resolveArtifact(Artifact artifact, List<RemoteRepository> repositories) {
            throw unsupported();
        }

        @Override
        public Collection<DownloadedArtifact> resolveArtifacts(Artifact... artifacts) {
            throw unsupported();
        }

        @Override
        public void installArtifacts(ProducedArtifact... artifacts) {
            throw unsupported();
        }

        @Override
        public void installArtifacts(Collection<ProducedArtifact> artifacts) {
            throw unsupported();
        }

        @Override
        public void deployArtifact(RemoteRepository repository, ProducedArtifact... artifacts) {
            throw unsupported();
        }

        @Override
        public void setArtifactPath(ProducedArtifact artifact, Path path) {
            throw unsupported();
        }

        @Override
        public Optional<Path> getArtifactPath(Artifact artifact) {
            return Optional.empty();
        }

        @Override
        public Path getPathForLocalArtifact(Artifact artifact) {
            throw unsupported();
        }

        @Override
        public Path getPathForRemoteArtifact(RemoteRepository repository, Artifact artifact) {
            throw unsupported();
        }

        @Override
        public boolean isVersionSnapshot(String version) {
            return version.endsWith("-SNAPSHOT");
        }

        @Override
        public Node collectDependencies(Artifact artifact, PathScope pathScope) {
            throw unsupported();
        }

        @Override
        public Node collectDependencies(Project project, PathScope pathScope) {
            throw unsupported();
        }

        @Override
        public Node collectDependencies(DependencyCoordinates dependency, PathScope pathScope) {
            throw unsupported();
        }

        @Override
        public List<Node> flattenDependencies(Node node, PathScope pathScope) {
            throw unsupported();
        }

        @Override
        public List<Path> resolveDependencies(DependencyCoordinates dependency) {
            throw unsupported();
        }

        @Override
        public List<Path> resolveDependencies(List<DependencyCoordinates> dependencies) {
            throw unsupported();
        }

        @Override
        public List<Path> resolveDependencies(Project project, PathScope pathScope) {
            throw unsupported();
        }

        @Override
        public Map<PathType, List<Path>> resolveDependencies(
                DependencyCoordinates dependency, PathScope pathScope, Collection<PathType> desiredTypes) {
            throw unsupported();
        }

        @Override
        public Map<PathType, List<Path>> resolveDependencies(
                Project project, PathScope pathScope, Collection<PathType> desiredTypes) {
            throw unsupported();
        }

        @Override
        public Version resolveVersion(ArtifactCoordinates coordinates) throws VersionResolverException {
            throw unsupported();
        }

        @Override
        public List<Version> resolveVersionRange(ArtifactCoordinates coordinates) throws VersionResolverException {
            throw unsupported();
        }

        @Override
        public List<Version> resolveVersionRange(
                ArtifactCoordinates coordinates, List<RemoteRepository> repositories) throws VersionResolverException {
            throw unsupported();
        }

        @Override
        public Optional<Version> resolveHighestVersion(
                ArtifactCoordinates coordinates, List<RemoteRepository> repositories) throws VersionResolverException {
            throw unsupported();
        }

        @Override
        public Version parseVersion(String version) {
            return new SimpleVersion(version);
        }

        @Override
        public VersionRange parseVersionRange(String range) {
            throw unsupported();
        }

        @Override
        public VersionConstraint parseVersionConstraint(String constraint) {
            return new SimpleVersionConstraint(new SimpleVersion(constraint));
        }

        @Override
        public Type requireType(String id) {
            return new SimpleType(id);
        }

        @Override
        public Language requireLanguage(String id) {
            throw unsupported();
        }

        @Override
        public Packaging requirePackaging(String id) {
            throw unsupported();
        }

        @Override
        public ProjectScope requireProjectScope(String id) {
            throw unsupported();
        }

        @Override
        public DependencyScope requireDependencyScope(String id) {
            DependencyScope scope = DependencyScope.forId(id);
            if (scope == null) {
                throw unsupported();
            }
            return scope;
        }

        @Override
        public PathScope requirePathScope(String id) {
            throw unsupported();
        }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("not needed by these tests");
        }
    }
}
