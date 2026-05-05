/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_model;

import org.apache.maven.model.Activation;
import org.apache.maven.model.ActivationFile;
import org.apache.maven.model.ActivationOS;
import org.apache.maven.model.ActivationProperty;
import org.apache.maven.model.Build;
import org.apache.maven.model.BuildBase;
import org.apache.maven.model.CiManagement;
import org.apache.maven.model.Contributor;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.DeploymentRepository;
import org.apache.maven.model.Developer;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Extension;
import org.apache.maven.model.IssueManagement;
import org.apache.maven.model.License;
import org.apache.maven.model.MailingList;
import org.apache.maven.model.Model;
import org.apache.maven.model.Notifier;
import org.apache.maven.model.Organization;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.Prerequisites;
import org.apache.maven.model.Profile;
import org.apache.maven.model.Relocation;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.ReportSet;
import org.apache.maven.model.Reporting;
import org.apache.maven.model.Repository;
import org.apache.maven.model.RepositoryPolicy;
import org.apache.maven.model.Resource;
import org.apache.maven.model.Scm;
import org.apache.maven.model.Site;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Maven_modelTest {
    @Test
    void readsRichPomIntoCompleteModelGraph() throws Exception {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        reader.setAddDefaultEntities(true);

        Model model = reader.read(new StringReader(richPomXml()));

        assertThat(reader.getAddDefaultEntities()).isTrue();
        assertThat(model.getModelVersion()).isEqualTo("4.0.0");
        assertThat(model.getId()).isEqualTo("org.example:demo-app:jar:1.0.0");
        assertThat(model.getName()).isEqualTo("Demo Application");
        assertThat(model.getDescription()).isEqualTo("A project model used by reachability tests.");
        assertThat(model.getUrl()).isEqualTo("https://example.org/demo");
        assertThat(model.getInceptionYear()).isEqualTo("2006");

        Parent parent = model.getParent();
        assertThat(parent.getId()).isEqualTo("org.example:demo-parent::1.0");
        assertThat(parent.getRelativePath()).isEqualTo("../pom.xml");

        Organization organization = model.getOrganization();
        assertThat(organization.getName()).isEqualTo("Example Foundation");
        assertThat(organization.getUrl()).isEqualTo("https://example.org");

        License license = (License) model.getLicenses().get(0);
        assertThat(license.getName()).isEqualTo("Apache-2.0");
        assertThat(license.getDistribution()).isEqualTo("repo");
        assertThat(license.getComments()).isEqualTo("Permissive license");

        Developer developer = (Developer) model.getDevelopers().get(0);
        assertThat(developer.getId()).isEqualTo("dev1");
        assertThat(developer.getName()).isEqualTo("Ada Lovelace");
        assertThat(developer.getRoles()).containsExactly("architect", "maintainer");
        assertThat(developer.getProperties()).containsEntry("team", "core");

        Contributor contributor = (Contributor) model.getContributors().get(0);
        assertThat(contributor.getName()).isEqualTo("Grace Hopper");
        assertThat(contributor.getRoles()).containsExactly("reviewer");

        MailingList mailingList = (MailingList) model.getMailingLists().get(0);
        assertThat(mailingList.getOtherArchives()).containsExactly("https://example.org/archive/dev");

        assertThat(model.getPrerequisites().getMaven()).isEqualTo("2.0");
        assertThat(model.getScm().getTag()).isEqualTo("demo-app-1.0.0");
        assertThat(model.getIssueManagement().getSystem()).isEqualTo("GitHub");
        assertThat(model.getModules()).containsExactly("core", "cli");
        assertThat(model.getProperties()).containsEntry("project.build.sourceEncoding", "UTF-8")
                .containsEntry("feature.flag", "true");

        CiManagement ciManagement = model.getCiManagement();
        Notifier notifier = (Notifier) ciManagement.getNotifiers().get(0);
        assertThat(ciManagement.getSystem()).isEqualTo("Hudson");
        assertThat(notifier.getType()).isEqualTo("mail");
        assertThat(notifier.isSendOnError()).isTrue();
        assertThat(notifier.isSendOnFailure()).isTrue();
        assertThat(notifier.isSendOnSuccess()).isFalse();
        assertThat(notifier.isSendOnWarning()).isTrue();
        assertThat(notifier.getConfiguration()).containsEntry("committers", "true");

        DistributionManagement distributionManagement = model.getDistributionManagement();
        assertThat(distributionManagement.getRepository().isUniqueVersion()).isFalse();
        assertThat(distributionManagement.getSnapshotRepository().isUniqueVersion()).isTrue();
        assertThat(distributionManagement.getSite().getUrl()).isEqualTo("scp://example.org/site");
        assertThat(distributionManagement.getDownloadUrl()).isEqualTo("https://example.org/downloads");
        assertThat(distributionManagement.getRelocation().getMessage()).isEqualTo("Use the new coordinates");
        assertThat(distributionManagement.getStatus()).isEqualTo("deployed");

        Dependency dependency = (Dependency) model.getDependencies().get(0);
        assertThat(dependency.getManagementKey()).isEqualTo("commons-io:commons-io:jar:tests");
        assertThat(dependency.isOptional()).isTrue();
        Exclusion exclusion = (Exclusion) dependency.getExclusions().get(0);
        assertThat(exclusion.getGroupId()).isEqualTo("log4j");
        assertThat(exclusion.getArtifactId()).isEqualTo("log4j");

        Dependency managedDependency = (Dependency) model.getDependencyManagement().getDependencies().get(0);
        assertThat(managedDependency.getManagementKey()).isEqualTo("junit:junit:jar");

        Repository repository = (Repository) model.getRepositories().get(0);
        assertRepository(repository, "central", "https://repo.maven.apache.org/maven2", true, false);
        Repository pluginRepository = (Repository) model.getPluginRepositories().get(0);
        assertRepository(pluginRepository, "plugin-releases", "https://example.org/plugins", true, true);

        Build build = model.getBuild();
        assertThat(build.getSourceDirectory()).isEqualTo("src/main/java");
        assertThat(build.getScriptSourceDirectory()).isEqualTo("src/main/scripts");
        assertThat(build.getTestSourceDirectory()).isEqualTo("src/test/java");
        assertThat(build.getOutputDirectory()).isEqualTo("target/classes");
        assertThat(build.getTestOutputDirectory()).isEqualTo("target/test-classes");
        assertThat(build.getDefaultGoal()).isEqualTo("verify");
        assertThat(build.getFilters()).containsExactly("src/main/filters/filter.properties");
        assertThat(build.getExtensions()).hasSize(1);
        assertResource((Resource) build.getResources().get(0), "src/main/resources", true);
        assertResource((Resource) build.getTestResources().get(0), "src/test/resources", false);

        Plugin plugin = (Plugin) build.getPlugins().get(0);
        assertThat(plugin.getKey()).isEqualTo("org.apache.maven.plugins:maven-compiler-plugin");
        assertThat(plugin.isExtensions()).isFalse();
        assertThat(configuration(plugin).getChild("source").getValue()).isEqualTo("1.5");
        assertThat(plugin.getExecutionsAsMap()).containsKey("compile-java");
        PluginExecution execution = (PluginExecution) plugin.getExecutionsAsMap().get("compile-java");
        assertThat(execution.getPhase()).isEqualTo("compile");
        assertThat(execution.getGoals()).containsExactly("compile", "testCompile");
        Dependency pluginDependency = (Dependency) plugin.getDependencies().get(0);
        assertThat(pluginDependency.getManagementKey()).isEqualTo("org.ow2.asm:asm:jar");

        Plugin managedPlugin = (Plugin) build.getPluginManagement().getPlugins().get(0);
        assertThat(managedPlugin.getKey()).isEqualTo("org.apache.maven.plugins:maven-surefire-plugin");

        Reporting reporting = model.getReporting();
        assertThat(reporting.isExcludeDefaults()).isTrue();
        assertThat(reporting.getOutputDirectory()).isEqualTo("target/site");
        assertThat(reporting.getReportPluginsAsMap()).containsKey("org.apache.maven.plugins:maven-javadoc-plugin");
        ReportPlugin reportPlugin = (ReportPlugin) reporting.getReportPluginsAsMap()
                .get("org.apache.maven.plugins:maven-javadoc-plugin");
        assertThat(reportPlugin.getInherited()).isEqualTo("false");
        assertThat(reportPlugin.isInheritanceApplied()).isTrue();
        assertThat(reportPlugin.getReportSetsAsMap()).containsKey("aggregate");
        ReportSet reportSet = (ReportSet) reportPlugin.getReportSetsAsMap().get("aggregate");
        assertThat(reportSet.getInherited()).isEqualTo("false");
        assertThat(reportSet.isInheritanceApplied()).isTrue();
        assertThat(reportSet.getReports()).containsExactly("javadoc", "test-javadoc");

        Profile profile = (Profile) model.getProfiles().get(0);
        assertThat(profile.getId()).isEqualTo("native");
        assertActivation(profile.getActivation());
        assertThat(profile.getBuild().getDefaultGoal()).isEqualTo("package");
        assertThat(profile.getRepositories()).hasSize(1);
        assertThat(((Dependency) profile.getDependencies().get(0)).getArtifactId()).isEqualTo("profile-lib");
    }

    @Test
    void readerStrictModeRejectsUnknownPomElementsWhileLenientModeSkipsThem() throws Exception {
        String pomXml = """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>lenient-reader</artifactId>
                  <version>1.0</version>
                  <unexpectedMetadata>
                    <generatedBy>test-suite</generatedBy>
                  </unexpectedMetadata>
                </project>
                """;

        Model lenientModel = new MavenXpp3Reader().read(new StringReader(pomXml), false);

        assertThat(lenientModel.getId()).isEqualTo("org.example:lenient-reader:jar:1.0");
        assertThrows(XmlPullParserException.class,
                () -> new MavenXpp3Reader().read(new StringReader(pomXml), true));
    }

    @Test
    void readerResolvesDefaultEntityReferencesWhenEnabled() throws Exception {
        String pomXml = """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>entity-reader</artifactId>
                  <version>1.0</version>
                  <description>Copyright &copy; Example &mdash; all rights reserved</description>
                </project>
                """;
        MavenXpp3Reader reader = new MavenXpp3Reader();

        Model model = reader.read(new StringReader(pomXml));

        assertThat(reader.getAddDefaultEntities()).isTrue();
        assertThat(model.getDescription()).isEqualTo("Copyright © Example — all rights reserved");

        MavenXpp3Reader readerWithoutDefaultEntities = new MavenXpp3Reader();
        readerWithoutDefaultEntities.setAddDefaultEntities(false);

        assertThat(readerWithoutDefaultEntities.getAddDefaultEntities()).isFalse();
        assertThrows(XmlPullParserException.class,
                () -> readerWithoutDefaultEntities.read(new StringReader(pomXml)));
    }

    @Test
    void writesProgrammaticModelAndReadsItBack() throws Exception {
        Model model = new Model();
        model.setModelEncoding("UTF-8");
        model.setModelVersion("4.0.0");
        model.setGroupId("org.example.generated");
        model.setArtifactId("generated-model");
        model.setPackaging("pom");
        model.setVersion("2.0.0");
        model.setName("Generated Model");
        model.setDescription("Model created through the public Maven model API.");
        model.setUrl("https://example.org/generated");
        model.addModule("api");
        model.addModule("impl");
        model.removeModule("impl");
        model.addProperty("encoding", "UTF-8");
        model.setOrganization(organization("Generated Org", "https://example.org/org"));
        model.addLicense(license("EPL-2.0"));
        model.addDeveloper(developer("dev2", "Katherine Johnson"));
        model.addContributor(contributor("Dorothy Vaughan"));
        model.addMailingList(mailingList());
        model.setParent(parent());
        model.setPrerequisites(prerequisites());
        model.setScm(scm());
        model.setIssueManagement(issueManagement());
        model.setCiManagement(ciManagement());
        model.setDistributionManagement(distributionManagement());
        model.setDependencyManagement(dependencyManagement());
        model.addDependency(dependency("org.example", "runtime-lib", "1.1", "runtime"));
        model.addRepository(repository("repo", "https://example.org/repo", true, false));
        model.addPluginRepository(repository("plugin-repo", "https://example.org/plugin-repo", true, true));
        model.setBuild(build());
        model.setReporting(reporting());
        model.addProfile(profile());

        StringWriter writer = new StringWriter();
        new MavenXpp3Writer().write(writer, model);
        String xml = writer.toString();

        assertThat(xml).contains("<artifactId>generated-model</artifactId>")
                .contains("<module>api</module>")
                .doesNotContain("<module>impl</module>")
                .contains("<project>");

        Model roundTripped = new MavenXpp3Reader().read(new StringReader(xml));

        assertThat(roundTripped.getId()).isEqualTo("org.example.generated:generated-model:pom:2.0.0");
        assertThat(roundTripped.getModules()).containsExactly("api");
        assertThat(roundTripped.getProperties()).containsEntry("encoding", "UTF-8");
        assertThat(roundTripped.getOrganization().getName()).isEqualTo("Generated Org");
        assertThat(((License) roundTripped.getLicenses().get(0)).getName()).isEqualTo("EPL-2.0");
        assertThat(((Developer) roundTripped.getDevelopers().get(0)).getId()).isEqualTo("dev2");
        assertThat(((Contributor) roundTripped.getContributors().get(0)).getName()).isEqualTo("Dorothy Vaughan");
        assertThat(roundTripped.getParent().getId()).isEqualTo("org.example:demo-parent::1.0");
        assertThat(roundTripped.getDistributionManagement().getRelocation().getArtifactId()).isEqualTo("new-artifact");
        assertThat(roundTripped.getBuild().getPluginManagement().getPlugins()).hasSize(1);
        assertThat(((Plugin) roundTripped.getBuild().getPlugins().get(0)).getExecutionsAsMap())
                .containsKey("generate-sources");
        assertThat(roundTripped.getReporting().getReportPluginsAsMap())
                .containsKey("org.apache.maven.plugins:maven-project-info-reports-plugin");
        assertThat(((Profile) roundTripped.getProfiles().get(0)).getActivation().getProperty().getName())
                .isEqualTo("env.NATIVE");
    }

    @Test
    void collectionMutatorsAndMapViewsTrackModelChanges() {
        Dependency dependency = dependency("org.example", "lib", "1.0", "compile");
        Exclusion exclusion = new Exclusion();
        exclusion.setGroupId("bad.group");
        exclusion.setArtifactId("bad-artifact");
        dependency.addExclusion(exclusion);
        assertThat(dependency.getExclusions()).containsExactly(exclusion);
        dependency.removeExclusion(exclusion);
        assertThat(dependency.getExclusions()).isEmpty();

        Plugin plugin = plugin("org.example.plugins", "codegen-plugin", "1.0");
        PluginExecution execution = pluginExecution("codegen", "generate-sources", "generate");
        plugin.addExecution(execution);
        assertThat(plugin.getExecutionsAsMap()).containsEntry("codegen", execution);
        plugin.removeExecution(execution);
        plugin.flushExecutionMap();
        assertThat(plugin.getExecutionsAsMap()).isEmpty();
        assertThat(Plugin.constructKey("org.example.plugins", "codegen-plugin"))
                .isEqualTo(plugin.getKey());

        Build build = new Build();
        build.addFilter("filter-one.properties");
        build.addFilter("filter-two.properties");
        build.removeFilter("filter-two.properties");
        assertThat(build.getFilters()).containsExactly("filter-one.properties");
        Resource resource = resource("src/main/resources", true);
        build.addResource(resource);
        build.removeResource(resource);
        assertThat(build.getResources()).isEmpty();

        PluginManagement pluginManagement = new PluginManagement();
        pluginManagement.addPlugin(plugin);
        assertThat(pluginManagement.getPluginsAsMap()).containsEntry(plugin.getKey(), plugin);
        pluginManagement.removePlugin(plugin);
        pluginManagement.flushPluginMap();
        assertThat(pluginManagement.getPluginsAsMap()).isEmpty();

        Reporting reporting = new Reporting();
        ReportPlugin reportPlugin = new ReportPlugin();
        reportPlugin.setGroupId("org.apache.maven.plugins");
        reportPlugin.setArtifactId("maven-site-plugin");
        reportPlugin.setVersion("2.0");
        reporting.addPlugin(reportPlugin);
        Map reportPluginMap = reporting.getReportPluginsAsMap();
        assertThat(reportPluginMap).containsEntry(reportPlugin.getKey(), reportPlugin);
        reporting.removePlugin(reportPlugin);
        reporting.flushReportPluginMap();
        assertThat(reporting.getReportPluginsAsMap()).isEmpty();

        ReportSet reportSet = reportSet();
        reportPlugin.addReportSet(reportSet);
        assertThat(reportPlugin.getReportSetsAsMap()).containsEntry("aggregate", reportSet);
        reportPlugin.removeReportSet(reportSet);
        reportPlugin.flushReportSetMap();
        assertThat(reportPlugin.getReportSetsAsMap()).isEmpty();

        CiManagement ciManagement = new CiManagement();
        Notifier notifier = notifier();
        ciManagement.addNotifier(notifier);
        assertThat(ciManagement.getNotifiers()).containsExactly(notifier);
        ciManagement.removeNotifier(notifier);
        assertThat(ciManagement.getNotifiers()).isEmpty();
    }

    private static String richPomXml() {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                    <groupId>org.example</groupId>
                    <artifactId>demo-parent</artifactId>
                    <version>1.0</version>
                    <relativePath>../pom.xml</relativePath>
                  </parent>
                  <groupId>org.example</groupId>
                  <artifactId>demo-app</artifactId>
                  <packaging>jar</packaging>
                  <name>Demo Application</name>
                  <version>1.0.0</version>
                  <description>A project model used by reachability tests.</description>
                  <url>https://example.org/demo</url>
                  <inceptionYear>2006</inceptionYear>
                  <organization>
                    <name>Example Foundation</name>
                    <url>https://example.org</url>
                  </organization>
                  <licenses>
                    <license>
                      <name>Apache-2.0</name>
                      <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>
                      <distribution>repo</distribution>
                      <comments>Permissive license</comments>
                    </license>
                  </licenses>
                  <developers>
                    <developer>
                      <id>dev1</id>
                      <name>Ada Lovelace</name>
                      <email>ada@example.org</email>
                      <url>https://example.org/ada</url>
                      <organization>Example Foundation</organization>
                      <organizationUrl>https://example.org</organizationUrl>
                      <roles>
                        <role>architect</role>
                        <role>maintainer</role>
                      </roles>
                      <timezone>UTC</timezone>
                      <properties>
                        <team>core</team>
                      </properties>
                    </developer>
                  </developers>
                  <contributors>
                    <contributor>
                      <name>Grace Hopper</name>
                      <email>grace@example.org</email>
                      <roles>
                        <role>reviewer</role>
                      </roles>
                      <timezone>-5</timezone>
                    </contributor>
                  </contributors>
                  <mailingLists>
                    <mailingList>
                      <name>Users</name>
                      <subscribe>users-subscribe@example.org</subscribe>
                      <unsubscribe>users-unsubscribe@example.org</unsubscribe>
                      <post>users@example.org</post>
                      <archive>https://example.org/archive/users</archive>
                      <otherArchives>
                        <otherArchive>https://example.org/archive/dev</otherArchive>
                      </otherArchives>
                    </mailingList>
                  </mailingLists>
                  <prerequisites>
                    <maven>2.0</maven>
                  </prerequisites>
                  <modules>
                    <module>core</module>
                    <module>cli</module>
                  </modules>
                  <scm>
                    <connection>scm:git:https://example.org/demo.git</connection>
                    <developerConnection>scm:git:ssh://example.org/demo.git</developerConnection>
                    <tag>demo-app-1.0.0</tag>
                    <url>https://example.org/demo/scm</url>
                  </scm>
                  <issueManagement>
                    <system>GitHub</system>
                    <url>https://example.org/demo/issues</url>
                  </issueManagement>
                  <ciManagement>
                    <system>Hudson</system>
                    <url>https://ci.example.org/demo</url>
                    <notifiers>
                      <notifier>
                        <type>mail</type>
                        <sendOnError>true</sendOnError>
                        <sendOnFailure>true</sendOnFailure>
                        <sendOnSuccess>false</sendOnSuccess>
                        <sendOnWarning>true</sendOnWarning>
                        <address>builds@example.org</address>
                        <configuration>
                          <committers>true</committers>
                        </configuration>
                      </notifier>
                    </notifiers>
                  </ciManagement>
                  <distributionManagement>
                    <repository>
                      <uniqueVersion>false</uniqueVersion>
                      <id>releases</id>
                      <name>Release Repository</name>
                      <url>scp://example.org/releases</url>
                      <layout>default</layout>
                    </repository>
                    <snapshotRepository>
                      <uniqueVersion>true</uniqueVersion>
                      <id>snapshots</id>
                      <name>Snapshot Repository</name>
                      <url>scp://example.org/snapshots</url>
                      <layout>legacy</layout>
                    </snapshotRepository>
                    <site>
                      <id>site</id>
                      <name>Project Site</name>
                      <url>scp://example.org/site</url>
                    </site>
                    <downloadUrl>https://example.org/downloads</downloadUrl>
                    <relocation>
                      <groupId>org.example.new</groupId>
                      <artifactId>demo-new</artifactId>
                      <version>2.0.0</version>
                      <message>Use the new coordinates</message>
                    </relocation>
                    <status>deployed</status>
                  </distributionManagement>
                  <properties>
                    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                    <feature.flag>true</feature.flag>
                  </properties>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>4.13.2</version>
                        <type>jar</type>
                        <scope>test</scope>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>commons-io</groupId>
                      <artifactId>commons-io</artifactId>
                      <version>2.11.0</version>
                      <type>jar</type>
                      <classifier>tests</classifier>
                      <scope>system</scope>
                      <systemPath>/opt/libs/commons-io-tests.jar</systemPath>
                      <optional>true</optional>
                      <exclusions>
                        <exclusion>
                          <groupId>log4j</groupId>
                          <artifactId>log4j</artifactId>
                        </exclusion>
                      </exclusions>
                    </dependency>
                  </dependencies>
                  <repositories>
                    <repository>
                      <releases>
                        <enabled>true</enabled>
                        <updatePolicy>daily</updatePolicy>
                        <checksumPolicy>warn</checksumPolicy>
                      </releases>
                      <snapshots>
                        <enabled>false</enabled>
                        <updatePolicy>never</updatePolicy>
                        <checksumPolicy>fail</checksumPolicy>
                      </snapshots>
                      <id>central</id>
                      <name>Maven Central</name>
                      <url>https://repo.maven.apache.org/maven2</url>
                      <layout>default</layout>
                    </repository>
                  </repositories>
                  <pluginRepositories>
                    <pluginRepository>
                      <releases>
                        <enabled>true</enabled>
                      </releases>
                      <snapshots>
                        <enabled>true</enabled>
                      </snapshots>
                      <id>plugin-releases</id>
                      <name>Plugin Repository</name>
                      <url>https://example.org/plugins</url>
                      <layout>default</layout>
                    </pluginRepository>
                  </pluginRepositories>
                  <build>
                    <sourceDirectory>src/main/java</sourceDirectory>
                    <scriptSourceDirectory>src/main/scripts</scriptSourceDirectory>
                    <testSourceDirectory>src/test/java</testSourceDirectory>
                    <outputDirectory>target/classes</outputDirectory>
                    <testOutputDirectory>target/test-classes</testOutputDirectory>
                    <extensions>
                      <extension>
                        <groupId>org.example</groupId>
                        <artifactId>build-extension</artifactId>
                        <version>1.0</version>
                      </extension>
                    </extensions>
                    <defaultGoal>verify</defaultGoal>
                    <resources>
                      <resource>
                        <targetPath>classes</targetPath>
                        <filtering>true</filtering>
                        <directory>src/main/resources</directory>
                        <includes>
                          <include>**/*.properties</include>
                        </includes>
                        <excludes>
                          <exclude>**/*.tmp</exclude>
                        </excludes>
                      </resource>
                    </resources>
                    <testResources>
                      <testResource>
                        <targetPath>test-classes</targetPath>
                        <filtering>false</filtering>
                        <directory>src/test/resources</directory>
                        <includes>
                          <include>**/*.xml</include>
                        </includes>
                      </testResource>
                    </testResources>
                    <directory>target</directory>
                    <finalName>demo-app</finalName>
                    <filters>
                      <filter>src/main/filters/filter.properties</filter>
                    </filters>
                    <pluginManagement>
                      <plugins>
                        <plugin>
                          <groupId>org.apache.maven.plugins</groupId>
                          <artifactId>maven-surefire-plugin</artifactId>
                          <version>2.4.3</version>
                        </plugin>
                      </plugins>
                    </pluginManagement>
                    <plugins>
                      <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-compiler-plugin</artifactId>
                        <version>2.0.2</version>
                        <extensions>false</extensions>
                        <inherited>false</inherited>
                        <configuration>
                          <source>1.5</source>
                          <target>1.5</target>
                        </configuration>
                        <executions>
                          <execution>
                            <id>compile-java</id>
                            <phase>compile</phase>
                            <goals>
                              <goal>compile</goal>
                              <goal>testCompile</goal>
                            </goals>
                            <inherited>true</inherited>
                            <configuration>
                              <debug>true</debug>
                            </configuration>
                          </execution>
                        </executions>
                        <dependencies>
                          <dependency>
                            <groupId>org.ow2.asm</groupId>
                            <artifactId>asm</artifactId>
                            <version>5.0.4</version>
                            <type>jar</type>
                          </dependency>
                        </dependencies>
                        <goals>compile</goals>
                      </plugin>
                    </plugins>
                  </build>
                  <reporting>
                    <excludeDefaults>true</excludeDefaults>
                    <outputDirectory>target/site</outputDirectory>
                    <plugins>
                      <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-javadoc-plugin</artifactId>
                        <version>2.8</version>
                        <inherited>false</inherited>
                        <configuration>
                          <quiet>true</quiet>
                        </configuration>
                        <reportSets>
                          <reportSet>
                            <id>aggregate</id>
                            <reports>
                              <report>javadoc</report>
                              <report>test-javadoc</report>
                            </reports>
                            <inherited>false</inherited>
                            <configuration>
                              <aggregate>true</aggregate>
                            </configuration>
                          </reportSet>
                        </reportSets>
                      </plugin>
                    </plugins>
                  </reporting>
                  <profiles>
                    <profile>
                      <id>native</id>
                      <activation>
                        <activeByDefault>false</activeByDefault>
                        <jdk>[1.5,)</jdk>
                        <os>
                          <name>Linux</name>
                          <family>unix</family>
                          <arch>x86_64</arch>
                          <version>5</version>
                        </os>
                        <property>
                          <name>env.NATIVE</name>
                          <value>true</value>
                        </property>
                        <file>
                          <exists>pom.xml</exists>
                          <missing>missing.file</missing>
                        </file>
                      </activation>
                      <build>
                        <defaultGoal>package</defaultGoal>
                      </build>
                      <repositories>
                        <repository>
                          <id>native-repo</id>
                          <url>https://example.org/native</url>
                        </repository>
                      </repositories>
                      <dependencies>
                        <dependency>
                          <groupId>org.example</groupId>
                          <artifactId>profile-lib</artifactId>
                          <version>1.0</version>
                          <type>jar</type>
                        </dependency>
                      </dependencies>
                      <properties>
                        <profile.active>true</profile.active>
                      </properties>
                    </profile>
                  </profiles>
                </project>
                """;
    }

    private static void assertRepository(Repository repository, String id, String url, boolean releases,
            boolean snapshots) {
        assertThat(repository.getId()).isEqualTo(id);
        assertThat(repository.getUrl()).isEqualTo(url);
        assertThat(repository.getReleases().isEnabled()).isEqualTo(releases);
        assertThat(repository.getSnapshots().isEnabled()).isEqualTo(snapshots);
    }

    private static void assertResource(Resource resource, String directory, boolean filtering) {
        assertThat(resource.getDirectory()).isEqualTo(directory);
        assertThat(resource.isFiltering()).isEqualTo(filtering);
        assertThat(resource.getIncludes()).isNotEmpty();
    }

    private static void assertActivation(Activation activation) {
        assertThat(activation.isActiveByDefault()).isFalse();
        assertThat(activation.getJdk()).isEqualTo("[1.5,)");
        assertThat(activation.getOs().getFamily()).isEqualTo("unix");
        assertThat(activation.getProperty().getName()).isEqualTo("env.NATIVE");
        assertThat(activation.getFile().getExists()).isEqualTo("pom.xml");
        assertThat(activation.getFile().getMissing()).isEqualTo("missing.file");
    }

    private static Xpp3Dom configuration(Plugin plugin) {
        assertThat(plugin.getConfiguration()).isInstanceOf(Xpp3Dom.class);
        return (Xpp3Dom) plugin.getConfiguration();
    }

    private static Parent parent() {
        Parent parent = new Parent();
        parent.setGroupId("org.example");
        parent.setArtifactId("demo-parent");
        parent.setVersion("1.0");
        parent.setRelativePath("../pom.xml");
        return parent;
    }

    private static Organization organization(String name, String url) {
        Organization organization = new Organization();
        organization.setName(name);
        organization.setUrl(url);
        return organization;
    }

    private static License license(String name) {
        License license = new License();
        license.setName(name);
        license.setUrl("https://example.org/license");
        license.setDistribution("repo");
        license.setComments("Created for tests");
        return license;
    }

    private static Developer developer(String id, String name) {
        Developer developer = new Developer();
        developer.setId(id);
        developer.setName(name);
        developer.setEmail(id + "@example.org");
        developer.setUrl("https://example.org/" + id);
        developer.setOrganization("Example Org");
        developer.setOrganizationUrl("https://example.org/org");
        developer.addRole("developer");
        developer.addRole("reviewer");
        developer.removeRole("reviewer");
        developer.setTimezone("UTC");
        developer.addProperty("area", "tests");
        return developer;
    }

    private static Contributor contributor(String name) {
        Contributor contributor = new Contributor();
        contributor.setName(name);
        contributor.setEmail("contributor@example.org");
        contributor.addRole("writer");
        contributor.setTimezone("+1");
        return contributor;
    }

    private static MailingList mailingList() {
        MailingList mailingList = new MailingList();
        mailingList.setName("Announcements");
        mailingList.setSubscribe("announce-subscribe@example.org");
        mailingList.setUnsubscribe("announce-unsubscribe@example.org");
        mailingList.setPost("announce@example.org");
        mailingList.setArchive("https://example.org/archive/announce");
        mailingList.addOtherArchive("https://example.org/archive/announce-old");
        return mailingList;
    }

    private static Prerequisites prerequisites() {
        Prerequisites prerequisites = new Prerequisites();
        prerequisites.setMaven("2.0");
        return prerequisites;
    }

    private static Scm scm() {
        Scm scm = new Scm();
        scm.setConnection("scm:git:https://example.org/generated.git");
        scm.setDeveloperConnection("scm:git:ssh://example.org/generated.git");
        scm.setTag("generated-2.0.0");
        scm.setUrl("https://example.org/generated/scm");
        return scm;
    }

    private static IssueManagement issueManagement() {
        IssueManagement issueManagement = new IssueManagement();
        issueManagement.setSystem("Jira");
        issueManagement.setUrl("https://example.org/issues");
        return issueManagement;
    }

    private static CiManagement ciManagement() {
        CiManagement ciManagement = new CiManagement();
        ciManagement.setSystem("GitHub Actions");
        ciManagement.setUrl("https://example.org/actions");
        ciManagement.addNotifier(notifier());
        return ciManagement;
    }

    private static Notifier notifier() {
        Notifier notifier = new Notifier();
        notifier.setType("mail");
        notifier.setSendOnError(true);
        notifier.setSendOnFailure(true);
        notifier.setSendOnSuccess(false);
        notifier.setSendOnWarning(true);
        notifier.setAddress("ci@example.org");
        notifier.addConfiguration("sendTo", "committers");
        return notifier;
    }

    private static DistributionManagement distributionManagement() {
        DistributionManagement distributionManagement = new DistributionManagement();
        distributionManagement.setRepository(deploymentRepository("releases", false));
        distributionManagement.setSnapshotRepository(deploymentRepository("snapshots", true));
        Site site = new Site();
        site.setId("site");
        site.setName("Generated Site");
        site.setUrl("scp://example.org/generated-site");
        distributionManagement.setSite(site);
        distributionManagement.setDownloadUrl("https://example.org/download");
        Relocation relocation = new Relocation();
        relocation.setGroupId("org.example.new");
        relocation.setArtifactId("new-artifact");
        relocation.setVersion("3.0");
        relocation.setMessage("Moved");
        distributionManagement.setRelocation(relocation);
        distributionManagement.setStatus("deployed");
        return distributionManagement;
    }

    private static DeploymentRepository deploymentRepository(String id, boolean uniqueVersion) {
        DeploymentRepository repository = new DeploymentRepository();
        repository.setId(id);
        repository.setName(id + " repository");
        repository.setUrl("scp://example.org/" + id);
        repository.setLayout("default");
        repository.setUniqueVersion(uniqueVersion);
        return repository;
    }

    private static DependencyManagement dependencyManagement() {
        DependencyManagement dependencyManagement = new DependencyManagement();
        dependencyManagement.addDependency(dependency("org.example", "managed-lib", "1.0", "compile"));
        return dependencyManagement;
    }

    private static Dependency dependency(String groupId, String artifactId, String version, String scope) {
        Dependency dependency = new Dependency();
        dependency.setGroupId(groupId);
        dependency.setArtifactId(artifactId);
        dependency.setVersion(version);
        dependency.setType("jar");
        dependency.setScope(scope);
        dependency.setOptional(false);
        return dependency;
    }

    private static Repository repository(String id, String url, boolean releases, boolean snapshots) {
        Repository repository = new Repository();
        repository.setId(id);
        repository.setName(id + " repository");
        repository.setUrl(url);
        repository.setLayout("default");
        repository.setReleases(repositoryPolicy(releases, "daily", "warn"));
        repository.setSnapshots(repositoryPolicy(snapshots, "never", "fail"));
        return repository;
    }

    private static RepositoryPolicy repositoryPolicy(boolean enabled, String updatePolicy, String checksumPolicy) {
        RepositoryPolicy policy = new RepositoryPolicy();
        policy.setEnabled(enabled);
        policy.setUpdatePolicy(updatePolicy);
        policy.setChecksumPolicy(checksumPolicy);
        return policy;
    }

    private static Build build() {
        Build build = new Build();
        build.setSourceDirectory("src/main/java");
        build.setScriptSourceDirectory("src/main/scripts");
        build.setTestSourceDirectory("src/test/java");
        build.setOutputDirectory("target/classes");
        build.setTestOutputDirectory("target/test-classes");
        build.setDirectory("target");
        build.setFinalName("generated-model");
        build.setDefaultGoal("verify");
        build.addFilter("src/main/filters/generated.properties");
        build.addExtension(extension());
        build.addResource(resource("src/main/resources", true));
        build.addTestResource(resource("src/test/resources", false));
        PluginManagement pluginManagement = new PluginManagement();
        pluginManagement.addPlugin(plugin("org.apache.maven.plugins", "maven-install-plugin", "2.2"));
        build.setPluginManagement(pluginManagement);
        Plugin plugin = plugin("org.example.plugins", "generator-plugin", "1.0");
        plugin.addExecution(pluginExecution("generate-sources", "generate-sources", "generate"));
        plugin.addDependency(dependency("org.example", "plugin-helper", "1.0", "runtime"));
        build.addPlugin(plugin);
        return build;
    }

    private static Extension extension() {
        Extension extension = new Extension();
        extension.setGroupId("org.example");
        extension.setArtifactId("generated-extension");
        extension.setVersion("1.0");
        return extension;
    }

    private static Resource resource(String directory, boolean filtering) {
        Resource resource = new Resource();
        resource.setDirectory(directory);
        resource.setFiltering(filtering);
        resource.setTargetPath(filtering ? "classes" : "test-classes");
        resource.addInclude("**/*.properties");
        resource.addExclude("**/*.bak");
        return resource;
    }

    private static Plugin plugin(String groupId, String artifactId, String version) {
        Plugin plugin = new Plugin();
        plugin.setGroupId(groupId);
        plugin.setArtifactId(artifactId);
        plugin.setVersion(version);
        plugin.setExtensions(false);
        plugin.setInherited("true");
        Xpp3Dom configuration = new Xpp3Dom("configuration");
        Xpp3Dom enabled = new Xpp3Dom("enabled");
        enabled.setValue("true");
        configuration.addChild(enabled);
        plugin.setConfiguration(configuration);
        return plugin;
    }

    private static PluginExecution pluginExecution(String id, String phase, String goal) {
        PluginExecution execution = new PluginExecution();
        execution.setId(id);
        execution.setPhase(phase);
        execution.addGoal(goal);
        Xpp3Dom configuration = new Xpp3Dom("configuration");
        Xpp3Dom skip = new Xpp3Dom("skip");
        skip.setValue("false");
        configuration.addChild(skip);
        execution.setConfiguration(configuration);
        execution.setInherited("true");
        return execution;
    }

    private static Reporting reporting() {
        Reporting reporting = new Reporting();
        reporting.setExcludeDefaults(false);
        reporting.setOutputDirectory("target/generated-site");
        reporting.addPlugin(reportPlugin());
        return reporting;
    }

    private static ReportPlugin reportPlugin() {
        ReportPlugin reportPlugin = new ReportPlugin();
        reportPlugin.setGroupId("org.apache.maven.plugins");
        reportPlugin.setArtifactId("maven-project-info-reports-plugin");
        reportPlugin.setVersion("2.1");
        reportPlugin.setInherited("true");
        reportPlugin.setConfiguration(simpleDom("configuration", "dependencyDetails", "true"));
        reportPlugin.addReportSet(reportSet());
        return reportPlugin;
    }

    private static ReportSet reportSet() {
        ReportSet reportSet = new ReportSet();
        reportSet.setId("aggregate");
        reportSet.addReport("index");
        reportSet.addReport("dependencies");
        reportSet.removeReport("dependencies");
        reportSet.setInherited("true");
        reportSet.setConfiguration(simpleDom("configuration", "aggregate", "true"));
        return reportSet;
    }

    private static Xpp3Dom simpleDom(String name, String childName, String value) {
        Xpp3Dom dom = new Xpp3Dom(name);
        Xpp3Dom child = new Xpp3Dom(childName);
        child.setValue(value);
        dom.addChild(child);
        return dom;
    }

    private static Profile profile() {
        Profile profile = new Profile();
        profile.setId("generated-profile");
        profile.setSource("settings.xml");
        profile.setActivation(activation());
        BuildBase buildBase = new BuildBase();
        buildBase.setDefaultGoal("package");
        buildBase.setDirectory("target/profile");
        buildBase.setFinalName("profile-artifact");
        buildBase.setFilters(Arrays.asList("profile-filter.properties"));
        buildBase.setResources(Collections.singletonList(resource("src/profile/resources", true)));
        profile.setBuild(buildBase);
        profile.addRepository(repository("profile-repo", "https://example.org/profile", true, false));
        profile.addDependency(dependency("org.example", "profile-dependency", "1.0", "runtime"));
        Properties properties = new Properties();
        properties.setProperty("profile.enabled", "true");
        profile.setProperties(properties);
        return profile;
    }

    private static Activation activation() {
        Activation activation = new Activation();
        activation.setActiveByDefault(false);
        activation.setJdk("[1.5,)");
        ActivationOS os = new ActivationOS();
        os.setName("Linux");
        os.setFamily("unix");
        os.setArch("x86_64");
        os.setVersion("5");
        activation.setOs(os);
        ActivationProperty property = new ActivationProperty();
        property.setName("env.NATIVE");
        property.setValue("true");
        activation.setProperty(property);
        ActivationFile file = new ActivationFile();
        file.setExists("pom.xml");
        file.setMissing("missing.file");
        activation.setFile(file);
        return activation;
    }
}
