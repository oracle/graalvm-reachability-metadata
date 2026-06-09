/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_plugin_testing.maven_plugin_testing_harness;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.testing.ArtifactStubFactory;
import org.apache.maven.plugin.testing.MojoParameters;
import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.plugin.testing.ResolverExpressionEvaluatorStub;
import org.apache.maven.plugin.testing.SilentLog;
import org.apache.maven.plugin.testing.WithoutMojo;
import org.apache.maven.plugin.testing.resources.TestResources;
import org.apache.maven.plugin.testing.stubs.ArtifactStub;
import org.apache.maven.plugin.testing.stubs.DefaultArtifactHandlerStub;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;
import org.apache.maven.plugin.testing.stubs.StubArtifactRepository;
import org.apache.maven.plugin.testing.stubs.StubArtifactResolver;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.configurator.ComponentConfigurator;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class Maven_plugin_testing_harnessTest {
    @TempDir
    Path tempDir;

    @Test
    void artifactStubFactoryCreatesArtifactsAndRepositoryFiles() throws Exception {
        File repositoryRoot = tempDir.resolve("repository").toFile();
        ArtifactStubFactory factory = new ArtifactStubFactory(repositoryRoot, true);

        Artifact artifact = factory.createArtifact(
                "com.example", "demo-plugin", "1.0", Artifact.SCOPE_TEST, "test-jar", "tests");

        assertThat(artifact.getGroupId()).isEqualTo("com.example");
        assertThat(artifact.getArtifactId()).isEqualTo("demo-plugin");
        assertThat(artifact.getScope()).isEqualTo(Artifact.SCOPE_TEST);
        assertThat(artifact.getType()).isEqualTo("test-jar");
        assertThat(artifact.getClassifier()).isEqualTo("tests");
        assertThat(artifact.isRelease()).isTrue();
        assertThat(artifact.getFile()).isFile();
        assertThat(artifact.getFile().getName()).isEqualTo("demo-plugin-1.0-tests.jar");
        assertThat(ArtifactStubFactory.getFormattedFileName(artifact, true)).isEqualTo("demo-plugin-tests.jar");
        assertThat(ArtifactStubFactory.getUnpackableFileName(artifact))
                .isEqualTo("com.example-demo-plugin-1.0-tests-test-jar.txt");

        Set<Artifact> scopedArtifacts = factory.getScopedArtifacts();
        assertThat(scopedArtifacts).hasSize(5);
        assertThat(scopedArtifacts.stream().map(Artifact::getScope).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder(
                        Artifact.SCOPE_COMPILE,
                        Artifact.SCOPE_PROVIDED,
                        Artifact.SCOPE_RUNTIME,
                        Artifact.SCOPE_SYSTEM,
                        Artifact.SCOPE_TEST);

        assertThat(factory.getTypedArchiveArtifacts().stream().map(Artifact::getType).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("jar", "rar", "war", "zip");
        assertThat(factory.getClassifiedArtifacts().stream().map(Artifact::getClassifier).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("one", "two", "three", "four");
    }

    @Test
    void mavenProjectAndArtifactStubsBehaveLikeMutableMavenObjects() throws Exception {
        Model model = new Model();
        model.setModelVersion("4.0.0");
        Build build = new Build();
        build.setDirectory("target/custom");

        MavenProjectStub project = new MavenProjectStub(model);
        project.setGroupId("com.example");
        project.setArtifactId("sample-plugin");
        project.setVersion("1.0-SNAPSHOT");
        project.setPackaging("maven-plugin");
        project.setName("Sample Plugin");
        project.setBuild(build);
        project.addCompileSourceRoot("src/main/java");
        project.addTestCompileSourceRoot("src/test/java");
        project.addScriptSourceRoot("src/main/scripts");
        project.setRuntimeClasspathElements(Arrays.asList("target/classes", "target/dependency/library.jar"));
        project.setExecutionRoot(true);

        ArtifactStub projectArtifact = new ArtifactStub();
        projectArtifact.setGroupId("com.example");
        projectArtifact.setArtifactId("sample-plugin");
        projectArtifact.setVersion("1.0-SNAPSHOT");
        projectArtifact.setType("maven-plugin");
        projectArtifact.setScope(Artifact.SCOPE_TEST);
        project.setArtifact(projectArtifact);
        project.addAttachedArtifact(projectArtifact);

        MavenProjectStub parent = new MavenProjectStub();
        parent.setGroupId("com.example.parent");
        project.setParent(parent);

        assertThat(project.getModel()).isSameAs(model);
        assertThat(project.getGroupId()).isEqualTo("com.example");
        assertThat(project.getArtifactId()).isEqualTo("sample-plugin");
        assertThat(project.getVersion()).isEqualTo("1.0-SNAPSHOT");
        assertThat(project.getPackaging()).isEqualTo("maven-plugin");
        assertThat(project.getBuild().getDirectory()).isEqualTo("target/custom");
        assertThat(project.getCompileClasspathElements()).containsExactly("src/main/java");
        assertThat(project.getTestCompileSourceRoots()).containsExactly("src/test/java");
        assertThat(project.getScriptSourceRoots()).containsExactly("src/main/scripts");
        assertThat(project.getRuntimeClasspathElements())
                .containsExactly("target/classes", "target/dependency/library.jar");
        assertThat(project.isExecutionRoot()).isTrue();
        assertThat(project.hasParent()).isTrue();
        assertThat(project.getParent().getGroupId()).isEqualTo("com.example.parent");
        assertThat(project.getArtifact()).isSameAs(projectArtifact);
        assertThat(project.getAttachedArtifacts()).containsExactly(projectArtifact);

        assertThat(projectArtifact.isSnapshot()).isTrue();
        assertThat(projectArtifact.isRelease()).isFalse();
        assertThat(projectArtifact.hasClassifier()).isFalse();
        assertThat(projectArtifact.getDependencyConflictId()).isEqualTo("com.example:sample-plugin:maven-plugin:null");
        assertThat(projectArtifact.toString()).isEqualTo("com.example:sample-plugin:maven-plugin:1.0-SNAPSHOT:test");
    }

    @Test
    void artifactTestDoublesResolveArtifactsAndExposeHandlerMetadata() throws Exception {
        DefaultArtifactHandlerStub handler = new DefaultArtifactHandlerStub("test-jar", "tests");

        assertThat(handler.getType()).isEqualTo("test-jar");
        assertThat(handler.getClassifier()).isEqualTo("tests");
        assertThat(handler.getExtension()).isEqualTo("jar");
        assertThat(handler.getPackaging()).isEqualTo("test-jar");
        assertThat(handler.getDirectory()).isEqualTo("test-jars");
        assertThat(handler.getLanguage()).isEqualTo("none");
        assertThat(handler.isIncludesDependencies()).isFalse();
        assertThat(handler.isAddedToClasspath()).isFalse();

        handler.setExtension("zip");
        handler.setPackaging("distribution");
        handler.setDirectory("distributions");
        handler.setLanguage("java");
        handler.setIncludesDependencies(true);
        handler.setAddedToClasspath(true);

        assertThat(handler.getExtension()).isEqualTo("zip");
        assertThat(handler.getPackaging()).isEqualTo("distribution");
        assertThat(handler.getDirectory()).isEqualTo("distributions");
        assertThat(handler.getLanguage()).isEqualTo("java");
        assertThat(handler.isIncludesDependencies()).isTrue();
        assertThat(handler.isAddedToClasspath()).isTrue();

        File repositoryRoot = tempDir.resolve("resolver-repository").toFile();
        ArtifactStubFactory factory = new ArtifactStubFactory(repositoryRoot, false);
        Artifact artifact = factory.createArtifact(
                "com.example", "resolver-target", "1.0", Artifact.SCOPE_COMPILE, "jar", "");
        StubArtifactRepository repository = new StubArtifactRepository(repositoryRoot.getAbsolutePath());
        StubArtifactResolver resolver = new StubArtifactResolver(factory, false, false);

        assertThat(artifact.getFile()).isNull();
        assertThat(repository.getBasedir()).isEqualTo(repositoryRoot.getAbsolutePath());
        assertThat(repository.pathOf(artifact)).isEqualTo(artifact.getId());
        assertThat(repository.findVersions(artifact)).isEmpty();
        assertThat(repository.getMirroredRepositories()).isEmpty();

        resolver.resolve(artifact, Collections.emptyList(), repository);

        assertThat(artifact.getFile()).isFile();
        assertThat(artifact.getFile().getParentFile()).isEqualTo(factory.getWorkingDir());
        assertThat(artifact.getFile().getName()).isEqualTo("resolver-target-1.0.jar");
    }

    @Test
    void mojoRuleExposesHarnessFieldHelpersAndWithoutMojoBypass() throws Throwable {
        MojoRule rule = new MojoRule();
        RecordingMojo mojo = new RecordingMojo();

        rule.setVariableValueToObject(mojo, "message", "configured by harness");
        rule.setVariableValueToObject(mojo, "enabled", Boolean.TRUE);

        assertThat(rule.getVariableValueFromObject(mojo, "message")).isEqualTo("configured by harness");
        Map<String, Object> variables = rule.getVariablesAndValuesFromObject(mojo);
        assertThat(variables).containsEntry("message", "configured by harness");
        assertThat(variables).containsEntry("enabled", Boolean.TRUE);

        AtomicBoolean evaluated = new AtomicBoolean();
        Statement base = new Statement() {
            @Override
            public void evaluate() {
                evaluated.set(true);
            }
        };
        Statement statement = rule.apply(
                base,
                Description.createTestDescription(
                        Maven_plugin_testing_harnessTest.class, "withoutMojo", withoutMojoAnnotation()));

        statement.evaluate();

        assertThat(evaluated).isTrue();
    }

    @Test
    void mojoRuleCanInitializePlexusContainerForMavenHarnessLookups() throws Exception {
        MojoRule rule = new MojoRule();
        PlexusContainer container = null;
        try {
            rule.setupContainer();
            container = rule.getContainer();

            System.out.println("plexus components.xml resources="
                    + Collections.list(getClass().getClassLoader().getResources("META-INF/plexus/components.xml")));
            System.out.println("sisu javax.inject.Named resources="
                    + Collections.list(getClass().getClassLoader().getResources("META-INF/sisu/javax.inject.Named")));
            System.out.println("has ComponentConfigurator=" + container.hasComponent(ComponentConfigurator.class));
            System.out.println("has basic ComponentConfigurator="
                    + container.hasComponent(ComponentConfigurator.class, "basic"));
            System.out.println("ComponentConfigurator keys=" + container.lookupMap(ComponentConfigurator.class).keySet());
            try {
                System.out.println("basic configurator class="
                        + container.getContainerRealm().loadClass(
                                "org.codehaus.plexus.component.configurator.BasicComponentConfigurator"));
            } catch (Throwable throwable) {
                System.out.println("basic configurator load failed=" + throwable);
            }
            try {
                System.out.println("map-oriented configurator class="
                        + container.getContainerRealm().loadClass(
                                "org.codehaus.plexus.component.configurator.MapOrientedComponentConfigurator"));
            } catch (Throwable throwable) {
                System.out.println("map-oriented configurator load failed=" + throwable);
            }

            assertThat(container).isNotNull();
            assertThat(rule.lookup(ComponentConfigurator.class)).isNotNull();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            if (container != null) {
                container.dispose();
            }
        }
    }

    @Test
    void resolverExpressionEvaluatorInterpolatesHarnessExpressions() throws Exception {
        ResolverExpressionEvaluatorStub evaluator = new ResolverExpressionEvaluatorStub();

        Object basedirExpression = evaluator.evaluate("${basedir}");
        assertThat(basedirExpression).isInstanceOf(String.class);
        String basedir = (String) basedirExpression;

        assertThat(basedir).isNotEmpty();
        assertThat(evaluator.evaluate("project=${basedir}/src"))
                .isEqualTo("project=" + basedir + "/src");
        assertThat(evaluator.evaluate("$${basedir}"))
                .isEqualTo("${basedir}");
        assertThat(evaluator.evaluate("cost $$1"))
                .isEqualTo("cost $1");

        Object localRepositoryExpression = evaluator.evaluate("${localRepository}");
        assertThat(localRepositoryExpression).isInstanceOf(ArtifactRepository.class);
        ArtifactRepository localRepository = (ArtifactRepository) localRepositoryExpression;
        assertThat(localRepository.getId()).isEqualTo("localRepository");
        assertThat(localRepository.getUrl()).startsWith("file://");
        assertThat(localRepository.getBasedir()).endsWith("target" + File.separator + "local-repo");

        File relativePath = new File("target/generated-fixtures");
        assertThat(evaluator.alignToBaseDirectory(relativePath).getAbsolutePath())
                .isEqualTo(new File(basedir, relativePath.getPath()).getAbsolutePath());
        File absolutePath = tempDir.resolve("absolute-fixture").toFile();
        assertThat(evaluator.alignToBaseDirectory(absolutePath)).isSameAs(absolutePath);
    }

    @Test
    void mojoParametersCreateConfigurationNodes() {
        Xpp3Dom parameter = MojoParameters.newParameter("message", "hello from configuration");
        Xpp3Dom configuration = new Xpp3Dom("configuration");
        configuration.addChild(parameter);

        assertThat(parameter.getName()).isEqualTo("message");
        assertThat(parameter.getValue()).isEqualTo("hello from configuration");
        assertThat(configuration.getChild("message").getValue()).isEqualTo("hello from configuration");
    }

    @Test
    void testResourcesCopiesAndAssertsProjectFixtures() throws Throwable {
        Path projectsDir = tempDir.resolve("projects");
        Path sourceProject = projectsDir.resolve("sample-project");
        Path workDir = tempDir.resolve("work");
        Files.createDirectories(sourceProject);
        Files.writeString(sourceProject.resolve("input.txt"), "fixture contents\n");

        TestResources resources = new TestResources(projectsDir.toString(), workDir.toString());
        Statement useResources = new Statement() {
            @Override
            public void evaluate() throws Throwable {
                File basedir = resources.getBasedir("sample-project");

                TestResources.cp(basedir, "input.txt", "copy.txt");
                TestResources.assertFileContents(basedir, "input.txt", "copy.txt");
                TestResources.create(basedir, "generated/output.txt");
                Files.writeString(new File(basedir, "generated/output.txt").toPath(), "generated contents\n");
                TestResources.assertDirectoryContents(
                        basedir, "copy.txt", "generated/", "generated/output.txt", "input.txt");
                TestResources.rm(basedir, "copy.txt");
                TestResources.assertDirectoryContents(basedir, "generated/", "generated/output.txt", "input.txt");
            }
        };

        resources.apply(
                useResources,
                Description.createTestDescription(Maven_plugin_testing_harnessTest.class, "copiesFixture"))
                .evaluate();
    }

    @Test
    void silentLogImplementsMavenAndPlexusLoggingAsNoOpLogger() {
        SilentLog log = new SilentLog();
        Throwable failure = new IllegalStateException("ignored");

        log.debug(new StringBuilder("debug"));
        log.debug(new StringBuilder("debug"), failure);
        log.debug(failure);
        log.info(new StringBuilder("info"));
        log.info(new StringBuilder("info"), failure);
        log.info(failure);
        log.warn(new StringBuilder("warn"));
        log.warn(new StringBuilder("warn"), failure);
        log.warn(failure);
        log.error(new StringBuilder("error"));
        log.error(new StringBuilder("error"), failure);
        log.error(failure);
        log.debug("plexus debug", failure);
        log.info("plexus info", failure);
        log.warn("plexus warn", failure);
        log.error("plexus error", failure);
        log.fatalError("plexus fatal", failure);
        log.setThreshold(Logger.LEVEL_DEBUG);

        assertThat(log.isDebugEnabled()).isFalse();
        assertThat(log.isInfoEnabled()).isFalse();
        assertThat(log.isWarnEnabled()).isFalse();
        assertThat(log.isErrorEnabled()).isFalse();
        assertThat(log.isFatalErrorEnabled()).isFalse();
        assertThat(log.getChildLogger("child")).isNull();
        assertThat(log.getThreshold()).isZero();
        assertThat(log.getName()).isNull();
    }

    private static WithoutMojo withoutMojoAnnotation() {
        return new WithoutMojo() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return WithoutMojo.class;
            }
        };
    }

    private static final class RecordingMojo extends AbstractMojo {
        private String message;

        private boolean enabled;

        @Override
        public void execute() throws MojoExecutionException {
            if (enabled && message == null) {
                throw new MojoExecutionException("message is required when enabled");
            }
        }
    }
}
