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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class Maven_plugin_annotationsTest {
    @Test
    void lifecyclePhasesExposeMavenLifecycleIdsInDeclarationOrder() {
        assertThat(LifecyclePhase.values())
                .extracting(LifecyclePhase::id)
                .containsExactly(
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
        assertThat(LifecyclePhase.valueOf("GENERATE_SOURCES")).isSameAs(LifecyclePhase.GENERATE_SOURCES);
        assertThat(LifecyclePhase.valueOf("PRE_INTEGRATION_TEST")).isSameAs(LifecyclePhase.PRE_INTEGRATION_TEST);
        assertThat(LifecyclePhase.NONE.id()).isEmpty();
    }

    @Test
    void resolutionScopesExposeMavenArtifactScopeIds() {
        assertThat(ResolutionScope.values())
                .extracting(ResolutionScope::id)
                .containsExactly(
                        null,
                        "compile",
                        "compile+runtime",
                        "runtime",
                        "runtime+system",
                        "test");
        assertThat(ResolutionScope.valueOf("COMPILE_PLUS_RUNTIME"))
                .isSameAs(ResolutionScope.COMPILE_PLUS_RUNTIME);
        assertThat(ResolutionScope.valueOf("RUNTIME_PLUS_SYSTEM"))
                .isSameAs(ResolutionScope.RUNTIME_PLUS_SYSTEM);
        assertThat(ResolutionScope.NONE.id()).isNull();
    }

    @Test
    void instanciationStrategiesExposePlexusComponentInstantiationIds() {
        assertThat(InstanciationStrategy.values())
                .extracting(InstanciationStrategy::id)
                .containsExactly("per-lookup", "singleton", "keep-alive", "poolable");
        assertThat(InstanciationStrategy.valueOf("PER_LOOKUP")).isSameAs(InstanciationStrategy.PER_LOOKUP);
        assertThat(InstanciationStrategy.valueOf("SINGLETON")).isSameAs(InstanciationStrategy.SINGLETON);
    }

    @Test
    void enumValueOfUsesJavaConstantNamesRatherThanDescriptorIds() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> LifecyclePhase.valueOf("generate-sources"));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ResolutionScope.valueOf("compile+runtime"));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> InstanciationStrategy.valueOf("per-lookup"));
    }

    @Test
    void annotationTypesCanBePlacedOnMojoClassesAndFields() {
        AnnotatedMojo mojo = new AnnotatedMojo();

        assertThat(mojo.descriptorInput()).isEqualTo("target/generated-sources/plugin");
    }

    @Test
    void minimalAnnotationDeclarationsCanOmitOptionalDescriptorAttributes() {
        MinimalAnnotatedMojo mojo = new MinimalAnnotatedMojo();

        assertThat(mojo.parameterValue()).isEqualTo("default-parameter");
    }

    @Test
    void mojoAnnotationValuesExposePluginDescriptorConfiguration() {
        Mojo mojo = new ConfiguredMojoAnnotation();

        assertThat(mojo.annotationType()).isEqualTo(Mojo.class);
        assertThat(mojo.name()).isEqualTo("descriptor");
        assertThat(mojo.defaultPhase()).isSameAs(LifecyclePhase.VERIFY);
        assertThat(mojo.requiresDependencyResolution()).isSameAs(ResolutionScope.TEST);
        assertThat(mojo.requiresDependencyCollection()).isSameAs(ResolutionScope.RUNTIME);
        assertThat(mojo.instantiationStrategy()).isSameAs(InstanciationStrategy.SINGLETON);
        assertThat(mojo.executionStrategy()).isEqualTo("always");
        assertThat(mojo.requiresProject()).isFalse();
        assertThat(mojo.requiresReports()).isTrue();
        assertThat(mojo.aggregator()).isTrue();
        assertThat(mojo.requiresDirectInvocation()).isTrue();
        assertThat(mojo.requiresOnline()).isTrue();
        assertThat(mojo.inheritByDefault()).isFalse();
        assertThat(mojo.configurator()).isEqualTo("custom-configurator");
        assertThat(mojo.threadSafe()).isTrue();
    }

    @Test
    void executeParameterAndComponentAnnotationsExposeConfiguredValues() {
        Execute execute = new ConfiguredExecuteAnnotation();
        Parameter parameter = new ConfiguredParameterAnnotation();
        Component component = new ConfiguredComponentAnnotation();

        assertThat(execute.annotationType()).isEqualTo(Execute.class);
        assertThat(execute.phase()).isSameAs(LifecyclePhase.GENERATE_SOURCES);
        assertThat(execute.goal()).isEqualTo("generate-descriptor");
        assertThat(execute.lifecycle()).isEqualTo("site");

        assertThat(parameter.annotationType()).isEqualTo(Parameter.class);
        assertThat(parameter.alias()).isEqualTo("output");
        assertThat(parameter.property()).isEqualTo("plugin.outputDirectory");
        assertThat(parameter.defaultValue()).isEqualTo("${project.build.directory}/generated-sources/plugin");
        assertThat(parameter.required()).isTrue();
        assertThat(parameter.readonly()).isTrue();

        assertThat(component.annotationType()).isEqualTo(Component.class);
        assertThat(component.role()).isEqualTo(Runnable.class);
        assertThat(component.hint()).isEqualTo("descriptor-generator");
    }

    @SuppressWarnings("unused")
    @Mojo(
            name = "annotated",
            defaultPhase = LifecyclePhase.GENERATE_SOURCES,
            requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
            requiresDependencyCollection = ResolutionScope.TEST,
            instantiationStrategy = InstanciationStrategy.PER_LOOKUP,
            executionStrategy = "once-per-session",
            requiresProject = true,
            requiresReports = false,
            aggregator = true,
            requiresDirectInvocation = false,
            requiresOnline = false,
            inheritByDefault = true,
            configurator = "basic",
            threadSafe = true)
    @Execute(phase = LifecyclePhase.PROCESS_CLASSES, goal = "help", lifecycle = "default")
    private static final class AnnotatedMojo {
        @Parameter(
                alias = "generatedOutput",
                property = "plugin.generatedOutput",
                defaultValue = "${project.build.directory}/generated-sources/plugin",
                required = true,
                readonly = true)
        private final String outputDirectory = "target/generated-sources/plugin";

        @Component(role = Runnable.class, hint = "descriptor-generator")
        private final Runnable descriptorGenerator = () -> { };

        private String descriptorInput() {
            descriptorGenerator.run();
            return outputDirectory;
        }
    }

    @SuppressWarnings("unused")
    @Mojo(name = "minimal")
    @Execute
    private static final class MinimalAnnotatedMojo {
        @Parameter
        private final String parameter = "default-parameter";

        @Component
        private final Runnable component = () -> { };

        private String parameterValue() {
            component.run();
            return parameter;
        }
    }

    private static final class ConfiguredMojoAnnotation implements Mojo {
        @Override
        public Class<? extends Annotation> annotationType() {
            return Mojo.class;
        }

        @Override
        public String name() {
            return "descriptor";
        }

        @Override
        public LifecyclePhase defaultPhase() {
            return LifecyclePhase.VERIFY;
        }

        @Override
        public ResolutionScope requiresDependencyResolution() {
            return ResolutionScope.TEST;
        }

        @Override
        public ResolutionScope requiresDependencyCollection() {
            return ResolutionScope.RUNTIME;
        }

        @Override
        public InstanciationStrategy instantiationStrategy() {
            return InstanciationStrategy.SINGLETON;
        }

        @Override
        public String executionStrategy() {
            return "always";
        }

        @Override
        public boolean requiresProject() {
            return false;
        }

        @Override
        public boolean requiresReports() {
            return true;
        }

        @Override
        public boolean aggregator() {
            return true;
        }

        @Override
        public boolean requiresDirectInvocation() {
            return true;
        }

        @Override
        public boolean requiresOnline() {
            return true;
        }

        @Override
        public boolean inheritByDefault() {
            return false;
        }

        @Override
        public String configurator() {
            return "custom-configurator";
        }

        @Override
        public boolean threadSafe() {
            return true;
        }
    }

    private static final class ConfiguredExecuteAnnotation implements Execute {
        @Override
        public Class<? extends Annotation> annotationType() {
            return Execute.class;
        }

        @Override
        public LifecyclePhase phase() {
            return LifecyclePhase.GENERATE_SOURCES;
        }

        @Override
        public String goal() {
            return "generate-descriptor";
        }

        @Override
        public String lifecycle() {
            return "site";
        }
    }

    private static final class ConfiguredParameterAnnotation implements Parameter {
        @Override
        public Class<? extends Annotation> annotationType() {
            return Parameter.class;
        }

        @Override
        public String alias() {
            return "output";
        }

        @Override
        public String property() {
            return "plugin.outputDirectory";
        }

        @Override
        public String defaultValue() {
            return "${project.build.directory}/generated-sources/plugin";
        }

        @Override
        public boolean required() {
            return true;
        }

        @Override
        public boolean readonly() {
            return true;
        }
    }

    private static final class ConfiguredComponentAnnotation implements Component {
        @Override
        public Class<? extends Annotation> annotationType() {
            return Component.class;
        }

        @Override
        public Class<?> role() {
            return Runnable.class;
        }

        @Override
        public String hint() {
            return "descriptor-generator";
        }
    }
}
