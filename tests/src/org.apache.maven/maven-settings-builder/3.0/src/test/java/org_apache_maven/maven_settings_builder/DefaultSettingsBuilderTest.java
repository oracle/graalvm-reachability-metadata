/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_settings_builder;

import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Profile;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingResult;
import org.apache.maven.settings.building.SettingsProblem;
import org.apache.maven.settings.building.SettingsProblem.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DefaultSettingsBuilderTest {
    @TempDir
    Path tempDir;

    @Test
    void mergesGlobalAndUserSettingsWithUserEntriesTakingPrecedence() throws Exception {
        Path globalFile = Files.writeString(tempDir.resolve("global-settings.xml"), """
                <settings>
                  <servers>
                    <server><id>releases</id><username>global-user</username></server>
                    <server><id>snapshots</id><username>snapshot-user</username></server>
                  </servers>
                  <mirrors>
                    <mirror><id>company</id><url>https://global.example/repo</url><mirrorOf>*</mirrorOf></mirror>
                    <mirror><id>backup</id><url>https://backup.example/repo</url><mirrorOf>central</mirrorOf></mirror>
                  </mirrors>
                  <proxies><proxy><id>office</id><host>proxy.example</host><port>8080</port></proxy></proxies>
                  <profiles>
                    <profile><id>shared</id><properties><channel>global</channel></properties></profile>
                    <profile><id>global-only</id><properties><channel>inherited</channel></properties></profile>
                  </profiles>
                  <activeProfiles><activeProfile>shared</activeProfile><activeProfile>global-only</activeProfile></activeProfiles>
                  <pluginGroups><pluginGroup>com.example.shared</pluginGroup><pluginGroup>com.example.global</pluginGroup></pluginGroups>
                </settings>
                """);
        Path userFile = Files.writeString(tempDir.resolve("user-settings.xml"), """
                <settings>
                  <servers><server><id>releases</id><username>user-account</username><password>user-secret</password></server></servers>
                  <mirrors><mirror><id>company</id><url>https://user.example/repo</url><mirrorOf>*</mirrorOf></mirror></mirrors>
                  <profiles>
                    <profile>
                      <id>shared</id><properties><channel>user</channel></properties>
                      <repositories><repository><id>releases</id><url>https://releases.example/repo</url></repository></repositories>
                    </profile>
                  </profiles>
                  <activeProfiles><activeProfile>shared</activeProfile><activeProfile>user-only</activeProfile></activeProfiles>
                  <pluginGroups><pluginGroup>com.example.shared</pluginGroup><pluginGroup>com.example.user</pluginGroup></pluginGroups>
                </settings>
                """);
        DefaultSettingsBuildingRequest request = new DefaultSettingsBuildingRequest()
                .setGlobalSettingsFile(globalFile.toFile())
                .setUserSettingsFile(userFile.toFile());

        SettingsBuildingResult result = new DefaultSettingsBuilderFactory().newInstance().build(request);
        Settings settings = result.getEffectiveSettings();

        assertThat(result.getProblems()).isEmpty();
        assertThat(settings.getServers()).extracting(Server::getId).containsExactly("releases", "snapshots");
        assertThat(settings.getServers().get(0).getUsername()).isEqualTo("user-account");
        assertThat(settings.getServers().get(0).getPassword()).isEqualTo("user-secret");
        assertThat(settings.getServers().get(1).getUsername()).isEqualTo("snapshot-user");
        assertThat(settings.getMirrors()).extracting(Mirror::getUrl)
                .containsExactly("https://user.example/repo", "https://backup.example/repo");
        assertThat(settings.getActiveProxy().getHost()).isEqualTo("proxy.example");
        assertThat(settings.getActiveProxy().getPort()).isEqualTo(8080);
        assertThat(settings.getProfiles()).extracting(Profile::getId).containsExactly("shared", "global-only");
        assertThat(settings.getProfiles().get(0).getProperties().getProperty("channel")).isEqualTo("user");
        assertThat(settings.getProfiles().get(0).getRepositories().get(0).getUrl())
                .isEqualTo("https://releases.example/repo");
        assertThat(settings.getProfiles().get(1).getProperties().getProperty("channel")).isEqualTo("inherited");
        assertThat(settings.getActiveProfiles()).containsExactly("shared", "user-only", "global-only");
        assertThat(settings.getPluginGroups()).containsExactly("com.example.shared", "com.example.user", "com.example.global");
    }

    @Test
    void interpolatesPropertiesAndPreservesXmlCharacters() throws Exception {
        Path settingsFile = Files.writeString(tempDir.resolve("interpolated-settings.xml"), """
                <settings>
                  <localRepository>${test.repository}</localRepository>
                  <servers>
                    <server><id>deployment</id><username>${test.account}</username><password>${test.secret}</password></server>
                  </servers>
                  <mirrors>
                    <mirror><id>company</id><url>https://${test.host}/repo</url><mirrorOf>*</mirrorOf></mirror>
                  </mirrors>
                </settings>
                """);
        String repository = tempDir.resolve("local-repository").toString();
        Properties systemProperties = new Properties();
        systemProperties.setProperty("test.repository", repository);
        systemProperties.setProperty("test.host", "mirror.example");
        systemProperties.setProperty("test.account", "system-account");
        Properties userProperties = new Properties();
        userProperties.setProperty("test.account", "user-account");
        userProperties.setProperty("test.secret", "secret<&>value");
        DefaultSettingsBuildingRequest request = new DefaultSettingsBuildingRequest()
                .setUserSettingsFile(settingsFile.toFile())
                .setSystemProperties(systemProperties)
                .setUserProperties(userProperties);

        SettingsBuildingResult result = new DefaultSettingsBuilderFactory().newInstance().build(request);

        assertThat(result.getProblems()).isEmpty();
        Settings settings = result.getEffectiveSettings();
        assertThat(settings.getLocalRepository()).isEqualTo(repository);
        assertThat(settings.getServers().get(0).getUsername()).isEqualTo("user-account");
        assertThat(settings.getServers().get(0).getPassword()).isEqualTo("secret<&>value");
        assertThat(settings.getMirrors().get(0).getUrl()).isEqualTo("https://mirror.example/repo");
    }

    @Test
    void reportsUnknownElementsAsWarningsWhileBuildingKnownSettings() throws Exception {
        Path settingsFile = Files.writeString(tempDir.resolve("extended-settings.xml"), """
                <settings>
                  <futureOption>ignored</futureOption>
                  <offline>true</offline>
                </settings>
                """);
        DefaultSettingsBuildingRequest request = new DefaultSettingsBuildingRequest()
                .setUserSettingsFile(settingsFile.toFile());

        SettingsBuildingResult result = new DefaultSettingsBuilderFactory().newInstance().build(request);

        assertThat(result.getEffectiveSettings().isOffline()).isTrue();
        assertThat(result.getProblems()).hasSize(1);
        SettingsProblem problem = result.getProblems().get(0);
        assertThat(problem.getSeverity()).isEqualTo(Severity.WARNING);
        assertThat(problem.getMessage()).contains("futureOption");
        assertThat(problem.getSource()).isEqualTo(settingsFile.toAbsolutePath().toString());
        assertThat(problem.getLineNumber()).isPositive();
    }

    @Test
    void collectsValidationErrorsForInvalidSettings() throws Exception {
        Path settingsFile = Files.writeString(tempDir.resolve("invalid-settings.xml"), """
                <settings>
                  <pluginGroups><pluginGroup>not a group id</pluginGroup></pluginGroups>
                  <mirrors><mirror><id>incomplete</id><mirrorOf>*</mirrorOf></mirror></mirrors>
                </settings>
                """);
        DefaultSettingsBuildingRequest request = new DefaultSettingsBuildingRequest()
                .setUserSettingsFile(settingsFile.toFile());
        SettingsBuilder builder = new DefaultSettingsBuilderFactory().newInstance();

        SettingsBuildingException exception = assertThrows(SettingsBuildingException.class, () -> builder.build(request));

        assertThat(exception.getProblems()).hasSize(2);
        assertThat(exception.getProblems()).extracting(SettingsProblem::getSeverity).containsOnly(Severity.ERROR);
        assertThat(exception.getProblems()).extracting(SettingsProblem::getMessage)
                .anySatisfy(message -> assertThat(message).contains("pluginGroups.pluginGroup"))
                .anySatisfy(message -> assertThat(message).contains("mirrors.mirror.url"));
        assertThat(exception.getProblems()).extracting(SettingsProblem::getSource)
                .containsOnly(settingsFile.toAbsolutePath().toString());
    }

    @Test
    void reportsMalformedXmlWithItsSourceAndLocation() throws Exception {
        Path settingsFile = Files.writeString(tempDir.resolve("malformed-settings.xml"), """
                <settings>
                  <servers><server></servers>
                </settings>
                """);
        DefaultSettingsBuildingRequest request = new DefaultSettingsBuildingRequest()
                .setUserSettingsFile(settingsFile.toFile());
        SettingsBuilder builder = new DefaultSettingsBuilderFactory().newInstance();

        SettingsBuildingException exception = assertThrows(SettingsBuildingException.class, () -> builder.build(request));

        assertThat(exception.getProblems()).hasSize(1);
        SettingsProblem problem = exception.getProblems().get(0);
        assertThat(problem.getSeverity()).isEqualTo(Severity.FATAL);
        assertThat(problem.getSource()).isEqualTo(settingsFile.toAbsolutePath().toString());
        assertThat(problem.getLineNumber()).isPositive();
        assertThat(problem.getColumnNumber()).isPositive();
        assertThat(problem.getException()).isNotNull();
    }
}
