/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_plugin_tools.maven_plugin_annotations;

import java.util.Arrays;
import java.util.List;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.InstanciationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Maven_plugin_annotationsTest {
    @Test
    void lifecyclePhaseIdsMatchMavenLifecycleDescriptorTokens() {
        List<String> phaseIds = Arrays.stream(LifecyclePhase.values())
                .map(LifecyclePhase::id)
                .toList();

        assertThat(phaseIds).containsExactly(
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
    void lifecyclePhaseEnumNamesResolveToTheSameConstantsAsDirectReferences() {
        assertThat(LifecyclePhase.valueOf("VALIDATE")).isSameAs(LifecyclePhase.VALIDATE);
        assertThat(LifecyclePhase.valueOf("TEST_COMPILE")).isSameAs(LifecyclePhase.TEST_COMPILE);
        assertThat(LifecyclePhase.valueOf("PRE_INTEGRATION_TEST"))
                .isSameAs(LifecyclePhase.PRE_INTEGRATION_TEST);
        assertThat(LifecyclePhase.valueOf("SITE_DEPLOY")).isSameAs(LifecyclePhase.SITE_DEPLOY);
        assertThat(LifecyclePhase.valueOf("NONE")).isSameAs(LifecyclePhase.NONE);
    }

    @Test
    void resolutionScopeIdsMatchMavenPluginDescriptorTokens() {
        List<String> scopeIds = Arrays.stream(ResolutionScope.values())
                .map(ResolutionScope::id)
                .toList();

        assertThat(scopeIds).containsExactly(
                null,
                "compile",
                "compile+runtime",
                "runtime",
                "runtime+system",
                "test");
        assertThat(ResolutionScope.valueOf("NONE")).isSameAs(ResolutionScope.NONE);
        assertThat(ResolutionScope.valueOf("COMPILE_PLUS_RUNTIME"))
                .isSameAs(ResolutionScope.COMPILE_PLUS_RUNTIME);
        assertThat(ResolutionScope.valueOf("RUNTIME_PLUS_SYSTEM"))
                .isSameAs(ResolutionScope.RUNTIME_PLUS_SYSTEM);
    }

    @Test
    void instanciationStrategyIdsMatchMavenPluginDescriptorTokens() {
        List<String> strategyIds = Arrays.stream(InstanciationStrategy.values())
                .map(InstanciationStrategy::id)
                .toList();

        assertThat(strategyIds).containsExactly(
                "per-lookup",
                "singleton",
                "keep-alive",
                "poolable");
        assertThat(InstanciationStrategy.valueOf("PER_LOOKUP"))
                .isSameAs(InstanciationStrategy.PER_LOOKUP);
        assertThat(InstanciationStrategy.valueOf("SINGLETON"))
                .isSameAs(InstanciationStrategy.SINGLETON);
        assertThat(InstanciationStrategy.valueOf("KEEP_ALIVE"))
                .isSameAs(InstanciationStrategy.KEEP_ALIVE);
        assertThat(InstanciationStrategy.valueOf("POOLABLE"))
                .isSameAs(InstanciationStrategy.POOLABLE);
    }

    @Test
    void annotationTypesCanDescribeMojoClassesUsingRequiredAndDefaultedElements() {
        MinimalMojo minimalMojo = new MinimalMojo();
        MinimalLifecycleExecution minimalLifecycleExecution = new MinimalLifecycleExecution();

        minimalMojo.parameter = "configured";
        minimalMojo.component = () -> "service";

        assertThat(minimalMojo.parameter).isEqualTo("configured");
        assertThat(minimalMojo.component.name()).isEqualTo("service");
        assertThat(minimalLifecycleExecution).isNotNull();
    }

    @Test
    void annotationTypesCanDescribeMojoClassesUsingEveryExplicitElement() {
        CompleteMojo completeMojo = new CompleteMojo();

        completeMojo.inputDirectory = "src/main/plugin";
        completeMojo.helper = () -> "helper";

        assertThat(completeMojo.inputDirectory).isEqualTo("src/main/plugin");
        assertThat(completeMojo.helper.name()).isEqualTo("helper");
        assertThat(completeMojo.execute()).isEqualTo("complete");
    }

    @Mojo(name = "minimal")
    private static final class MinimalMojo {
        @Parameter
        private String parameter;

        @Component
        private NamedService component;
    }

    @Execute
    private static final class MinimalLifecycleExecution {
    }

    @Mojo(
            name = "complete",
            defaultPhase = LifecyclePhase.PROCESS_CLASSES,
            requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
            requiresDependencyCollection = ResolutionScope.TEST,
            instantiationStrategy = InstanciationStrategy.SINGLETON,
            executionStrategy = "always",
            requiresProject = false,
            requiresReports = true,
            aggregator = true,
            requiresDirectInvocation = true,
            requiresOnline = true,
            inheritByDefault = false,
            configurator = "include-project-dependencies",
            threadSafe = true)
    @Execute(phase = LifecyclePhase.GENERATE_SOURCES, goal = "generate", lifecycle = "default")
    private static final class CompleteMojo {
        @Parameter(
                alias = "input",
                property = "plugin.inputDirectory",
                defaultValue = "${project.basedir}",
                required = true,
                readonly = true)
        private String inputDirectory;

        @Component(role = NamedService.class, hint = "default")
        private NamedService helper;

        private String execute() {
            return "complete";
        }
    }

    private interface NamedService {
        String name();
    }
}
