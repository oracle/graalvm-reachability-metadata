/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_plugin_tools.maven_plugin_annotations;

import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.InstanciationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class Maven_plugin_annotationsTest {
    @Test
    void lifecyclePhaseIdsCoverTheStandardMavenLifecyclesInDeclarationOrder() {
        assertThat(LifecyclePhase.values()).containsExactly(
                LifecyclePhase.VALIDATE,
                LifecyclePhase.INITIALIZE,
                LifecyclePhase.GENERATE_SOURCES,
                LifecyclePhase.PROCESS_SOURCES,
                LifecyclePhase.GENERATE_RESOURCES,
                LifecyclePhase.PROCESS_RESOURCES,
                LifecyclePhase.COMPILE,
                LifecyclePhase.PROCESS_CLASSES,
                LifecyclePhase.GENERATE_TEST_SOURCES,
                LifecyclePhase.PROCESS_TEST_SOURCES,
                LifecyclePhase.GENERATE_TEST_RESOURCES,
                LifecyclePhase.PROCESS_TEST_RESOURCES,
                LifecyclePhase.TEST_COMPILE,
                LifecyclePhase.PROCESS_TEST_CLASSES,
                LifecyclePhase.TEST,
                LifecyclePhase.PREPARE_PACKAGE,
                LifecyclePhase.PACKAGE,
                LifecyclePhase.PRE_INTEGRATION_TEST,
                LifecyclePhase.INTEGRATION_TEST,
                LifecyclePhase.POST_INTEGRATION_TEST,
                LifecyclePhase.VERIFY,
                LifecyclePhase.INSTALL,
                LifecyclePhase.DEPLOY,
                LifecyclePhase.PRE_CLEAN,
                LifecyclePhase.CLEAN,
                LifecyclePhase.POST_CLEAN,
                LifecyclePhase.PRE_SITE,
                LifecyclePhase.SITE,
                LifecyclePhase.POST_SITE,
                LifecyclePhase.SITE_DEPLOY,
                LifecyclePhase.NONE);

        assertThat(LifecyclePhase.values()).extracting(LifecyclePhase::id).containsExactly(
                "validate",
                "initialize",
                "generate-sources",
                "process-sources",
                "generate-resources",
                "process-resources",
                "compile",
                "process-classes",
                "generate-test-sources",
                "process-test-sources",
                "generate-test-resources",
                "process-test-resources",
                "test-compile",
                "process-test-classes",
                "test",
                "prepare-package",
                "package",
                "pre-integration-test",
                "integration-test",
                "post-integration-test",
                "verify",
                "install",
                "deploy",
                "pre-clean",
                "clean",
                "post-clean",
                "pre-site",
                "site",
                "post-site",
                "site-deploy",
                "");
    }

    @Test
    void resolutionScopeIdsModelMavenDependencyScopes() {
        assertThat(ResolutionScope.values()).containsExactly(
                ResolutionScope.NONE,
                ResolutionScope.COMPILE,
                ResolutionScope.COMPILE_PLUS_RUNTIME,
                ResolutionScope.RUNTIME,
                ResolutionScope.RUNTIME_PLUS_SYSTEM,
                ResolutionScope.TEST);

        assertThat(ResolutionScope.values()).extracting(ResolutionScope::id).containsExactly(
                null,
                "compile",
                "compile+runtime",
                "runtime",
                "runtime+system",
                "test");
    }

    @Test
    void instanciationStrategyIdsExposePlexusComponentStrategies() {
        assertThat(InstanciationStrategy.values()).containsExactly(
                InstanciationStrategy.PER_LOOKUP,
                InstanciationStrategy.SINGLETON,
                InstanciationStrategy.KEEP_ALIVE,
                InstanciationStrategy.POOLABLE);

        assertThat(InstanciationStrategy.values()).extracting(InstanciationStrategy::id).containsExactly(
                "per-lookup",
                "singleton",
                "keep-alive",
                "poolable");
    }

    @Test
    void enumLookupsUseJavaEnumNamesRatherThanMavenDescriptorIds() {
        assertThat(LifecyclePhase.valueOf("GENERATE_TEST_RESOURCES"))
                .isSameAs(LifecyclePhase.GENERATE_TEST_RESOURCES);
        assertThat(ResolutionScope.valueOf("COMPILE_PLUS_RUNTIME"))
                .isSameAs(ResolutionScope.COMPILE_PLUS_RUNTIME);
        assertThat(InstanciationStrategy.valueOf("KEEP_ALIVE"))
                .isSameAs(InstanciationStrategy.KEEP_ALIVE);

        assertThatIllegalArgumentException().isThrownBy(() -> LifecyclePhase.valueOf("generate-test-resources"));
        assertThatIllegalArgumentException().isThrownBy(() -> ResolutionScope.valueOf("compile+runtime"));
        assertThatIllegalArgumentException().isThrownBy(() -> InstanciationStrategy.valueOf("keep-alive"));
    }

    @Test
    void valuesMethodsReturnIndependentArrays() {
        LifecyclePhase[] lifecyclePhases = LifecyclePhase.values();
        ResolutionScope[] resolutionScopes = ResolutionScope.values();
        InstanciationStrategy[] instanciationStrategies = InstanciationStrategy.values();

        lifecyclePhases[0] = LifecyclePhase.NONE;
        resolutionScopes[0] = ResolutionScope.TEST;
        instanciationStrategies[0] = InstanciationStrategy.POOLABLE;

        assertThat(LifecyclePhase.values()[0]).isSameAs(LifecyclePhase.VALIDATE);
        assertThat(ResolutionScope.values()[0]).isSameAs(ResolutionScope.NONE);
        assertThat(InstanciationStrategy.values()[0]).isSameAs(InstanciationStrategy.PER_LOOKUP);
    }

    @Test
    void annotatedMojoClassesRemainUsableAsNormalApplicationTypes() {
        FullyAnnotatedMojo mojo = new FullyAnnotatedMojo();
        mojo.outputDirectory = "target/classes";
        mojo.project = "demo-project";

        assertThat(mojo.describe()).isEqualTo("demo-project -> target/classes");
        assertThat(new MinimalMojo().isConfigured()).isFalse();
    }

    @Mojo(
            name = "full-metadata",
            defaultPhase = LifecyclePhase.VERIFY,
            requiresDependencyResolution = ResolutionScope.TEST,
            requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME,
            instantiationStrategy = InstanciationStrategy.SINGLETON,
            executionStrategy = "always",
            requiresProject = false,
            requiresReports = true,
            aggregator = true,
            requiresDirectInvocation = true,
            requiresOnline = true,
            inheritByDefault = false,
            configurator = "basic",
            threadSafe = true)
    @Execute(phase = LifecyclePhase.COMPILE, goal = "compile", lifecycle = "default")
    private static final class FullyAnnotatedMojo {
        @Parameter(
                alias = "output",
                property = "example.outputDirectory",
                defaultValue = "${project.build.outputDirectory}",
                required = true,
                readonly = true)
        private String outputDirectory;

        @Component(role = CharSequence.class, hint = "project-name")
        private String project;

        private String describe() {
            return project + " -> " + outputDirectory;
        }
    }

    @Mojo(name = "minimal-metadata")
    private static final class MinimalMojo {
        @Parameter
        private String optionalParameter;

        @Component
        private Runnable callback;

        private boolean isConfigured() {
            return optionalParameter != null || callback != null;
        }
    }
}
