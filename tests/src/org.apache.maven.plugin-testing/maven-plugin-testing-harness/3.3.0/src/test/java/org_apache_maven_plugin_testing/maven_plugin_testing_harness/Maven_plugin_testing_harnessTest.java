/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_plugin_testing.maven_plugin_testing_harness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ResolutionListener;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.testing.ArtifactStubFactory;
import org.apache.maven.plugin.testing.ConfigurationException;
import org.apache.maven.plugin.testing.MojoParameters;
import org.apache.maven.plugin.testing.ResolverExpressionEvaluatorStub;
import org.apache.maven.plugin.testing.SilentLog;
import org.apache.maven.plugin.testing.resources.TestResources;
import org.apache.maven.plugin.testing.stubs.ArtifactStub;
import org.apache.maven.plugin.testing.stubs.DefaultArtifactHandlerStub;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;
import org.apache.maven.plugin.testing.stubs.StubArtifactCollector;
import org.apache.maven.plugin.testing.stubs.StubArtifactRepository;
import org.apache.maven.plugin.testing.stubs.StubArtifactResolver;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class Maven_plugin_testing_harnessTest {
    @TempDir
    Path tempDir;

    @Test
    void mojoParametersCreateNamedDomNodesWithValues() {
        Xpp3Dom parameter = MojoParameters.newParameter("outputDirectory", "target/generated-sources");

        assertThat(parameter.getName()).isEqualTo("outputDirectory");
        assertThat(parameter.getValue()).isEqualTo("target/generated-sources");
        assertThat(parameter.getChildCount()).isZero();
    }

    @Test
    void resolverExpressionEvaluatorExpandsBaseDirectoryAndEscapesDollars()
            throws ExpressionEvaluationException {
        ResolverExpressionEvaluatorStub evaluator = new ResolverExpressionEvaluatorStub();

        assertThat(evaluator.evaluate(null)).isNull();
        assertThat(evaluator.evaluate("plain-value")).isEqualTo("plain-value");
        assertThat(String.valueOf(evaluator.evaluate("${basedir}"))).isNotEmpty();
        assertThat(String.valueOf(evaluator.evaluate("prefix-${basedir}/child")))
                .startsWith("prefix-")
                .endsWith("/child");
        assertThat(evaluator.evaluate("cost $$5")).isEqualTo("cost $5");
        assertThat(evaluator.evaluate("$${escaped}")).isEqualTo("${escaped}");
        assertThat(evaluator.evaluate("${localRepository}"))
                .isInstanceOfSatisfying(ArtifactRepository.class, repository -> {
                    assertThat(repository.getId()).isEqualTo("localRepository");
                    assertThat(repository.getUrl()).contains("target/local-repo");
                });
    }

    @Test
    void resolverExpressionEvaluatorAlignsRelativeFilesToBasedir() {
        ResolverExpressionEvaluatorStub evaluator = new ResolverExpressionEvaluatorStub();
        File relativeFile = new File("src/test/projects/sample");
        File absoluteFile = tempDir.resolve("already-absolute.txt").toFile();

        assertThat(evaluator.alignToBaseDirectory(relativeFile).getPath())
                .endsWith(relativeFile.getPath());
        assertThat(evaluator.alignToBaseDirectory(absoluteFile)).isEqualTo(absoluteFile);
    }

    @Test
    void silentLogImplementsMavenLogAndPlexusLoggerAsNoOp() {
        SilentLog log = new SilentLog();
        RuntimeException failure = new RuntimeException("ignored");

        assertThat(log.isDebugEnabled()).isFalse();
        assertThat(log.isInfoEnabled()).isFalse();
        assertThat(log.isWarnEnabled()).isFalse();
        assertThat(log.isErrorEnabled()).isFalse();
        assertThat(log.isFatalErrorEnabled()).isFalse();
        assertThat(log.getChildLogger("child")).isNull();
        assertThat(log.getThreshold()).isZero();
        assertThat(log.getName()).isNull();

        assertThatCode(() -> {
            log.debug("debug");
            log.debug("debug", failure);
            log.debug(failure);
            log.info("info");
            log.info("info", failure);
            log.info(failure);
            log.warn("warn");
            log.warn("warn", failure);
            log.warn(failure);
            log.error("error");
            log.error("error", failure);
            log.error(failure);
            log.fatalError("fatal");
            log.fatalError("fatal", failure);
            log.setThreshold(10);
        }).doesNotThrowAnyException();
    }

    @Test
    void defaultArtifactHandlerStubDerivesDefaultsAndKeepsExplicitSettings() {
        DefaultArtifactHandlerStub jarHandler = new DefaultArtifactHandlerStub("jar");

        assertThat(jarHandler.getType()).isEqualTo("jar");
        assertThat(jarHandler.getExtension()).isEqualTo("jar");
        assertThat(jarHandler.getPackaging()).isEqualTo("jar");
        assertThat(jarHandler.getDirectory()).isEqualTo("jars");
        assertThat(jarHandler.getLanguage()).isEqualTo("none");
        assertThat(jarHandler.isAddedToClasspath()).isFalse();
        assertThat(jarHandler.isIncludesDependencies()).isFalse();

        jarHandler.setExtension("bundle");
        jarHandler.setPackaging("custom-packaging");
        jarHandler.setDirectory("custom-directory");
        jarHandler.setLanguage("java");
        jarHandler.setClassifier("tests");
        jarHandler.setAddedToClasspath(true);
        jarHandler.setIncludesDependencies(true);
        jarHandler.setType("custom-type");

        assertThat(jarHandler.getExtension()).isEqualTo("bundle");
        assertThat(jarHandler.getPackaging()).isEqualTo("custom-packaging");
        assertThat(jarHandler.getDirectory()).isEqualTo("custom-directory");
        assertThat(jarHandler.getLanguage()).isEqualTo("java");
        assertThat(jarHandler.getClassifier()).isEqualTo("tests");
        assertThat(jarHandler.isAddedToClasspath()).isTrue();
        assertThat(jarHandler.isIncludesDependencies()).isTrue();
        assertThat(jarHandler.getType()).isEqualTo("custom-type");

        DefaultArtifactHandlerStub testJarHandler = new DefaultArtifactHandlerStub("test-jar", "tests");
        assertThat(testJarHandler.getExtension()).isEqualTo("jar");
        assertThat(testJarHandler.getClassifier()).isEqualTo("tests");
    }

    @Test
    void artifactStubStoresCoordinatesRepositoryAndSnapshotState() {
        ArtifactStub artifact = new ArtifactStub();
        StubArtifactRepository repository = new StubArtifactRepository(tempDir.toString());
        File artifactFile = tempDir.resolve("artifact.jar").toFile();

        artifact.setGroupId("com.example");
        artifact.setArtifactId("demo");
        artifact.setType("test-jar");
        artifact.setVersion("1.0-SNAPSHOT");
        artifact.setScope("test");
        artifact.setFile(artifactFile);
        artifact.setRepository(repository);

        assertThat(artifact.getGroupId()).isEqualTo("com.example");
        assertThat(artifact.getArtifactId()).isEqualTo("demo");
        assertThat(artifact.getType()).isEqualTo("test-jar");
        assertThat(artifact.getVersion()).isEqualTo("1.0-SNAPSHOT");
        assertThat(artifact.getScope()).isEqualTo("test");
        assertThat(artifact.getFile()).isEqualTo(artifactFile);
        assertThat(artifact.getRepository()).isSameAs(repository);
        assertThat(artifact.hasClassifier()).isFalse();
        assertThat(artifact.isSnapshot()).isTrue();
        assertThat(artifact.isRelease()).isFalse();
        assertThat(artifact.isResolved()).isFalse();
        assertThat(artifact.isOptional()).isFalse();
        assertThat(artifact.isFromAuthoritativeRepository()).isTrue();
        assertThat(artifact.getDependencyConflictId()).isEqualTo("com.example:demo:test-jar:null");
        assertThat(artifact.toString()).isEqualTo("com.example:demo:test-jar:1.0-SNAPSHOT:test");

        artifact.setVersion("1.0");
        assertThat(artifact.isSnapshot()).isFalse();
        assertThat(artifact.isRelease()).isTrue();
    }

    @Test
    void artifactStubFactoryCreatesArtifactsAndRepositoryFiles(@TempDir Path repositoryRoot) throws IOException {
        ArtifactStubFactory factory = new ArtifactStubFactory(repositoryRoot.toFile(), true);

        Artifact artifact = factory.createArtifact("com.example", "demo", "1.2.3", "runtime", "test-jar", "tests");

        assertThat(factory.isCreateFiles()).isTrue();
        assertThat(factory.getWorkingDir()).isEqualTo(repositoryRoot.resolve("localTestRepo").toFile());
        assertThat(artifact.getGroupId()).isEqualTo("com.example");
        assertThat(artifact.getArtifactId()).isEqualTo("demo");
        assertThat(artifact.getVersion()).isEqualTo("1.2.3");
        assertThat(artifact.getScope()).isEqualTo("runtime");
        assertThat(artifact.getType()).isEqualTo("test-jar");
        assertThat(artifact.getClassifier()).isEqualTo("tests");
        assertThat(artifact.isRelease()).isTrue();
        assertThat(artifact.isSnapshot()).isFalse();
        assertThat(artifact.getFile()).isFile();
        assertThat(artifact.getFile().getName()).isEqualTo("demo-1.2.3-tests.jar");
        assertThat(ArtifactStubFactory.getFormattedFileName(artifact, false)).isEqualTo("demo-1.2.3-tests.jar");
        assertThat(ArtifactStubFactory.getFormattedFileName(artifact, true)).isEqualTo("demo-tests.jar");
        assertThat(ArtifactStubFactory.getUnpackableFileName(artifact))
                .isEqualTo("com.example-demo-1.2.3-tests-test-jar.txt");
    }

    @Test
    void artifactStubFactoryCopiesSourceFilesWhenAssigningArtifactFile(@TempDir Path repositoryRoot)
            throws IOException {
        ArtifactStubFactory factory = new ArtifactStubFactory();
        Path source = Files.writeString(tempDir.resolve("source.jar"), "payload", StandardCharsets.UTF_8);
        Artifact artifact = factory.createArtifact("g", "a", VersionRange.createFromVersion("2.0"),
                "compile", "jar", null, false);

        factory.setWorkingDir(repositoryRoot.toFile());
        factory.setSrcFile(source.toFile());
        factory.setArtifactFile(artifact, repositoryRoot.toFile(), source.toFile());

        assertThat(factory.getWorkingDir()).isEqualTo(repositoryRoot.toFile());
        assertThat(factory.getSrcFile()).isEqualTo(source.toFile());
        assertThat(artifact.getFile()).isFile().hasContent("payload");
        assertThat(artifact.getFile().getName()).isEqualTo("a-2.0.jar");

        factory.setCreateFiles(true);
        assertThat(factory.isCreateFiles()).isTrue();
    }

    @Test
    void artifactStubFactoryProvidesRepresentativeArtifactSets() throws IOException {
        ArtifactStubFactory factory = new ArtifactStubFactory();

        assertThat(coordinateIds(factory.getReleaseAndSnapshotArtifacts()))
                .containsExactlyInAnyOrder("testGroupId:release:jar:1.0", "testGroupId:snapshot:jar:2.0-SNAPSHOT");
        assertThat(scopes(factory.getScopedArtifacts()))
                .containsExactlyInAnyOrder("compile", "provided", "runtime", "system", "test");
        assertThat(types(factory.getTypedArtifacts()))
                .containsExactlyInAnyOrder("jar", "rar", "sources", "war", "zip");
        assertThat(classifiers(factory.getClassifiedArtifacts()))
                .containsExactlyInAnyOrder("four", "one", "three", "two");
        assertThat(factory.getTypedArchiveArtifacts()).hasSize(4);
        assertThat(factory.getArtifactArtifacts()).hasSize(4);
        assertThat(factory.getGroupIdArtifacts()).hasSize(4);
        assertThat(factory.getMixedArtifacts()).hasSize(12);
    }

    @Test
    void stubArtifactRepositoryExposesStableRepositoryDefaults() throws IOException {
        StubArtifactRepository repository = new StubArtifactRepository(tempDir.toString());
        Artifact artifact = new ArtifactStubFactory().createArtifact("com.example", "sample", "1.0");

        assertThat(repository.getBasedir()).isEqualTo(tempDir.toString());
        assertThat(repository.pathOf(artifact)).isEqualTo("com.example:sample:jar:1.0");
        assertThat(repository.findVersions(artifact)).isEmpty();
        assertThat(repository.getMirroredRepositories()).isEmpty();
        assertThat(repository.isUniqueVersion()).isFalse();
        assertThat(repository.isBlacklisted()).isFalse();
        assertThat(repository.isProjectAware()).isFalse();
        assertThat(repository.getUrl()).isNull();
        assertThat(repository.getId()).isNull();
        assertThat(repository.getLayout()).isNull();
    }

    @Test
    void stubArtifactResolverCanCreateFilesOrThrowConfiguredExceptions(@TempDir Path repositoryRoot)
            throws Exception {
        ArtifactStubFactory factory = new ArtifactStubFactory();
        factory.setWorkingDir(repositoryRoot.toFile());
        Artifact artifact = factory.createArtifact("com.example", "resolved", "1.0");

        StubArtifactResolver resolver = new StubArtifactResolver(factory, false, false);
        resolver.resolve(artifact, Collections.emptyList(), new StubArtifactRepository(repositoryRoot.toString()));

        assertThat(artifact.getFile()).isFile();
        assertThat(artifact.getFile().getName()).isEqualTo("resolved-1.0.jar");

        StubArtifactResolver resolutionFailure = new StubArtifactResolver(factory, true, false);
        assertThatThrownBy(() -> resolutionFailure.resolve(artifact, Collections.emptyList(), null))
                .isInstanceOf(ArtifactResolutionException.class)
                .hasMessageContaining("Catch!");

        StubArtifactResolver notFoundFailure = new StubArtifactResolver(factory, false, true);
        assertThatThrownBy(() -> notFoundFailure.resolve(artifact, Collections.emptyList(), null))
                .isInstanceOf(ArtifactNotFoundException.class)
                .hasMessageContaining("Catch!");
    }

    @Test
    void stubArtifactCollectorReturnsEmptyResolutionResultsForAllOverloads() throws Exception {
        StubArtifactCollector collector = new StubArtifactCollector();
        Artifact artifact = new ArtifactStubFactory().createArtifact("g", "a", "1");
        Set<Artifact> artifacts = Collections.singleton(artifact);
        Map<String, Artifact> managedVersions = Collections.emptyMap();
        List<ResolutionListener> listeners = Collections.emptyList();

        ArtifactResolutionResult requestResult = collector.collect(artifacts, artifact, managedVersions,
                new ArtifactResolutionRequest(), null, null, listeners, Collections.emptyList());
        ArtifactResolutionResult repositoryResult = collector.collect(artifacts, artifact, managedVersions,
                new StubArtifactRepository(tempDir.toString()), Collections.emptyList(), null, null, listeners);
        ArtifactResolutionResult legacyResult = collector.collect(artifacts, artifact,
                new StubArtifactRepository(tempDir.toString()), Collections.emptyList(), null, null, listeners);

        assertThat(requestResult.getArtifacts()).isEmpty();
        assertThat(repositoryResult.getArtifacts()).isEmpty();
        assertThat(legacyResult.getArtifacts()).isEmpty();
    }

    @Test
    void mavenProjectStubStoresCommonProjectState() throws Exception {
        Model model = new Model();
        MavenProjectStub project = new MavenProjectStub(model);
        MavenProject parent = new MavenProjectStub();
        Artifact artifact = new ArtifactStubFactory().createArtifact("g", "a", "1");
        Build build = new Build();
        License license = new License();
        Dependency dependency = new Dependency();
        List<String> runtimeClasspath = List.of("target/classes", "target/dependency/a.jar");

        project.setGroupId("com.example");
        project.setArtifactId("demo");
        project.setVersion("1.0");
        project.setName("Demo Project");
        project.setDescription("Project used by tests");
        project.setPackaging("jar");
        project.setModelVersion("4.0.0");
        project.setInceptionYear("2024");
        project.setUrl("https://example.invalid/demo");
        project.setFile(tempDir.resolve("pom.xml").toFile());
        project.setParent(parent);
        project.setArtifact(artifact);
        project.setBuild(build);
        project.setLicenses(List.of(license));
        project.setCompileDependencies(List.of(dependency));
        project.setRuntimeClasspathElements(runtimeClasspath);
        project.setExecutionRoot(true);

        project.addCompileSourceRoot("src/main/java");
        project.addCompileSourceRoot("generated/main/java");
        project.addTestCompileSourceRoot("src/test/java");
        project.addScriptSourceRoot("src/main/scripts");
        project.addAttachedArtifact(artifact);
        project.setDependencyArtifacts(Collections.singleton(artifact));

        assertThat(project.getModel()).isSameAs(model);
        assertThat(project.getGroupId()).isEqualTo("com.example");
        assertThat(project.getArtifactId()).isEqualTo("demo");
        assertThat(project.getVersion()).isEqualTo("1.0");
        assertThat(project.getName()).isEqualTo("Demo Project");
        assertThat(project.getDescription()).isEqualTo("Project used by tests");
        assertThat(project.getPackaging()).isEqualTo("jar");
        assertThat(project.getModelVersion()).isEqualTo("4.0.0");
        assertThat(project.getInceptionYear()).isEqualTo("2024");
        assertThat(project.getUrl()).isEqualTo("https://example.invalid/demo");
        assertThat(project.getFile()).isEqualTo(tempDir.resolve("pom.xml").toFile());
        assertThat(project.getParent()).isSameAs(parent);
        assertThat(project.hasParent()).isTrue();
        assertThat(project.getArtifact()).isSameAs(artifact);
        assertThat(project.getBuild()).isSameAs(build);
        assertThat(project.getLicenses()).containsExactly(license);
        assertThat(project.getCompileDependencies()).containsExactly(dependency);
        assertThat(project.getRuntimeClasspathElements()).containsExactlyElementsOf(runtimeClasspath);
        assertThat(project.getCompileSourceRoots()).containsExactly("src/main/java", "generated/main/java");
        assertThat(project.getCompileClasspathElements()).containsExactly("src/main/java", "generated/main/java");
        assertThat(project.getTestCompileSourceRoots()).containsExactly("src/test/java");
        assertThat(project.getScriptSourceRoots()).containsExactly("src/main/scripts");
        assertThat(project.getAttachedArtifacts()).containsExactly(artifact);
        assertThat(project.getDependencyArtifacts()).containsExactly(artifact);
        assertThat(project.isExecutionRoot()).isTrue();
        assertThat(project.getRemoteArtifactRepositories()).isEmpty();
        assertThat(project.getRepositories()).isEmpty();
        assertThat(project.getProperties()).isEmpty();
        assertThat(project.getModulePathAdjustment(project)).isEmpty();
    }

    @Test
    void testResourcesOperateOnDirectoryTrees() throws IOException, InterruptedException {
        File base = tempDir.toFile();
        TestResources.create(base, "created/created-by-helper.txt");
        Files.createDirectories(tempDir.resolve("source"));
        Files.createDirectories(tempDir.resolve("copy"));
        Files.writeString(tempDir.resolve("source/expected.txt"), "same", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("source/actual.txt"), "same", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("source/delete-me.txt"), "delete", StandardCharsets.UTF_8);

        TestResources.cp(base, "source/expected.txt", "copy/copied.txt");
        TestResources.assertFileContents(base, "source/expected.txt", "source/actual.txt");
        TestResources.assertDirectoryContents(base,
                "copy/",
                "copy/copied.txt",
                "created/",
                "created/created-by-helper.txt",
                "source/",
                "source/actual.txt",
                "source/delete-me.txt",
                "source/expected.txt");

        File fileToTouch = tempDir.resolve("source/actual.txt").toFile();
        assertThat(fileToTouch.setLastModified(0L)).isTrue();
        TestResources.touch(base, "source/actual.txt");
        assertThat(fileToTouch).exists();

        TestResources.rm(base, "source/delete-me.txt");
        assertThat(tempDir.resolve("source/delete-me.txt")).doesNotExist();
    }

    @Test
    void testResourcesRuleCreatesIsolatedWorkingCopyForTestProject() throws IOException {
        Path projectsDir = Files.createDirectories(tempDir.resolve("projects"));
        Path workDir = Files.createDirectories(tempDir.resolve("work"));
        Path projectDir = Files.createDirectories(projectsDir.resolve("sample-project"));
        Files.createDirectories(projectDir.resolve("src"));
        Files.writeString(projectDir.resolve("pom.xml"), "<project/>", StandardCharsets.UTF_8);
        Files.writeString(projectDir.resolve("src/config.txt"), "copied", StandardCharsets.UTF_8);

        Path staleWorkingCopy = workDir.resolve(
                "Maven_plugin_testing_harnessTest_copies_project_configuration_sample-project");
        Files.createDirectories(staleWorkingCopy);
        Files.writeString(staleWorkingCopy.resolve("stale.txt"), "stale", StandardCharsets.UTF_8);

        TestResources resources = new TestResources(projectsDir.toString(), workDir.toString());
        AtomicReference<File> basedir = new AtomicReference<>();
        Statement statement = resources.apply(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                basedir.set(resources.getBasedir("sample-project"));
            }
        }, Description.createTestDescription(
                Maven_plugin_testing_harnessTest.class, "copies/project\\configuration"));

        assertThatCode(statement::evaluate).doesNotThrowAnyException();

        assertThat(basedir.get()).isDirectory();
        assertThat(basedir.get()).isEqualTo(staleWorkingCopy.toFile().getCanonicalFile());
        assertThat(new File(basedir.get(), "pom.xml")).hasContent("<project/>");
        assertThat(new File(basedir.get(), "src/config.txt")).hasContent("copied");
        assertThat(new File(basedir.get(), "stale.txt")).doesNotExist();
    }

    @Test
    void configurationExceptionConstructorsPreserveMessagesAndCauses() {
        IOException cause = new IOException("root cause");

        assertThat(new ConfigurationException("message")).hasMessage("message").hasNoCause();
        assertThat(new ConfigurationException(cause)).hasCause(cause);
        assertThat(new ConfigurationException("message", cause)).hasMessage("message").hasCause(cause);
    }

    private static List<String> coordinateIds(Set<Artifact> artifacts) {
        return artifacts.stream()
                .map(Artifact::getId)
                .collect(Collectors.toList());
    }

    private static List<String> scopes(Set<Artifact> artifacts) {
        return artifacts.stream()
                .map(Artifact::getScope)
                .collect(Collectors.toList());
    }

    private static List<String> types(Set<Artifact> artifacts) {
        return artifacts.stream()
                .map(Artifact::getType)
                .collect(Collectors.toList());
    }

    private static List<String> classifiers(Set<Artifact> artifacts) {
        return artifacts.stream()
                .map(Artifact::getClassifier)
                .collect(Collectors.toList());
    }
}
