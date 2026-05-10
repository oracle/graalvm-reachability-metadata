/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_plugin_tools.maven_plugin_annotations;

import java.lang.annotation.Annotation;

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

    @Test
    void annotationInterfacesExposeTypedPluginDescriptorMetadata() {
        Mojo mojo = new DescriptorMojoMetadata();
        Execute execute = new DescriptorExecutionMetadata();
        Parameter parameter = new DescriptorParameterMetadata();
        Component component = new DescriptorComponentMetadata();

        assertThat(mojo.annotationType()).isEqualTo(Mojo.class);
        assertThat(mojo.name()).isEqualTo("compile-assets");
        assertThat(mojo.defaultPhase()).isSameAs(LifecyclePhase.PROCESS_RESOURCES);
        assertThat(mojo.requiresDependencyResolution()).isSameAs(ResolutionScope.RUNTIME);
        assertThat(mojo.requiresDependencyCollection()).isSameAs(ResolutionScope.COMPILE);
        assertThat(mojo.instantiationStrategy()).isSameAs(InstanciationStrategy.KEEP_ALIVE);
        assertThat(mojo.executionStrategy()).isEqualTo("always");
        assertThat(mojo.requiresProject()).isTrue();
        assertThat(mojo.requiresReports()).isFalse();
        assertThat(mojo.aggregator()).isFalse();
        assertThat(mojo.requiresDirectInvocation()).isFalse();
        assertThat(mojo.requiresOnline()).isTrue();
        assertThat(mojo.inheritByDefault()).isTrue();
        assertThat(mojo.configurator()).isEqualTo("map-oriented");
        assertThat(mojo.threadSafe()).isTrue();

        assertThat(execute.annotationType()).isEqualTo(Execute.class);
        assertThat(execute.phase()).isSameAs(LifecyclePhase.TEST_COMPILE);
        assertThat(execute.goal()).isEqualTo("generate-test-stubs");
        assertThat(execute.lifecycle()).isEqualTo("stub-generation");

        assertThat(parameter.annotationType()).isEqualTo(Parameter.class);
        assertThat(parameter.alias()).isEqualTo("assetOutput");
        assertThat(parameter.property()).isEqualTo("assets.outputDirectory");
        assertThat(parameter.defaultValue()).isEqualTo("${project.build.directory}/assets");
        assertThat(parameter.required()).isTrue();
        assertThat(parameter.readonly()).isFalse();

        assertThat(component.annotationType()).isEqualTo(Component.class);
        assertThat(component.role()).isEqualTo(Runnable.class);
        assertThat(component.hint()).isEqualTo("asset-compiler");
    }

    @Test
    void annotationInterfacesRepresentMinimalDescriptorDefaults() {
        Mojo mojo = new MinimalDescriptorMojoMetadata();
        Execute execute = new MinimalDescriptorExecutionMetadata();
        Parameter parameter = new MinimalDescriptorParameterMetadata();
        Component component = new MinimalDescriptorComponentMetadata();

        assertThat(mojo.annotationType()).isEqualTo(Mojo.class);
        assertThat(mojo.name()).isEqualTo("generate-help");
        assertThat(mojo.defaultPhase()).isSameAs(LifecyclePhase.NONE);
        assertThat(mojo.requiresDependencyResolution()).isSameAs(ResolutionScope.NONE);
        assertThat(mojo.requiresDependencyCollection()).isSameAs(ResolutionScope.NONE);
        assertThat(mojo.instantiationStrategy()).isSameAs(InstanciationStrategy.PER_LOOKUP);
        assertThat(mojo.executionStrategy()).isEqualTo("once-per-session");
        assertThat(mojo.requiresProject()).isTrue();
        assertThat(mojo.requiresReports()).isFalse();
        assertThat(mojo.aggregator()).isFalse();
        assertThat(mojo.requiresDirectInvocation()).isFalse();
        assertThat(mojo.requiresOnline()).isFalse();
        assertThat(mojo.inheritByDefault()).isTrue();
        assertThat(mojo.configurator()).isEmpty();
        assertThat(mojo.threadSafe()).isFalse();

        assertThat(execute.annotationType()).isEqualTo(Execute.class);
        assertThat(execute.phase()).isSameAs(LifecyclePhase.NONE);
        assertThat(execute.goal()).isEmpty();
        assertThat(execute.lifecycle()).isEmpty();

        assertThat(parameter.annotationType()).isEqualTo(Parameter.class);
        assertThat(parameter.alias()).isEmpty();
        assertThat(parameter.property()).isEmpty();
        assertThat(parameter.defaultValue()).isEmpty();
        assertThat(parameter.required()).isFalse();
        assertThat(parameter.readonly()).isFalse();

        assertThat(component.annotationType()).isEqualTo(Component.class);
        assertThat(component.role()).isEqualTo(Object.class);
        assertThat(component.hint()).isEmpty();
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

    private static final class MinimalDescriptorMojoMetadata implements Mojo {
        @Override
        public String name() {
            return "generate-help";
        }

        @Override
        public LifecyclePhase defaultPhase() {
            return LifecyclePhase.NONE;
        }

        @Override
        public ResolutionScope requiresDependencyResolution() {
            return ResolutionScope.NONE;
        }

        @Override
        public ResolutionScope requiresDependencyCollection() {
            return ResolutionScope.NONE;
        }

        @Override
        public InstanciationStrategy instantiationStrategy() {
            return InstanciationStrategy.PER_LOOKUP;
        }

        @Override
        public String executionStrategy() {
            return "once-per-session";
        }

        @Override
        public boolean requiresProject() {
            return true;
        }

        @Override
        public boolean requiresReports() {
            return false;
        }

        @Override
        public boolean aggregator() {
            return false;
        }

        @Override
        public boolean requiresDirectInvocation() {
            return false;
        }

        @Override
        public boolean requiresOnline() {
            return false;
        }

        @Override
        public boolean inheritByDefault() {
            return true;
        }

        @Override
        public String configurator() {
            return "";
        }

        @Override
        public boolean threadSafe() {
            return false;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Mojo.class;
        }
    }

    private static final class MinimalDescriptorExecutionMetadata implements Execute {
        @Override
        public LifecyclePhase phase() {
            return LifecyclePhase.NONE;
        }

        @Override
        public String goal() {
            return "";
        }

        @Override
        public String lifecycle() {
            return "";
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Execute.class;
        }
    }

    private static final class MinimalDescriptorParameterMetadata implements Parameter {
        @Override
        public String alias() {
            return "";
        }

        @Override
        public String property() {
            return "";
        }

        @Override
        public String defaultValue() {
            return "";
        }

        @Override
        public boolean required() {
            return false;
        }

        @Override
        public boolean readonly() {
            return false;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Parameter.class;
        }
    }

    private static final class MinimalDescriptorComponentMetadata implements Component {
        @Override
        public Class<?> role() {
            return Object.class;
        }

        @Override
        public String hint() {
            return "";
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Component.class;
        }
    }

    private static final class DescriptorMojoMetadata implements Mojo {
        @Override
        public String name() {
            return "compile-assets";
        }

        @Override
        public LifecyclePhase defaultPhase() {
            return LifecyclePhase.PROCESS_RESOURCES;
        }

        @Override
        public ResolutionScope requiresDependencyResolution() {
            return ResolutionScope.RUNTIME;
        }

        @Override
        public ResolutionScope requiresDependencyCollection() {
            return ResolutionScope.COMPILE;
        }

        @Override
        public InstanciationStrategy instantiationStrategy() {
            return InstanciationStrategy.KEEP_ALIVE;
        }

        @Override
        public String executionStrategy() {
            return "always";
        }

        @Override
        public boolean requiresProject() {
            return true;
        }

        @Override
        public boolean requiresReports() {
            return false;
        }

        @Override
        public boolean aggregator() {
            return false;
        }

        @Override
        public boolean requiresDirectInvocation() {
            return false;
        }

        @Override
        public boolean requiresOnline() {
            return true;
        }

        @Override
        public boolean inheritByDefault() {
            return true;
        }

        @Override
        public String configurator() {
            return "map-oriented";
        }

        @Override
        public boolean threadSafe() {
            return true;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Mojo.class;
        }
    }

    private static final class DescriptorExecutionMetadata implements Execute {
        @Override
        public LifecyclePhase phase() {
            return LifecyclePhase.TEST_COMPILE;
        }

        @Override
        public String goal() {
            return "generate-test-stubs";
        }

        @Override
        public String lifecycle() {
            return "stub-generation";
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Execute.class;
        }
    }

    private static final class DescriptorParameterMetadata implements Parameter {
        @Override
        public String alias() {
            return "assetOutput";
        }

        @Override
        public String property() {
            return "assets.outputDirectory";
        }

        @Override
        public String defaultValue() {
            return "${project.build.directory}/assets";
        }

        @Override
        public boolean required() {
            return true;
        }

        @Override
        public boolean readonly() {
            return false;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Parameter.class;
        }
    }

    private static final class DescriptorComponentMetadata implements Component {
        @Override
        public Class<?> role() {
            return Runnable.class;
        }

        @Override
        public String hint() {
            return "asset-compiler";
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Component.class;
        }
    }
}
