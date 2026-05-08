/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.maven.api.plugin.descriptor.Dependency;
import org.apache.maven.api.plugin.descriptor.MojoDescriptor;
import org.apache.maven.api.plugin.descriptor.Parameter;
import org.apache.maven.api.plugin.descriptor.PluginDescriptor;
import org.apache.maven.api.plugin.descriptor.Requirement;
import org.apache.maven.api.plugin.descriptor.Resolution;
import org.apache.maven.api.plugin.descriptor.lifecycle.Execution;
import org.apache.maven.api.plugin.descriptor.lifecycle.Lifecycle;
import org.apache.maven.api.plugin.descriptor.lifecycle.LifecycleConfiguration;
import org.apache.maven.api.plugin.descriptor.lifecycle.Phase;
import org.apache.maven.api.xml.XmlNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Maven_api_pluginTest {
    @Test
    void descriptorDefaultsAreUsefulAndImmutable() {
        PluginDescriptor plugin = PluginDescriptor.newInstance();
        MojoDescriptor mojo = MojoDescriptor.newInstance();
        Parameter parameter = Parameter.newInstance();
        LifecycleConfiguration lifecycleConfiguration = LifecycleConfiguration.newInstance();

        assertThat(plugin.getNamespaceUri()).isNull();
        assertThat(plugin.getModelEncoding()).isEqualTo("UTF-8");
        assertThat(plugin.isIsolatedRealm()).isFalse();
        assertThat(plugin.isInheritedByDefault()).isTrue();
        assertThat(plugin.getMojos()).isEmpty();

        assertThat(mojo.isDirectInvocationOnly()).isFalse();
        assertThat(mojo.isProjectRequired()).isTrue();
        assertThat(mojo.isOnlineRequired()).isFalse();
        assertThat(mojo.isAggregator()).isFalse();
        assertThat(mojo.isInheritedByDefault()).isTrue();
        assertThat(mojo.getParameters()).isEmpty();
        assertThat(mojo.getResolutions()).isEmpty();

        assertThat(parameter.isRequired()).isFalse();
        assertThat(parameter.isEditable()).isTrue();
        assertThat(lifecycleConfiguration.getModelEncoding()).isEqualTo("UTF-8");
        assertThat(lifecycleConfiguration.getLifecycles()).isEmpty();

        assertThatThrownBy(() -> plugin.getMojos().add(MojoDescriptor.newInstance()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> mojo.getParameters().add(Parameter.newInstance()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> lifecycleConfiguration.getLifecycles().add(Lifecycle.newInstance()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void dependencyRequirementAndResolutionBuildersSupportCopyAndWithers() {
        Dependency dependency = Dependency.newBuilder()
                .groupId("org.example")
                .artifactId("example-extension")
                .version("1.2.3")
                .type("maven-plugin")
                .build();
        Requirement requirement = Requirement.newBuilder()
                .role("org.example.Tool")
                .roleHint("default")
                .fieldName("tool")
                .build();
        Resolution resolution = Resolution.newBuilder()
                .field("project")
                .pathScope("runtime")
                .requestType("dependency-collection")
                .build();

        assertThat(dependency.getGroupId()).isEqualTo("org.example");
        assertThat(dependency.getArtifactId()).isEqualTo("example-extension");
        assertThat(dependency.getVersion()).isEqualTo("1.2.3");
        assertThat(dependency.getType()).isEqualTo("maven-plugin");
        assertThat(dependency.withVersion("2.0.0").getArtifactId()).isEqualTo("example-extension");
        assertThat(dependency.withVersion("2.0.0").getVersion()).isEqualTo("2.0.0");
        assertThat(Dependency.newBuilder(dependency).type("jar").build().getType()).isEqualTo("jar");

        assertThat(requirement.getRole()).isEqualTo("org.example.Tool");
        assertThat(requirement.getRoleHint()).isEqualTo("default");
        assertThat(requirement.withRoleHint("fast").getFieldName()).isEqualTo("tool");
        assertThat(requirement.withRoleHint("fast").getRoleHint()).isEqualTo("fast");

        assertThat(resolution.getField()).isEqualTo("project");
        assertThat(resolution.getPathScope()).isEqualTo("runtime");
        assertThat(resolution.withRequestType("dependency-resolution").getPathScope()).isEqualTo("runtime");
        assertThat(resolution.withRequestType("dependency-resolution").getRequestType())
                .isEqualTo("dependency-resolution");
    }

    @Test
    void parameterBuilderModelsPluginParameterMetadata() {
        Parameter parameter = Parameter.newBuilder()
                .name("outputDirectory")
                .alias("output")
                .type("java.io.File")
                .required(true)
                .editable(false)
                .description("Directory that receives generated sources")
                .since("1.0")
                .deprecated("Use generatedSourcesDirectory")
                .expression("${project.build.directory}/generated-sources")
                .defaultValue("${project.build.directory}/generated-sources/plugin")
                .build();

        assertThat(parameter.getName()).isEqualTo("outputDirectory");
        assertThat(parameter.getAlias()).isEqualTo("output");
        assertThat(parameter.getType()).isEqualTo("java.io.File");
        assertThat(parameter.isRequired()).isTrue();
        assertThat(parameter.isEditable()).isFalse();
        assertThat(parameter.getDescription()).contains("generated sources");
        assertThat(parameter.getSince()).isEqualTo("1.0");
        assertThat(parameter.getDeprecated()).isEqualTo("Use generatedSourcesDirectory");
        assertThat(parameter.getExpression()).isEqualTo("${project.build.directory}/generated-sources");
        assertThat(parameter.getDefaultValue()).isEqualTo("${project.build.directory}/generated-sources/plugin");

        Parameter updated = parameter.with()
                .required(false)
                .editable(true)
                .defaultValue("target/generated-sources/plugin")
                .build();

        assertThat(updated.getName()).isEqualTo("outputDirectory");
        assertThat(updated.isRequired()).isFalse();
        assertThat(updated.isEditable()).isTrue();
        assertThat(updated.getDefaultValue()).isEqualTo("target/generated-sources/plugin");
        assertThat(parameter.isRequired()).isTrue();
        assertThat(parameter.isEditable()).isFalse();
    }

    @Test
    void mojoDescriptorCombinesExecutionMetadataParametersAndResolutions() {
        Parameter parameter = Parameter.newBuilder()
                .name("skip")
                .type("boolean")
                .expression("${example.skip}")
                .defaultValue("false")
                .build();
        Resolution resolution = Resolution.newBuilder()
                .field("project")
                .pathScope("compile")
                .requestType("dependency-resolution")
                .build();
        List<Parameter> parameters = new ArrayList<>();
        parameters.add(parameter);

        MojoDescriptor mojo = MojoDescriptor.newBuilder()
                .goal("generate")
                .description("Generates project sources")
                .implementation("org.example.GenerateMojo")
                .language("java")
                .phase("generate-sources")
                .executePhase("compile")
                .executeGoal("compile")
                .executeLifecycle("default")
                .dependencyResolution("compile")
                .dependencyCollection("test")
                .directInvocationOnly(true)
                .projectRequired(false)
                .onlineRequired(true)
                .aggregator(true)
                .inheritedByDefault(false)
                .since("1.0")
                .deprecated("Use generate-new")
                .configurator("basic")
                .parameters(parameters)
                .resolutions(List.of(resolution))
                .id("org.example:example-plugin:generate")
                .fullGoalName("example:generate")
                .build();

        parameters.add(Parameter.newBuilder().name("late").build());

        assertThat(mojo.getGoal()).isEqualTo("generate");
        assertThat(mojo.getDescription()).isEqualTo("Generates project sources");
        assertThat(mojo.getImplementation()).isEqualTo("org.example.GenerateMojo");
        assertThat(mojo.getLanguage()).isEqualTo("java");
        assertThat(mojo.getPhase()).isEqualTo("generate-sources");
        assertThat(mojo.getExecutePhase()).isEqualTo("compile");
        assertThat(mojo.getExecuteGoal()).isEqualTo("compile");
        assertThat(mojo.getExecuteLifecycle()).isEqualTo("default");
        assertThat(mojo.getDependencyResolution()).isEqualTo("compile");
        assertThat(mojo.getDependencyCollection()).isEqualTo("test");
        assertThat(mojo.isDirectInvocationOnly()).isTrue();
        assertThat(mojo.isProjectRequired()).isFalse();
        assertThat(mojo.isOnlineRequired()).isTrue();
        assertThat(mojo.isAggregator()).isTrue();
        assertThat(mojo.isInheritedByDefault()).isFalse();
        assertThat(mojo.getSince()).isEqualTo("1.0");
        assertThat(mojo.getDeprecated()).isEqualTo("Use generate-new");
        assertThat(mojo.getConfigurator()).isEqualTo("basic");
        assertThat(mojo.getParameters()).containsExactly(parameter);
        assertThat(mojo.getResolutions()).containsExactly(resolution);
        assertThat(mojo.getId()).isEqualTo("org.example:example-plugin:generate");
        assertThat(mojo.getFullGoalName()).isEqualTo("example:generate");

        MojoDescriptor copied = MojoDescriptor.newBuilder(mojo)
                .goal("generate-new")
                .fullGoalName("example:generate-new")
                .build();

        assertThat(copied.getImplementation()).isEqualTo("org.example.GenerateMojo");
        assertThat(copied.getGoal()).isEqualTo("generate-new");
        assertThat(copied.getParameters()).containsExactly(parameter);
        assertThatThrownBy(() -> copied.getResolutions().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void pluginDescriptorProvidesMavenCoordinatesAndDefensiveMojoLists() {
        MojoDescriptor generateMojo = MojoDescriptor.newBuilder()
                .goal("generate")
                .implementation("org.example.GenerateMojo")
                .fullGoalName("example:generate")
                .build();
        List<MojoDescriptor> mojos = new ArrayList<>();
        mojos.add(generateMojo);

        PluginDescriptor descriptor = PluginDescriptor.newBuilder()
                .namespaceUri("https://maven.apache.org/plugin")
                .modelEncoding("UTF-16")
                .name("Example Maven Plugin")
                .description("Plugin used by integration tests")
                .groupId("org.example")
                .artifactId("example-maven-plugin")
                .version("1.0.0")
                .goalPrefix("example")
                .isolatedRealm(true)
                .inheritedByDefault(false)
                .requiredJavaVersion("17")
                .requiredMavenVersion("4.0.0")
                .mojos(mojos)
                .build();

        mojos.clear();

        assertThat(descriptor.getNamespaceUri()).isEqualTo("https://maven.apache.org/plugin");
        assertThat(descriptor.getModelEncoding()).isEqualTo("UTF-16");
        assertThat(descriptor.getName()).isEqualTo("Example Maven Plugin");
        assertThat(descriptor.getDescription()).isEqualTo("Plugin used by integration tests");
        assertThat(descriptor.getGroupId()).isEqualTo("org.example");
        assertThat(descriptor.getArtifactId()).isEqualTo("example-maven-plugin");
        assertThat(descriptor.getVersion()).isEqualTo("1.0.0");
        assertThat(descriptor.getGoalPrefix()).isEqualTo("example");
        assertThat(descriptor.isIsolatedRealm()).isTrue();
        assertThat(descriptor.isInheritedByDefault()).isFalse();
        assertThat(descriptor.getRequiredJavaVersion()).isEqualTo("17");
        assertThat(descriptor.getRequiredMavenVersion()).isEqualTo("4.0.0");
        assertThat(descriptor.getMojos()).containsExactly(generateMojo);
        assertThat(descriptor.getPluginLookupKey()).isEqualTo("org.example:example-maven-plugin");
        assertThat(descriptor.getId()).isEqualTo("org.example:example-maven-plugin:1.0.0");

        PluginDescriptor updated = descriptor.withVersion("1.1.0").withGoalPrefix("sample");

        assertThat(updated.getId()).isEqualTo("org.example:example-maven-plugin:1.1.0");
        assertThat(updated.getGoalPrefix()).isEqualTo("sample");
        assertThat(updated.getMojos()).containsExactly(generateMojo);
        assertThat(descriptor.getVersion()).isEqualTo("1.0.0");
        assertThatThrownBy(() -> updated.getMojos().remove(0)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void completeCopyBuildersCanClearOptionalScalarsAndCollections() {
        Parameter parameter = Parameter.newBuilder()
                .name("outputDirectory")
                .type("java.io.File")
                .description("Directory that receives generated sources")
                .expression("${project.build.directory}")
                .defaultValue("target")
                .required(true)
                .build();
        Parameter clearedParameter = Parameter.newBuilder(parameter, true)
                .description(null)
                .expression(null)
                .defaultValue(null)
                .build();

        assertThat(clearedParameter).isNotSameAs(parameter);
        assertThat(clearedParameter.getName()).isEqualTo("outputDirectory");
        assertThat(clearedParameter.getType()).isEqualTo("java.io.File");
        assertThat(clearedParameter.isRequired()).isTrue();
        assertThat(clearedParameter.getDescription()).isNull();
        assertThat(clearedParameter.getExpression()).isNull();
        assertThat(clearedParameter.getDefaultValue()).isNull();
        assertThat(parameter.getDescription()).isEqualTo("Directory that receives generated sources");
        assertThat(parameter.getExpression()).isEqualTo("${project.build.directory}");
        assertThat(parameter.getDefaultValue()).isEqualTo("target");

        XmlNode configuration = XmlNode.newInstance(
                "configuration", null, Map.of(), List.of(), "test-input-location");
        Execution execution = Execution.newBuilder()
                .configuration(configuration)
                .goals(List.of("compile", "test"))
                .build();
        Execution clearedExecution = Execution.newBuilder(execution, true)
                .configuration(null)
                .goals(null)
                .build();

        assertThat(clearedExecution).isNotSameAs(execution);
        assertThat(clearedExecution.getConfiguration()).isNull();
        assertThat(clearedExecution.getGoals()).isEmpty();
        assertThat(execution.getConfiguration().inputLocation()).isEqualTo("test-input-location");
        assertThat(execution.getGoals()).containsExactly("compile", "test");
    }

    @Test
    void sparseBuildersPreserveBaseValuesWhenPartiallyUpdatingModels() {
        MojoDescriptor mojo = MojoDescriptor.newBuilder()
                .goal("verify")
                .implementation("org.example.VerifyMojo")
                .fullGoalName("example:verify")
                .build();
        PluginDescriptor descriptor = PluginDescriptor.newBuilder()
                .namespaceUri("https://maven.apache.org/plugin")
                .modelEncoding("UTF-16")
                .name("Example Maven Plugin")
                .description("Plugin used by integration tests")
                .groupId("org.example")
                .artifactId("example-maven-plugin")
                .version("1.0.0")
                .goalPrefix("example")
                .requiredJavaVersion("17")
                .requiredMavenVersion("4.0.0")
                .mojos(List.of(mojo))
                .build();

        PluginDescriptor unchanged = PluginDescriptor.newBuilder(descriptor, false).build();

        assertThat(unchanged.getName()).isEqualTo(descriptor.getName());
        assertThat(unchanged.getModelEncoding()).isEqualTo(descriptor.getModelEncoding());
        assertThat(unchanged.getMojos()).containsExactly(mojo);

        PluginDescriptor renamed = PluginDescriptor.newBuilder(descriptor, false)
                .name("Renamed Maven Plugin")
                .build();

        assertThat(renamed.getName()).isEqualTo("Renamed Maven Plugin");
        assertThat(renamed.getNamespaceUri()).isEqualTo("https://maven.apache.org/plugin");
        assertThat(renamed.getModelEncoding()).isEqualTo("UTF-16");
        assertThat(renamed.getDescription()).isEqualTo("Plugin used by integration tests");
        assertThat(renamed.getGroupId()).isEqualTo("org.example");
        assertThat(renamed.getArtifactId()).isEqualTo("example-maven-plugin");
        assertThat(renamed.getVersion()).isEqualTo("1.0.0");
        assertThat(renamed.getGoalPrefix()).isEqualTo("example");
        assertThat(renamed.getRequiredJavaVersion()).isEqualTo("17");
        assertThat(renamed.getRequiredMavenVersion()).isEqualTo("4.0.0");
        assertThat(renamed.getMojos()).containsExactly(mojo);
        assertThat(descriptor.getName()).isEqualTo("Example Maven Plugin");

        Lifecycle lifecycle = Lifecycle.newBuilder()
                .id("default")
                .phases(List.of(Phase.newBuilder().id("verify").build()))
                .build();
        LifecycleConfiguration lifecycleConfiguration = LifecycleConfiguration.newBuilder()
                .namespaceUri("https://maven.apache.org/lifecycle")
                .modelEncoding("UTF-8")
                .lifecycles(List.of(lifecycle))
                .build();

        LifecycleConfiguration unchangedLifecycleConfiguration =
                LifecycleConfiguration.newBuilder(lifecycleConfiguration, false).build();

        assertThat(unchangedLifecycleConfiguration.getNamespaceUri())
                .isEqualTo(lifecycleConfiguration.getNamespaceUri());
        assertThat(unchangedLifecycleConfiguration.getModelEncoding())
                .isEqualTo(lifecycleConfiguration.getModelEncoding());
        assertThat(unchangedLifecycleConfiguration.getLifecycles()).containsExactly(lifecycle);

        Lifecycle renamedLifecycle = lifecycle.withId("site");
        LifecycleConfiguration updatedLifecycles = LifecycleConfiguration.newBuilder(lifecycleConfiguration, false)
                .lifecycles(List.of(renamedLifecycle))
                .build();

        assertThat(updatedLifecycles.getNamespaceUri()).isEqualTo("https://maven.apache.org/lifecycle");
        assertThat(updatedLifecycles.getModelEncoding()).isEqualTo("UTF-8");
        assertThat(updatedLifecycles.getLifecycles()).containsExactly(renamedLifecycle);
        assertThat(lifecycleConfiguration.getLifecycles()).containsExactly(lifecycle);
    }

    @Test
    void lifecycleConfigurationPreservesXmlConfigurationAndEffectivePhaseIds() {
        XmlNode configuration = XmlNode.newInstance(
                "configuration",
                null,
                Map.of("combine.children", "append"),
                List.of(XmlNode.newInstance("skip", "true"), XmlNode.newInstance("threads", "2")),
                "test-input-location");
        Execution execution = Execution.newBuilder()
                .configuration(configuration)
                .goals(List.of("compile", "testCompile"))
                .build();
        Phase beforeCompile = Phase.newBuilder()
                .id("compile")
                .executionPoint("before")
                .priority(10)
                .executions(List.of(execution))
                .configuration(configuration)
                .build();
        Phase regularTest = Phase.newBuilder()
                .id("test")
                .executions(List.of(execution.withGoals(List.of("test"))))
                .build();
        Lifecycle lifecycle = Lifecycle.newBuilder()
                .id("default")
                .phases(List.of(beforeCompile, regularTest))
                .build();
        LifecycleConfiguration lifecycleConfiguration = LifecycleConfiguration.newBuilder()
                .namespaceUri("https://maven.apache.org/lifecycle")
                .modelEncoding("UTF-8")
                .lifecycles(List.of(lifecycle))
                .build();

        assertThat(execution.getConfiguration().child("skip").value()).isEqualTo("true");
        assertThat(execution.getConfiguration().attribute("combine.children")).isEqualTo("append");
        assertThat(execution.getConfiguration().inputLocation()).isEqualTo("test-input-location");
        assertThat(execution.getGoals()).containsExactly("compile", "testCompile");
        assertThat(beforeCompile.getEffectiveId()).isEqualTo("before:compile[10]");
        assertThat(regularTest.getEffectiveId()).isEqualTo("test");
        assertThat(lifecycle.getPhases()).containsExactly(beforeCompile, regularTest);
        assertThat(lifecycleConfiguration.getNamespaceUri()).isEqualTo("https://maven.apache.org/lifecycle");
        assertThat(lifecycleConfiguration.getLifecycles()).containsExactly(lifecycle);

        LifecycleConfiguration updated = lifecycleConfiguration.withLifecycles(
                List.of(lifecycle.withPhases(List.of(regularTest.withPriority(5)))));

        assertThat(updated.getLifecycles().get(0).getPhases().get(0).getEffectiveId()).isEqualTo("test[5]");
        assertThat(lifecycleConfiguration.getLifecycles().get(0).getPhases())
                .containsExactly(beforeCompile, regularTest);
        assertThatThrownBy(() -> execution.getGoals().add("verify")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> lifecycle.getPhases().add(Phase.newInstance()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
