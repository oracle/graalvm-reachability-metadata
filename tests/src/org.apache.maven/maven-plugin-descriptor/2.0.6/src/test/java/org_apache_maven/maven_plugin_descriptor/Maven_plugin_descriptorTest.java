/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_plugin_descriptor;

import java.io.FileNotFoundException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import org.apache.maven.plugin.descriptor.DuplicateMojoDescriptorException;
import org.apache.maven.plugin.descriptor.DuplicateParameterException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.Parameter;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptorBuilder;
import org.apache.maven.plugin.lifecycle.Execution;
import org.apache.maven.plugin.lifecycle.Lifecycle;
import org.apache.maven.plugin.lifecycle.LifecycleConfiguration;
import org.apache.maven.plugin.lifecycle.Phase;
import org.apache.maven.plugin.lifecycle.io.xpp3.LifecycleMappingsXpp3Reader;
import org.apache.maven.plugin.lifecycle.io.xpp3.LifecycleMappingsXpp3Writer;
import org.codehaus.classworlds.ClassRealm;
import org.codehaus.classworlds.ClassWorld;
import org.codehaus.plexus.component.repository.ComponentDependency;
import org.codehaus.plexus.component.repository.ComponentRequirement;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.PlexusConfigurationException;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Maven_plugin_descriptorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void buildsCompletePluginDescriptorFromXml() throws PlexusConfigurationException {
        PluginDescriptor descriptor = new PluginDescriptorBuilder().build(new StringReader("""
                <plugin>
                  <groupId>com.example.build</groupId>
                  <artifactId>sample-maven-plugin</artifactId>
                  <version>1.0</version>
                  <goalPrefix>sample</goalPrefix>
                  <name>Sample Plugin</name>
                  <description>Builds a sample project</description>
                  <isolatedRealm>true</isolatedRealm>
                  <inheritedByDefault>false</inheritedByDefault>
                  <mojos>
                    <mojo>
                      <goal>compile</goal>
                      <implementation>com.example.build.CompileMojo</implementation>
                      <language>java</language>
                      <configurator>basic</configurator>
                      <composer>map-oriented</composer>
                      <since>1.0</since>
                      <phase>compile</phase>
                      <executePhase>generate-sources</executePhase>
                      <executeGoal>generate</executeGoal>
                      <executeLifecycle>default</executeLifecycle>
                      <instantiationStrategy>singleton</instantiationStrategy>
                      <description>Compiles generated sources</description>
                      <requiresDependencyResolution>test</requiresDependencyResolution>
                      <requiresDirectInvocation>true</requiresDirectInvocation>
                      <requiresProject>false</requiresProject>
                      <requiresReports>true</requiresReports>
                      <aggregator>true</aggregator>
                      <requiresOnline>true</requiresOnline>
                      <inheritedByDefault>false</inheritedByDefault>
                      <parameters>
                        <parameter>
                          <name>sourceDirectory</name>
                          <alias>sources</alias>
                          <type>java.io.File</type>
                          <required>true</required>
                          <editable>false</editable>
                          <description>Directory with sources</description>
                          <deprecated>Use inputDirectory</deprecated>
                          <implementation>java.io.File</implementation>
                        </parameter>
                      </parameters>
                      <configuration>
                        <sourceDirectory implementation="java.io.File">${source}</sourceDirectory>
                      </configuration>
                      <requirements>
                        <requirement>
                          <role>com.example.build.Compiler</role>
                          <role-hint>javac</role-hint>
                          <field-name>compiler</field-name>
                        </requirement>
                      </requirements>
                    </mojo>
                    <mojo>
                      <goal>clean</goal>
                      <implementation>com.example.build.CleanMojo</implementation>
                      <description>Cleans generated output</description>
                    </mojo>
                  </mojos>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>build-support</artifactId>
                      <type>jar</type>
                      <version>2.1</version>
                    </dependency>
                  </dependencies>
                </plugin>
                """), "memory-plugin.xml");

        assertThat(descriptor.getSource()).isEqualTo("memory-plugin.xml");
        assertThat(descriptor.getGroupId()).isEqualTo("com.example.build");
        assertThat(descriptor.getArtifactId()).isEqualTo("sample-maven-plugin");
        assertThat(descriptor.getVersion()).isEqualTo("1.0");
        assertThat(descriptor.getGoalPrefix()).isEqualTo("sample");
        assertThat(descriptor.getName()).isEqualTo("Sample Plugin");
        assertThat(descriptor.getDescription()).isEqualTo("Builds a sample project");
        assertThat(descriptor.isIsolatedRealm()).isTrue();
        assertThat(descriptor.isInheritedByDefault()).isFalse();
        assertThat(descriptor.getId()).isEqualTo("com.example.build:sample-maven-plugin:1.0");
        assertThat(descriptor.getPluginLookupKey()).isEqualTo("com.example.build:sample-maven-plugin");
        assertThat(descriptor.getMojos()).hasSize(2);

        MojoDescriptor mojo = descriptor.getMojo("compile");
        assertThat(mojo.getPluginDescriptor()).isSameAs(descriptor);
        assertThat(mojo.getGoal()).isEqualTo("compile");
        assertThat(mojo.getFullGoalName()).isEqualTo("sample:compile");
        assertThat(mojo.getImplementation()).isEqualTo("com.example.build.CompileMojo");
        assertThat(mojo.getLanguage()).isEqualTo("java");
        assertThat(mojo.getComponentConfigurator()).isEqualTo("basic");
        assertThat(mojo.getComponentComposer()).isEqualTo("map-oriented");
        assertThat(mojo.getSince()).isEqualTo("1.0");
        assertThat(mojo.getPhase()).isEqualTo("compile");
        assertThat(mojo.getExecutePhase()).isEqualTo("generate-sources");
        assertThat(mojo.getExecuteGoal()).isEqualTo("generate");
        assertThat(mojo.getExecuteLifecycle()).isEqualTo("default");
        assertThat(mojo.getInstantiationStrategy()).isEqualTo("singleton");
        assertThat(mojo.getDescription()).isEqualTo("Compiles generated sources");
        assertThat(mojo.isDependencyResolutionRequired()).isEqualTo("test");
        assertThat(mojo.isDirectInvocationOnly()).isTrue();
        assertThat(mojo.isProjectRequired()).isFalse();
        assertThat(mojo.isRequiresReports()).isTrue();
        assertThat(mojo.isAggregator()).isTrue();
        assertThat(mojo.requiresOnline()).isTrue();
        assertThat(mojo.isInheritedByDefault()).isFalse();
        assertThat(mojo.getParameterMap()).containsKey("sourceDirectory");

        Parameter parameter = (Parameter) mojo.getParameters().get(0);
        assertThat(parameter.getName()).isEqualTo("sourceDirectory");
        assertThat(parameter.getAlias()).isEqualTo("sources");
        assertThat(parameter.getType()).isEqualTo("java.io.File");
        assertThat(parameter.isRequired()).isTrue();
        assertThat(parameter.isEditable()).isFalse();
        assertThat(parameter.getDescription()).isEqualTo("Directory with sources");
        assertThat(parameter.getDeprecated()).isEqualTo("Use inputDirectory");
        assertThat(parameter.getImplementation()).isEqualTo("java.io.File");
        assertThat(parameter.toString()).contains("sourceDirectory", "sources");

        PlexusConfiguration sourceDirectory = mojo.getMojoConfiguration().getChild("sourceDirectory");
        assertThat(sourceDirectory.getValue()).isEqualTo("${source}");
        assertThat(sourceDirectory.getAttribute("implementation")).isEqualTo("java.io.File");

        ComponentRequirement requirement = (ComponentRequirement) mojo.getRequirements().get(0);
        assertThat(requirement.getRole()).isEqualTo("com.example.build.Compiler");
        assertThat(requirement.getRoleHint()).isEqualTo("javac");
        assertThat(requirement.getFieldName()).isEqualTo("compiler");

        ComponentDependency dependency = (ComponentDependency) descriptor.getDependencies().get(0);
        assertThat(dependency.getGroupId()).isEqualTo("com.example");
        assertThat(dependency.getArtifactId()).isEqualTo("build-support");
        assertThat(dependency.getType()).isEqualTo("jar");
        assertThat(dependency.getVersion()).isEqualTo("2.1");
        assertThat(descriptor.getMojo("clean").getImplementation()).isEqualTo("com.example.build.CleanMojo");
        assertThat(descriptor.getMojo("missing")).isNull();
    }

    @Test
    void descriptorModelsExposeKeysDefaultsAndDuplicateValidation() throws Exception {
        assertThat(PluginDescriptor.constructPluginKey("group", "artifact", "1"))
                .isEqualTo("group:artifact:1");
        assertThat(PluginDescriptor.getDefaultPluginGroupId()).isEqualTo("org.apache.maven.plugins");
        assertThat(PluginDescriptor.getDefaultPluginArtifactId("help")).isEqualTo("maven-help-plugin");
        assertThat(PluginDescriptor.getGoalPrefixFromArtifactId("maven-plugin-plugin")).isEqualTo("plugin");
        assertThat(PluginDescriptor.getGoalPrefixFromArtifactId("maven-compiler-plugin")).isEqualTo("compiler");
        assertThat(PluginDescriptor.getGoalPrefixFromArtifactId("sample-plugin-extension"))
                .isEqualTo("sampleextension");

        PluginDescriptor descriptor = new PluginDescriptor();
        descriptor.setGroupId("com.example");
        descriptor.setArtifactId("demo-plugin");
        descriptor.setVersion("1.0");
        descriptor.setGoalPrefix("demo");
        descriptor.setSource("manual");
        descriptor.setInheritedByDefault(true);
        descriptor.setArtifacts(Collections.emptyList());

        MojoDescriptor mojo = new MojoDescriptor();
        mojo.setPluginDescriptor(descriptor);
        mojo.setGoal("run");
        mojo.setImplementation("com.example.RunMojo");
        mojo.setDependencyResolutionRequired("runtime");
        mojo.setExecutionStrategy("always");
        mojo.setProjectRequired(true);
        mojo.setOnlineRequired(false);
        mojo.setAggregator(false);
        mojo.setDirectInvocationOnly(false);
        mojo.setRequiresReports(false);
        descriptor.addMojo(mojo);

        assertThat(mojo.getId()).isEqualTo("com.example:demo-plugin:1.0:run");
        assertThat(mojo.getRole()).isEqualTo("org.apache.maven.plugin.Mojo");
        assertThat(mojo.getRoleHint()).isEqualTo(mojo.getId());
        assertThat(mojo.getComponentType()).isEqualTo("maven-plugin");
        assertThat(mojo.alwaysExecute()).isTrue();
        assertThat(mojo.isDependencyResolutionRequired()).isEqualTo("runtime");
        assertThat(descriptor.getArtifacts()).isEmpty();
        assertThat(descriptor.getArtifactMap()).isEmpty();

        MojoDescriptor duplicateMojo = new MojoDescriptor();
        duplicateMojo.setPluginDescriptor(descriptor);
        duplicateMojo.setGoal("run");
        duplicateMojo.setImplementation("com.example.OtherRunMojo");
        assertThrows(DuplicateMojoDescriptorException.class, () -> descriptor.addMojo(duplicateMojo));

        Parameter parameter = new Parameter();
        parameter.setName("outputDirectory");
        parameter.setAlias("output");
        parameter.setType("java.io.File");
        parameter.setExpression("${project.build.directory}");
        parameter.setDefaultValue("target");
        parameter.setSince("1.0");
        parameter.setRequirement(new org.apache.maven.plugin.descriptor.Requirement("com.example.Output", "default"));
        mojo.addParameter(parameter);

        assertThat(parameter.getExpression()).isEqualTo("${project.build.directory}");
        assertThat(parameter.getDefaultValue()).isEqualTo("target");
        assertThat(parameter.getSince()).isEqualTo("1.0");
        assertThat(parameter.getRequirement().getRole()).isEqualTo("com.example.Output");
        assertThat(parameter.getRequirement().getRoleHint()).isEqualTo("default");
        assertThat(mojo.getParameterMap()).containsEntry("outputDirectory", parameter);

        Parameter duplicateParameter = new Parameter();
        duplicateParameter.setName("outputDirectory");
        assertThrows(DuplicateParameterException.class, () -> mojo.addParameter(duplicateParameter));
    }

    @Test
    void lifecycleMappingsCanBeWrittenReadAndMutated() throws Exception {
        Xpp3Dom executionConfiguration = new Xpp3Dom("configuration");
        Xpp3Dom skip = new Xpp3Dom("skip");
        skip.setValue("false");
        executionConfiguration.addChild(skip);

        Execution execution = new Execution();
        execution.addGoal("compile");
        execution.addGoal("testCompile");
        execution.removeGoal("testCompile");
        execution.setConfiguration(executionConfiguration);

        Phase phase = new Phase();
        phase.setId("process-sources");
        phase.addExecution(execution);

        Lifecycle lifecycle = new Lifecycle();
        lifecycle.setId("default");
        lifecycle.addPhase(phase);

        Lifecycle unusedLifecycle = new Lifecycle();
        unusedLifecycle.setId("site");

        LifecycleConfiguration configuration = new LifecycleConfiguration();
        configuration.setModelEncoding("UTF-8");
        configuration.addLifecycle(lifecycle);
        configuration.addLifecycle(unusedLifecycle);
        configuration.removeLifecycle(unusedLifecycle);

        StringWriter writer = new StringWriter();
        new LifecycleMappingsXpp3Writer().write(writer, configuration);

        String xml = writer.toString();
        assertThat(xml).contains("<lifecycles>", "<lifecycle>", "<id>default</id>", "<goal>compile</goal>");

        LifecycleConfiguration parsed = new LifecycleMappingsXpp3Reader().read(new StringReader(xml));
        assertThat(parsed.getLifecycles()).hasSize(1);

        Lifecycle parsedLifecycle = (Lifecycle) parsed.getLifecycles().get(0);
        assertThat(parsedLifecycle.getId()).isEqualTo("default");
        assertThat(parsedLifecycle.getPhases()).hasSize(1);

        Phase parsedPhase = (Phase) parsedLifecycle.getPhases().get(0);
        assertThat(parsedPhase.getId()).isEqualTo("process-sources");
        assertThat(parsedPhase.getExecutions()).hasSize(1);

        Execution parsedExecution = (Execution) parsedPhase.getExecutions().get(0);
        assertThat(parsedExecution.getGoals()).containsExactly("compile");
        Xpp3Dom parsedExecutionConfiguration = (Xpp3Dom) parsedExecution.getConfiguration();
        assertThat(parsedExecutionConfiguration.getChild("skip").getValue()).isEqualTo("false");

        parsedPhase.removeExecution(parsedExecution);
        assertThat(parsedPhase.getExecutions()).isEmpty();
        parsedLifecycle.removePhase(parsedPhase);
        assertThat(parsedLifecycle.getPhases()).isEmpty();
    }

    @Test
    void readersAndBuildersRejectInvalidDescriptors() throws Exception {
        PluginDescriptorBuilder builder = new PluginDescriptorBuilder();
        assertThrows(PlexusConfigurationException.class,
                () -> builder.buildConfiguration(new StringReader("<plugin>")));

        String duplicateLifecycleId = """
                <lifecycles>
                  <lifecycle>
                    <id>default</id>
                    <id>duplicate</id>
                  </lifecycle>
                </lifecycles>
                """;
        assertThrows(XmlPullParserException.class,
                () -> new LifecycleMappingsXpp3Reader().read(new StringReader(duplicateLifecycleId)));

        String unknownLifecycleTag = """
                <lifecycles>
                  <lifecycle>
                    <id>default</id>
                    <unexpected />
                  </lifecycle>
                </lifecycles>
                """;
        assertThrows(XmlPullParserException.class,
                () -> new LifecycleMappingsXpp3Reader().read(new StringReader(unknownLifecycleTag)));
    }

    @Test
    void pluginDescriptorReportsMissingLifecycleResourceFromClassRealm() throws Exception {
        PluginDescriptor descriptor = new PluginDescriptor();
        ClassRealm realm = new ClassWorld().newRealm("empty-plugin-realm");
        realm.addConstituent(temporaryDirectory.toUri().toURL());
        descriptor.setClassRealm(realm);

        assertThrows(FileNotFoundException.class, () -> descriptor.getLifecycleMapping("default"));
    }

    @Test
    void collectionSettersReplaceLifecycleModelContents() {
        Execution execution = new Execution();
        execution.setGoals(Arrays.asList("compile", "test"));
        assertThat(execution.getGoals()).containsExactly("compile", "test");

        Phase phase = new Phase();
        phase.setExecutions(Collections.singletonList(execution));
        phase.setConfiguration("phase-configuration");
        assertThat(phase.getExecutions()).containsExactly(execution);
        assertThat(phase.getConfiguration()).isEqualTo("phase-configuration");

        Lifecycle lifecycle = new Lifecycle();
        lifecycle.setPhases(Collections.singletonList(phase));
        assertThat(lifecycle.getPhases()).containsExactly(phase);

        LifecycleConfiguration configuration = new LifecycleConfiguration();
        configuration.setLifecycles(Collections.singletonList(lifecycle));
        assertThat(configuration.getLifecycles()).containsExactly(lifecycle);
    }
}
