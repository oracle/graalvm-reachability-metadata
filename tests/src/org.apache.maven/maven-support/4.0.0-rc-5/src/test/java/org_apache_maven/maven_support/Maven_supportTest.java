/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_support;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.apache.maven.api.metadata.Metadata;
import org.apache.maven.api.metadata.Versioning;
import org.apache.maven.api.model.Build;
import org.apache.maven.api.model.Dependency;
import org.apache.maven.api.model.InputLocation;
import org.apache.maven.api.model.InputSource;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.model.Plugin;
import org.apache.maven.api.plugin.descriptor.MojoDescriptor;
import org.apache.maven.api.plugin.descriptor.PluginDescriptor;
import org.apache.maven.api.plugin.descriptor.lifecycle.Execution;
import org.apache.maven.api.plugin.descriptor.lifecycle.Lifecycle;
import org.apache.maven.api.plugin.descriptor.lifecycle.LifecycleConfiguration;
import org.apache.maven.api.plugin.descriptor.lifecycle.Phase;
import org.apache.maven.api.settings.Server;
import org.apache.maven.api.settings.Settings;
import org.apache.maven.api.toolchain.PersistedToolchains;
import org.apache.maven.api.toolchain.ToolchainModel;
import org.apache.maven.metadata.v4.MetadataStaxReader;
import org.apache.maven.metadata.v4.MetadataStaxWriter;
import org.apache.maven.model.v4.MavenMerger;
import org.apache.maven.model.v4.MavenModelVersion;
import org.apache.maven.model.v4.MavenStaxReader;
import org.apache.maven.model.v4.MavenStaxWriter;
import org.apache.maven.model.v4.MavenTransformer;
import org.apache.maven.plugin.descriptor.io.PluginDescriptorStaxReader;
import org.apache.maven.plugin.descriptor.io.PluginDescriptorStaxWriter;
import org.apache.maven.plugin.lifecycle.io.LifecycleStaxReader;
import org.apache.maven.plugin.lifecycle.io.LifecycleStaxWriter;
import org.apache.maven.settings.v4.SettingsMerger;
import org.apache.maven.settings.v4.SettingsStaxReader;
import org.apache.maven.settings.v4.SettingsStaxWriter;
import org.apache.maven.settings.v4.SettingsTransformer;
import org.apache.maven.toolchain.v4.MavenToolchainsMerger;
import org.apache.maven.toolchain.v4.MavenToolchainsStaxReader;
import org.apache.maven.toolchain.v4.MavenToolchainsStaxWriter;
import org.apache.maven.toolchain.v4.MavenToolchainsTransformer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Maven_supportTest {
    @Test
    void readsMavenModelWithLocationsAndContentTransformation() throws Exception {
        String xml = """
                <project root="true">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <modules>
                    <module>core</module>
                    <module>cli</module>
                  </modules>
                  <dependencies>
                    <dependency>
                      <groupId>org.junit.jupiter</groupId>
                      <artifactId>junit-jupiter-api</artifactId>
                      <version>5.10.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """;

        MavenStaxReader reader = new MavenStaxReader((value, fieldName) -> {
            if ("artifactId".equals(fieldName)) {
                return value + "-transformed";
            }
            return value;
        });
        reader.setAddLocationInformation(true);
        Model model = reader.read(new StringReader(xml), true, new InputSource("pom.xml", null));

        assertThat(model.getGroupId()).isEqualTo("org.example");
        assertThat(model.getArtifactId()).isEqualTo("demo-transformed");
        assertThat(model.isRoot()).isTrue();
        assertThat(model.getModules()).containsExactly("core", "cli");
        assertThat(model.getDependencies()).hasSize(1);
        assertThat(model.getDependencies().get(0).getArtifactId()).isEqualTo("junit-jupiter-api-transformed");
        assertThat(new MavenModelVersion().getModelVersion(model)).isEqualTo("4.1.0");

        InputLocation projectLocation = model.getLocation("");
        InputLocation dependencyLocation = model.getDependencies().get(0).getLocation("");
        assertThat(projectLocation.getLineNumber()).isEqualTo(1);
        assertThat(dependencyLocation.getLineNumber()).isEqualTo(11);
        assertThat(model.getLocation("modules").getLocation(1).getLineNumber()).isEqualTo(8);
    }

    @Test
    void resolvesDefaultHtmlEntitiesWhenReadingMavenModelInNonStrictMode() throws Exception {
        String xml = """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>entity-demo</artifactId>
                  <version>1.0.0</version>
                  <description>Copyright &copy; Maven contributors &mdash; ready</description>
                </project>
                """;

        MavenStaxReader entityAwareReader = new MavenStaxReader();
        Model withDefaultEntities = entityAwareReader.read(new StringReader(xml), false, null);
        assertThat(withDefaultEntities.getDescription())
                .isEqualTo("Copyright © Maven contributors — ready");

        MavenStaxReader literalEntityReader = new MavenStaxReader();
        literalEntityReader.setAddDefaultEntities(false);
        Model withoutDefaultEntities = literalEntityReader.read(new StringReader(xml), false, null);
        assertThat(withoutDefaultEntities.getDescription())
                .isEqualTo("Copyright &copy; Maven contributors &mdash; ready");
    }

    @Test
    void writesAndReadsMavenModelRoundTripAndRejectsWrongNamespace() throws Exception {
        Dependency dependency = Dependency.newBuilder()
                .groupId("org.apache.maven")
                .artifactId("maven-api-model")
                .version("4.0.0")
                .scope("test")
                .build();
        Plugin plugin = Plugin.newBuilder()
                .groupId("org.apache.maven.plugins")
                .artifactId("maven-compiler-plugin")
                .version("3.13.0")
                .extensions("false")
                .build();
        Model model = Model.newBuilder()
                .modelVersion("4.0.0")
                .groupId("org.example")
                .artifactId("roundtrip")
                .version("1.0.0")
                .packaging("jar")
                .dependencies(List.of(dependency))
                .build(Build.newBuilder().plugins(List.of(plugin)).finalName("roundtrip-app").build())
                .build();

        StringWriter writer = new StringWriter();
        new MavenStaxWriter().write(writer, model);

        Model reparsed = new MavenStaxReader().read(new StringReader(writer.toString()));
        assertThat(reparsed.getId()).isEqualTo("org.example:roundtrip:jar:1.0.0");
        assertThat(reparsed.getDependencies()).extracting(Dependency::getManagementKey)
                .containsExactly("org.apache.maven:maven-api-model:jar");
        assertThat(reparsed.getBuild().getFinalName()).isEqualTo("roundtrip-app");
        assertThat(reparsed.getBuild().getPlugins().get(0).getKey())
                .isEqualTo("org.apache.maven.plugins:maven-compiler-plugin");
        assertThat(new MavenModelVersion().getModelVersion(reparsed)).isEqualTo("4.0.0");

        String mixedNamespaceXml = """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <build xmlns="http://maven.apache.org/POM/4.1.0"/>
                </project>
                """;
        assertThatThrownBy(() -> new MavenStaxReader().read(new StringReader(mixedNamespaceXml)))
                .isInstanceOf(XMLStreamException.class)
                .hasMessageContaining("Unexpected namespace for element 'build'");
    }

    @Test
    void mergesAndTransformsMavenModels() {
        Dependency targetDependency = Dependency.newBuilder()
                .groupId("org.example")
                .artifactId("shared")
                .version("1.0")
                .build();
        Dependency sourceDependency = targetDependency.withVersion("2.0");
        Model target = Model.newBuilder()
                .groupId("org.example")
                .artifactId("app")
                .version("1.0")
                .dependencies(List.of(targetDependency))
                .properties(Map.of("encoding", "UTF-8"))
                .build();
        Model source = Model.newBuilder()
                .artifactId("ignored-unless-source-dominant")
                .dependencies(List.of(sourceDependency))
                .properties(Map.of("release", "21"))
                .build();

        Model targetDominant = new MavenMerger().merge(target, source, false, Map.of());
        Model sourceDominant = new MavenMerger().merge(target, source, true, Map.of());
        assertThat(targetDominant.getArtifactId()).isEqualTo("app");
        assertThat(targetDominant.getDependencies().get(0).getVersion()).isEqualTo("1.0");
        assertThat(sourceDominant.getArtifactId()).isEqualTo("ignored-unless-source-dominant");
        assertThat(sourceDominant.getDependencies()).extracting(Dependency::getVersion).containsExactly("1.0", "2.0");
        assertThat(sourceDominant.getProperties()).containsEntry("encoding", "UTF-8").containsEntry("release", "21");

        Model transformed = new MavenTransformer(value -> value == null
                ? null
                : value.replace("org.example", "org.changed"))
                .visit(sourceDominant);
        assertThat(transformed.getGroupId()).isEqualTo("org.changed");
        assertThat(transformed.getDependencies().get(0).getGroupId()).isEqualTo("org.changed");
    }

    @Test
    void readsWritesMavenMetadata() throws Exception {
        String xml = """
                <metadata modelVersion="1.1.0">
                  <groupId>org.example</groupId>
                  <artifactId>demo</artifactId>
                  <versioning>
                    <latest>${latest}</latest>
                    <release>1.5.0</release>
                    <versions>
                      <version>1.0.0</version>
                      <version>2.0.0</version>
                    </versions>
                    <lastUpdated>20260101120000</lastUpdated>
                  </versioning>
                  <plugins>
                    <plugin>
                      <name>Example Plugin</name>
                      <prefix>example</prefix>
                      <artifactId>example-maven-plugin</artifactId>
                    </plugin>
                  </plugins>
                </metadata>
                """;

        Metadata metadata = new MetadataStaxReader((value, fieldName) -> {
            if ("latest".equals(fieldName)) {
                return "${latest}".equals(value) ? "2.0.0" : value;
            }
            return value;
        }).read(new StringReader(xml), true);

        assertThat(metadata.getModelVersion()).isEqualTo("1.1.0");
        assertThat(metadata.getVersioning().getVersions()).containsExactly("1.0.0", "2.0.0");
        assertThat(metadata.getPlugins().get(0).getPrefix()).isEqualTo("example");

        Metadata generated = Metadata.newBuilder()
                .groupId(metadata.getGroupId())
                .artifactId(metadata.getArtifactId())
                .versioning(Versioning.newBuilder()
                        .latest("3.0.0")
                        .release("3.0.0")
                        .versions(List.of("2.0.0", "3.0.0"))
                        .lastUpdated("20260202120000")
                        .build())
                .plugins(List.of(org.apache.maven.api.metadata.Plugin.newBuilder()
                        .prefix("fmt")
                        .name("Formatter")
                        .artifactId("formatter-maven-plugin")
                        .build()))
                .build();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new MetadataStaxWriter().write(output, generated);

        Metadata reparsed = new MetadataStaxReader().read(new ByteArrayInputStream(output.toByteArray()));
        assertThat(reparsed.getVersioning().getLatest()).isEqualTo("3.0.0");
        assertThat(reparsed.getPlugins().get(0).getArtifactId()).isEqualTo("formatter-maven-plugin");
    }

    @Test
    void readsWritesSettingsAndAppliesMergeAndTransformation() throws Exception {
        String xml = """
                <settings>
                  <localRepository>/tmp/repository</localRepository>
                  <offline>true</offline>
                  <servers>
                    <server>
                      <id>central</id>
                      <username>reader</username>
                      <password>secret</password>
                    </server>
                  </servers>
                  <activeProfiles>
                    <activeProfile>native</activeProfile>
                  </activeProfiles>
                  <pluginGroups>
                    <pluginGroup>org.example.plugins</pluginGroup>
                  </pluginGroups>
                </settings>
                """;

        SettingsStaxReader reader = new SettingsStaxReader((value, fieldName) -> {
            if ("username".equals(fieldName)) {
                return value.toUpperCase();
            }
            return value;
        });
        reader.setAddLocationInformation(true);
        org.apache.maven.api.settings.InputSource inputSource =
                new org.apache.maven.api.settings.InputSource("settings.xml");
        Settings settings = reader.read(new StringReader(xml), true, inputSource);
        assertThat(settings.getLocalRepository()).isEqualTo("/tmp/repository");
        assertThat(settings.isOffline()).isTrue();
        assertThat(settings.getServers().get(0).getUsername()).isEqualTo("READER");
        assertThat(settings.getActiveProfiles()).containsExactly("native");
        assertThat(settings.getLocation("servers").getLineNumber()).isEqualTo(4);

        Settings source = Settings.newBuilder()
                .localRepository("/source/repository")
                .servers(List.of(Server.newBuilder().id("central").username("source").password("override").build()))
                .pluginGroups(List.of("org.source.plugins"))
                .build();
        Settings merged = new SettingsMerger().merge(settings, source, true, Map.of());
        assertThat(merged.getLocalRepository()).isEqualTo("/source/repository");
        assertThat(merged.getServers()).extracting(Server::getUsername).containsExactly("READER", "source");
        assertThat(merged.getPluginGroups()).containsExactly("org.example.plugins", "org.source.plugins");

        Settings transformed = new SettingsTransformer(value -> value == null
                ? null
                : value.replace("source", "transformed"))
                .visit(merged);
        assertThat(transformed.getServers()).extracting(Server::getUsername).containsExactly("READER", "transformed");

        StringWriter writer = new StringWriter();
        new SettingsStaxWriter().write(writer, transformed);
        Settings reparsed = new SettingsStaxReader().read(new StringReader(writer.toString()));
        assertThat(reparsed.getServers()).extracting(Server::getPassword).containsExactly("secret", "override");
    }

    @Test
    void readsWritesToolchainsAndAppliesMergeAndTransformation() throws Exception {
        String xml = """
                <toolchains>
                  <toolchain>
                    <type>jdk</type>
                    <provides>
                      <version>21</version>
                      <vendor>graalvm</vendor>
                    </provides>
                  </toolchain>
                </toolchains>
                """;

        MavenToolchainsStaxReader reader = new MavenToolchainsStaxReader((value, fieldName) -> {
            if ("type".equals(fieldName)) {
                return value.toUpperCase();
            }
            return value;
        });
        reader.setAddLocationInformation(true);
        PersistedToolchains toolchains = reader.read(new StringReader(xml), true,
                new org.apache.maven.api.toolchain.InputSource("toolchains.xml"));
        ToolchainModel toolchain = toolchains.getToolchains().get(0);
        assertThat(toolchain.getType()).isEqualTo("JDK");
        assertThat(toolchain.getProvides()).containsEntry("version", "21").containsEntry("vendor", "graalvm");
        assertThat(toolchain.getLocation("provides").getLocation("vendor").getLineNumber()).isEqualTo(6);

        PersistedToolchains source = PersistedToolchains.newBuilder()
                .toolchains(List.of(ToolchainModel.newBuilder()
                        .type("JDK")
                        .provides(Map.of("version", "22", "runtime", "native-image"))
                        .build()))
                .build();
        PersistedToolchains merged = new MavenToolchainsMerger().merge(toolchains, source, true, Map.of());
        assertThat(merged.getToolchains()).hasSize(2);
        assertThat(merged.getToolchains().get(0).getProvides()).containsEntry("vendor", "graalvm");
        assertThat(merged.getToolchains().get(1).getProvides())
                .containsEntry("version", "22")
                .containsEntry("runtime", "native-image");

        List<ToolchainModel> keyedMerge = MavenToolchainsMerger.merge(
                List.of(toolchain),
                source.getToolchains(),
                ToolchainModel::getType,
                (target, dominantSource) -> dominantSource.withProvides(
                        Map.of("version", dominantSource.getProvides().get("version"), "vendor", "graalvm")));
        assertThat(keyedMerge).hasSize(1);
        assertThat(keyedMerge.get(0).getProvides()).containsEntry("version", "22").containsEntry("vendor", "graalvm");

        PersistedToolchains transformed = new MavenToolchainsTransformer(value -> value == null ? null : value + "-x")
                .visit(PersistedToolchains.newBuilder().toolchains(keyedMerge).build());
        assertThat(transformed.getToolchains().get(0).getType()).isEqualTo("JDK-x");
        assertThat(transformed.getToolchains().get(0).getProvides().get("vendor")).isEqualTo("graalvm-x");

        StringWriter writer = new StringWriter();
        new MavenToolchainsStaxWriter().write(writer, transformed);
        PersistedToolchains reparsed = new MavenToolchainsStaxReader().read(new StringReader(writer.toString()));
        assertThat(reparsed.getToolchains().get(0).getProvides().get("version")).isEqualTo("22-x");
    }

    @Test
    void readsWritesPluginDescriptors() throws Exception {
        String xml = """
                <plugin>
                  <name>Example Maven Plugin</name>
                  <description>Runs an example goal</description>
                  <groupId>org.example</groupId>
                  <artifactId>example-maven-plugin</artifactId>
                  <version>1.0.0</version>
                  <goalPrefix>example</goalPrefix>
                  <isolatedRealm>true</isolatedRealm>
                  <inheritedByDefault>false</inheritedByDefault>
                  <requiredJavaVersion>21</requiredJavaVersion>
                  <requiredMavenVersion>4.0.0</requiredMavenVersion>
                  <mojos>
                    <mojo>
                      <goal>run</goal>
                      <description>Run the example</description>
                      <implementation>org.example.RunMojo</implementation>
                      <language>java</language>
                      <phase>verify</phase>
                      <projectRequired>true</projectRequired>
                      <onlineRequired>false</onlineRequired>
                      <aggregator>true</aggregator>
                    </mojo>
                  </mojos>
                </plugin>
                """;

        PluginDescriptor descriptor = new PluginDescriptorStaxReader((value, fieldName) -> {
            if ("goalPrefix".equals(fieldName)) {
                return value + "-prefix";
            }
            return value;
        }).read(new StringReader(xml), true);
        assertThat(descriptor.getPluginLookupKey()).isEqualTo("org.example:example-maven-plugin");
        assertThat(descriptor.getGoalPrefix()).isEqualTo("example-prefix");
        assertThat(descriptor.isIsolatedRealm()).isTrue();
        assertThat(descriptor.getMojos().get(0).getFullGoalName()).isEqualTo("example-prefix:run");
        assertThat(descriptor.getMojos().get(0).isAggregator()).isTrue();

        PluginDescriptor generated = PluginDescriptor.newBuilder()
                .name("Generated Plugin")
                .groupId("org.example")
                .artifactId("generated-maven-plugin")
                .version("2.0.0")
                .goalPrefix("generated")
                .mojos(List.of(MojoDescriptor.newBuilder()
                        .goal("format")
                        .implementation("org.example.FormatMojo")
                        .language("java")
                        .phase("process-sources")
                        .projectRequired(true)
                        .build()))
                .build();
        StringWriter writer = new StringWriter();
        new PluginDescriptorStaxWriter().write(writer, generated);
        PluginDescriptor reparsed = new PluginDescriptorStaxReader().read(new StringReader(writer.toString()));
        assertThat(reparsed.getMojos().get(0).getPhase()).isEqualTo("process-sources");
    }

    @Test
    void readsWritesLifecycleMappings() throws Exception {
        String xml = """
                <lifecycles>
                  <lifecycle>
                    <id>default</id>
                    <phases>
                      <phase executionPoint="before" priority="5">
                        <id>compile</id>
                        <executions>
                          <execution>
                            <goals>
                              <goal>resources</goal>
                              <goal>compile</goal>
                            </goals>
                          </execution>
                        </executions>
                      </phase>
                    </phases>
                  </lifecycle>
                </lifecycles>
                """;

        LifecycleConfiguration configuration = new LifecycleStaxReader((value, fieldName) -> {
            if ("id".equals(fieldName)) {
                return value.toUpperCase();
            }
            return value;
        }).read(new StringReader(xml), true);
        Lifecycle lifecycle = configuration.getLifecycles().get(0);
        Phase phase = lifecycle.getPhases().get(0);
        assertThat(lifecycle.getId()).isEqualTo("DEFAULT");
        assertThat(phase.getEffectiveId()).isEqualTo("before:COMPILE[5]");
        assertThat(phase.getExecutions().get(0).getGoals()).containsExactly("resources", "compile");

        LifecycleConfiguration generated = LifecycleConfiguration.newBuilder()
                .lifecycles(List.of(Lifecycle.newBuilder()
                        .id("site")
                        .phases(List.of(Phase.newBuilder()
                                .id("deploy")
                                .executionPoint("after")
                                .priority(10)
                                .executions(List.of(Execution.newBuilder()
                                        .goals(List.of("site", "deploy"))
                                        .build()))
                                .build()))
                        .build()))
                .build();
        StringWriter writer = new StringWriter();
        new LifecycleStaxWriter().write(writer, generated);
        LifecycleConfiguration reparsed = new LifecycleStaxReader().read(new StringReader(writer.toString()));
        assertThat(reparsed.getLifecycles().get(0).getPhases().get(0).getEffectiveId()).isEqualTo("after:deploy[10]");
    }

    @Test
    void reportsMalformedDocumentRoots() {
        assertThatThrownBy(() -> new MetadataStaxReader().read(new StringReader("<notMetadata/>")))
                .isInstanceOf(XMLStreamException.class)
                .hasMessageContaining("Expected root element 'metadata'");
        assertThatThrownBy(() -> new PluginDescriptorStaxReader().read(new StringReader("<notPlugin/>")))
                .isInstanceOf(XMLStreamException.class)
                .hasMessageContaining("Expected root element 'plugin'");
        assertThatThrownBy(() -> new LifecycleStaxReader().read(new StringReader("<notLifecycles/>")))
                .isInstanceOf(XMLStreamException.class)
                .hasMessageContaining("Expected root element 'lifecycles'");
    }
}
