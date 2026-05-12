/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_settings_builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.settings.Activation;
import org.apache.maven.settings.ActivationFile;
import org.apache.maven.settings.ActivationOS;
import org.apache.maven.settings.ActivationProperty;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Profile;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Repository;
import org.apache.maven.settings.RepositoryPolicy;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.TrackableBase;
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingResult;
import org.apache.maven.settings.building.SettingsProblem;
import org.apache.maven.settings.building.SettingsProblemCollector;
import org.apache.maven.settings.crypto.DefaultSettingsDecrypter;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;
import org.apache.maven.settings.io.DefaultSettingsReader;
import org.apache.maven.settings.io.DefaultSettingsWriter;
import org.apache.maven.settings.merge.MavenSettingsMerger;
import org.apache.maven.settings.validation.DefaultSettingsValidator;
import org.junit.jupiter.api.Test;

public class Maven_settings_builderTest {
    @Test
    void readsCompleteSettingsModelFromXml() throws Exception {
        String xml = """
                <settings>
                  <localRepository>${user.home}/.m2/custom</localRepository>
                  <offline>true</offline>
                  <interactiveMode>false</interactiveMode>
                  <pluginGroups>
                    <pluginGroup>org.example.maven.plugins</pluginGroup>
                  </pluginGroups>
                  <servers>
                    <server>
                      <id>deploy</id>
                      <username>deployer</username>
                      <password>plain-password</password>
                      <privateKey>/keys/id_rsa</privateKey>
                      <passphrase>plain-passphrase</passphrase>
                      <filePermissions>640</filePermissions>
                      <directoryPermissions>750</directoryPermissions>
                    </server>
                  </servers>
                  <proxies>
                    <proxy>
                      <id>corp-proxy</id>
                      <active>true</active>
                      <protocol>https</protocol>
                      <host>proxy.example.test</host>
                      <port>8443</port>
                      <username>proxy-user</username>
                      <password>proxy-password</password>
                      <nonProxyHosts>localhost|*.example.test</nonProxyHosts>
                    </proxy>
                  </proxies>
                  <mirrors>
                    <mirror>
                      <id>central-mirror</id>
                      <name>Central mirror</name>
                      <url>https://repo.example.test/maven2</url>
                      <mirrorOf>central</mirrorOf>
                      <layout>default</layout>
                      <mirrorOfLayouts>default,legacy</mirrorOfLayouts>
                    </mirror>
                  </mirrors>
                  <profiles>
                    <profile>
                      <id>jdk-profile</id>
                      <activation>
                        <activeByDefault>true</activeByDefault>
                        <jdk>21</jdk>
                        <os>
                          <name>Linux</name>
                          <family>unix</family>
                          <arch>amd64</arch>
                          <version>6</version>
                        </os>
                        <property>
                          <name>env</name>
                          <value>ci</value>
                        </property>
                        <file>
                          <exists>${user.home}/.m2/settings.xml</exists>
                        </file>
                      </activation>
                      <properties>
                        <releaseRepository>central</releaseRepository>
                      </properties>
                      <repositories>
                        <repository>
                          <id>central</id>
                          <name>Central</name>
                          <url>https://repo.maven.apache.org/maven2</url>
                          <layout>default</layout>
                          <releases>
                            <enabled>true</enabled>
                            <updatePolicy>daily</updatePolicy>
                            <checksumPolicy>fail</checksumPolicy>
                          </releases>
                          <snapshots>
                            <enabled>false</enabled>
                          </snapshots>
                        </repository>
                      </repositories>
                      <pluginRepositories>
                        <pluginRepository>
                          <id>plugins</id>
                          <url>https://plugins.example.test</url>
                        </pluginRepository>
                      </pluginRepositories>
                    </profile>
                  </profiles>
                  <activeProfiles>
                    <activeProfile>jdk-profile</activeProfile>
                  </activeProfiles>
                </settings>
                """;

        Settings settings = new DefaultSettingsReader().read(new StringReader(xml), Map.of());

        assertThat(settings.getLocalRepository()).isEqualTo("${user.home}/.m2/custom");
        assertThat(settings.isOffline()).isTrue();
        assertThat(settings.isInteractiveMode()).isFalse();
        assertThat(settings.getPluginGroups()).containsExactly("org.example.maven.plugins");
        assertThat(settings.getActiveProfiles()).containsExactly("jdk-profile");

        Server server = settings.getServer("deploy");
        assertThat(server.getUsername()).isEqualTo("deployer");
        assertThat(server.getPassword()).isEqualTo("plain-password");
        assertThat(server.getPrivateKey()).isEqualTo("/keys/id_rsa");
        assertThat(server.getPassphrase()).isEqualTo("plain-passphrase");
        assertThat(server.getFilePermissions()).isEqualTo("640");
        assertThat(server.getDirectoryPermissions()).isEqualTo("750");

        Proxy proxy = settings.getActiveProxy();
        assertThat(proxy.getId()).isEqualTo("corp-proxy");
        assertThat(proxy.getProtocol()).isEqualTo("https");
        assertThat(proxy.getHost()).isEqualTo("proxy.example.test");
        assertThat(proxy.getPort()).isEqualTo(8443);
        assertThat(proxy.getNonProxyHosts()).isEqualTo("localhost|*.example.test");

        Mirror mirror = settings.getMirrorOf("central");
        assertThat(mirror.getId()).isEqualTo("central-mirror");
        assertThat(mirror.getName()).isEqualTo("Central mirror");
        assertThat(mirror.getUrl()).isEqualTo("https://repo.example.test/maven2");
        assertThat(mirror.getLayout()).isEqualTo("default");
        assertThat(mirror.getMirrorOfLayouts()).isEqualTo("default,legacy");

        Profile profile = settings.getProfiles().get(0);
        assertThat(profile.getId()).isEqualTo("jdk-profile");
        assertThat(profile.getProperties()).containsEntry("releaseRepository", "central");
        assertThat(profile.getActivation().isActiveByDefault()).isTrue();
        assertThat(profile.getActivation().getJdk()).isEqualTo("21");
        assertThat(profile.getActivation().getOs().getFamily()).isEqualTo("unix");
        assertThat(profile.getActivation().getProperty().getName()).isEqualTo("env");
        assertThat(profile.getActivation().getFile().getExists()).isEqualTo("${user.home}/.m2/settings.xml");

        Repository repository = profile.getRepositories().get(0);
        assertThat(repository.getId()).isEqualTo("central");
        assertThat(repository.getReleases().isEnabled()).isTrue();
        assertThat(repository.getReleases().getUpdatePolicy()).isEqualTo("daily");
        assertThat(repository.getReleases().getChecksumPolicy()).isEqualTo("fail");
        assertThat(repository.getSnapshots().isEnabled()).isFalse();
        assertThat(profile.getPluginRepositories().get(0).getId()).isEqualTo("plugins");
    }

    @Test
    void writesSettingsModelAndReadsItBack() throws Exception {
        Settings original = new Settings();
        original.setLocalRepository("/tmp/m2-repository");
        original.setOffline(true);
        original.addPluginGroup("org.example.plugins");
        original.addActiveProfile("release");
        original.addServer(server("deploy", "deployer"));
        original.addProxy(proxy("proxy", true));
        original.addMirror(mirror("central-mirror", "central"));
        original.addProfile(profile("release", true));

        StringWriter writer = new StringWriter();
        new DefaultSettingsWriter().write(writer, Map.of(), original);
        Settings roundTripped = new DefaultSettingsReader().read(new StringReader(writer.toString()), Map.of());

        assertThat(writer.toString()).contains("<settings", "<localRepository>/tmp/m2-repository</localRepository>");
        assertThat(roundTripped.getLocalRepository()).isEqualTo("/tmp/m2-repository");
        assertThat(roundTripped.isOffline()).isTrue();
        assertThat(roundTripped.getPluginGroups()).containsExactly("org.example.plugins");
        assertThat(roundTripped.getServer("deploy").getUsername()).isEqualTo("deployer");
        assertThat(roundTripped.getActiveProxy().getHost()).isEqualTo("proxy.example.test");
        assertThat(roundTripped.getMirrorOf("central").getId()).isEqualTo("central-mirror");
        assertThat(roundTripped.getProfilesAsMap()).containsKey("release");
        assertThat(roundTripped.getActiveProfiles()).containsExactly("release");
    }

    @Test
    void settingsBuilderMergesGlobalAndUserFilesAndInterpolatesProperties() throws Exception {
        Path testDirectory = testDirectory("builder-merge");
        Path globalSettings = testDirectory.resolve("global-settings.xml");
        Path userSettings = testDirectory.resolve("user-settings.xml");
        Files.writeString(globalSettings, """
                <settings>
                  <localRepository>/global/repository</localRepository>
                  <pluginGroups>
                    <pluginGroup>org.global.plugins</pluginGroup>
                  </pluginGroups>
                  <mirrors>
                    <mirror>
                      <id>global-mirror</id>
                      <url>https://global.example.test</url>
                      <mirrorOf>external:*</mirrorOf>
                    </mirror>
                  </mirrors>
                  <profiles>
                    <profile>
                      <id>global-profile</id>
                      <repositories>
                        <repository>
                          <id>global-repository</id>
                          <url>https://global.example.test/repository</url>
                        </repository>
                      </repositories>
                    </profile>
                  </profiles>
                </settings>
                """);
        Files.writeString(userSettings, """
                <settings>
                  <localRepository>${custom.repo}</localRepository>
                  <pluginGroups>
                    <pluginGroup>org.user.plugins</pluginGroup>
                  </pluginGroups>
                  <servers>
                    <server>
                      <id>deploy</id>
                      <username>${deploy.user}</username>
                    </server>
                  </servers>
                  <activeProfiles>
                    <activeProfile>global-profile</activeProfile>
                  </activeProfiles>
                </settings>
                """);
        Properties userProperties = new Properties();
        userProperties.setProperty("custom.repo", testDirectory.resolve("local-repository").toString());
        userProperties.setProperty("deploy.user", "ci-deployer");

        SettingsBuildingResult result = new DefaultSettingsBuilderFactory().newInstance()
                .build(new DefaultSettingsBuildingRequest()
                        .setGlobalSettingsFile(globalSettings.toFile())
                        .setUserSettingsFile(userSettings.toFile())
                        .setUserProperties(userProperties));
        Settings effectiveSettings = result.getEffectiveSettings();

        assertThat(result.getProblems()).isEmpty();
        assertThat(effectiveSettings.getLocalRepository()).isEqualTo(userProperties.getProperty("custom.repo"));
        assertThat(effectiveSettings.getPluginGroups()).contains("org.global.plugins", "org.user.plugins");
        assertThat(effectiveSettings.getMirrorOf("external:*").getUrl()).isEqualTo("https://global.example.test");
        assertThat(effectiveSettings.getServer("deploy").getUsername()).isEqualTo("ci-deployer");
        assertThat(effectiveSettings.getProfilesAsMap()).containsKey("global-profile");
        assertThat(effectiveSettings.getActiveProfiles()).containsExactly("global-profile");
    }

    @Test
    void settingsBuilderReportsParsingProblems() throws Exception {
        Path invalidSettings = testDirectory("builder-parsing-problems").resolve("invalid-settings.xml");
        Files.writeString(invalidSettings, """
                <settings>
                  <servers>
                    <server>
                      <id>broken</id>
                    </server>
                  </servers>
                """);

        SettingsBuildingException exception = catchThrowableOfType(
                () -> new DefaultSettingsBuilderFactory().newInstance()
                        .build(new DefaultSettingsBuildingRequest().setUserSettingsFile(invalidSettings.toFile())),
                SettingsBuildingException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getProblems()).hasSize(1);
        assertThat(exception.getProblems().get(0).getSeverity()).isEqualTo(SettingsProblem.Severity.FATAL);
        assertThat(exception.getProblems().get(0).getMessage()).contains("settings");
        assertThat(exception.getProblems().get(0).getSource()).isEqualTo(invalidSettings.toFile().getAbsolutePath());
    }

    @Test
    void readerRejectsMalformedSettingsXml() {
        assertThatExceptionOfType(IOException.class)
                .isThrownBy(() -> new DefaultSettingsReader().read(new StringReader("<settings><servers>"), Map.of()))
                .withMessageContaining("settings");
    }

    @Test
    void validatorCollectsErrorsAndWarningsForInvalidSettings() {
        Settings settings = new Settings();
        settings.addPluginGroup("not a valid group id");
        Server server = new Server();
        server.setId(null);
        settings.addServer(server);
        Mirror mirror = new Mirror();
        mirror.setId("local");
        settings.addMirror(mirror);
        Profile profile = new Profile();
        Repository repository = new Repository();
        profile.addRepository(repository);
        settings.addProfile(profile);
        RecordingProblemCollector collector = new RecordingProblemCollector();

        new DefaultSettingsValidator().validate(settings, collector);

        assertThat(collector.problems)
                .anySatisfy(problem -> {
                    assertThat(problem.severity).isEqualTo(SettingsProblem.Severity.ERROR);
                    assertThat(problem.fieldName).isEqualTo("pluginGroups.pluginGroup[0]");
                    assertThat(problem.message).contains("valid group id");
                })
                .anySatisfy(problem -> {
                    assertThat(problem.severity).isEqualTo(SettingsProblem.Severity.ERROR);
                    assertThat(problem.fieldName).isEqualTo("servers.server[0].id");
                    assertThat(problem.message).contains("is missing");
                })
                .anySatisfy(problem -> {
                    assertThat(problem.severity).isEqualTo(SettingsProblem.Severity.WARNING);
                    assertThat(problem.fieldName).isEqualTo("mirrors.mirror.id");
                    assertThat(problem.message).contains("must not be 'local'");
                })
                .anySatisfy(problem -> {
                    assertThat(problem.severity).isEqualTo(SettingsProblem.Severity.ERROR);
                    assertThat(problem.fieldName).isEqualTo("repositories.repository.id");
                    assertThat(problem.message).contains("is missing");
                });
    }

    @Test
    void mergerAddsMissingEntriesAndKeepsTargetValues() {
        Settings target = new Settings();
        target.setLocalRepository("/target/repository");
        target.addPluginGroup("org.target.plugins");
        target.addActiveProfile("target-profile");
        target.addServer(server("shared", "target-user"));
        target.addMirror(mirror("target-mirror", "central"));

        Settings source = new Settings();
        source.setLocalRepository("/source/repository");
        source.addPluginGroup("org.target.plugins");
        source.addPluginGroup("org.source.plugins");
        source.addActiveProfile("target-profile");
        source.addActiveProfile("source-profile");
        source.addServer(server("shared", "source-user"));
        source.addServer(server("source-only", "source-user"));
        source.addMirror(mirror("source-mirror", "snapshots"));

        new MavenSettingsMerger().merge(target, source, TrackableBase.GLOBAL_LEVEL);

        assertThat(target.getLocalRepository()).isEqualTo("/target/repository");
        assertThat(target.getPluginGroups()).containsExactly("org.target.plugins", "org.source.plugins");
        assertThat(target.getActiveProfiles()).containsExactly("target-profile", "source-profile");
        assertThat(target.getServers()).extracting(Server::getId).containsExactly("shared", "source-only");
        assertThat(target.getServer("shared").getUsername()).isEqualTo("target-user");
        assertThat(target.getServer("source-only").getSourceLevel()).isEqualTo(TrackableBase.GLOBAL_LEVEL);
        assertThat(target.getMirrors()).extracting(Mirror::getId).containsExactly("target-mirror", "source-mirror");
    }

    @Test
    void decrypterCopiesServersAndProxiesWithUnencryptedNullSecrets() {
        Server server = new Server();
        server.setId("server-without-secret");
        server.setUsername("server-user");
        Proxy proxy = proxy("proxy-without-secret", true);
        Settings settings = new Settings();
        settings.addServer(server);
        settings.addProxy(proxy);

        SettingsDecryptionResult result = new DefaultSettingsDecrypter()
                .decrypt(new DefaultSettingsDecryptionRequest(settings));

        assertThat(result.getProblems()).isEmpty();
        assertThat(result.getServers()).hasSize(1);
        assertThat(result.getProxies()).hasSize(1);
        assertThat(result.getServer()).isNotSameAs(server);
        assertThat(result.getServer().getId()).isEqualTo("server-without-secret");
        assertThat(result.getServer().getUsername()).isEqualTo("server-user");
        assertThat(result.getServer().getPassword()).isNull();
        assertThat(result.getProxy()).isNotSameAs(proxy);
        assertThat(result.getProxy().getId()).isEqualTo("proxy-without-secret");
        assertThat(result.getProxy().getPassword()).isNull();
    }

    @Test
    void clonedSettingsHaveIndependentCollectionsAndLookups() {
        Settings original = new Settings();
        original.addServer(server("deploy", "original-user"));
        original.addMirror(mirror("mirror", "central"));
        original.addProfile(profile("release", false));
        original.addActiveProfile("release");

        Settings clone = original.clone();
        clone.getServer("deploy").setUsername("clone-user");
        clone.addServer(server("staging", "staging-user"));
        clone.getMirrorOf("central").setUrl("https://clone.example.test");
        clone.addActiveProfile("staging");

        assertThat(original.getServer("deploy").getUsername()).isEqualTo("original-user");
        assertThat(original.getServer("staging")).isNull();
        assertThat(original.getMirrorOf("central").getUrl()).isEqualTo("https://mirror.example.test/central");
        assertThat(original.getActiveProfiles()).containsExactly("release");
        assertThat(clone.getServer("deploy").getUsername()).isEqualTo("clone-user");
        assertThat(clone.getServer("staging").getUsername()).isEqualTo("staging-user");
        assertThat(clone.getProfilesAsMap()).containsKey("release");
    }

    private static Path testDirectory(String name) throws IOException {
        Path directory = Path.of("build", "tmp", "Maven_settings_builderTest", name).toAbsolutePath();
        Files.createDirectories(directory);
        return directory;
    }

    private static Server server(String id, String username) {
        Server server = new Server();
        server.setId(id);
        server.setUsername(username);
        return server;
    }

    private static Proxy proxy(String id, boolean active) {
        Proxy proxy = new Proxy();
        proxy.setId(id);
        proxy.setActive(active);
        proxy.setProtocol("https");
        proxy.setHost("proxy.example.test");
        proxy.setPort(8443);
        proxy.setUsername("proxy-user");
        proxy.setNonProxyHosts("localhost|*.example.test");
        return proxy;
    }

    private static Mirror mirror(String id, String mirrorOf) {
        Mirror mirror = new Mirror();
        mirror.setId(id);
        mirror.setName(id + " name");
        mirror.setUrl("https://mirror.example.test/" + mirrorOf);
        mirror.setMirrorOf(mirrorOf);
        return mirror;
    }

    private static Profile profile(String id, boolean activeByDefault) {
        Profile profile = new Profile();
        profile.setId(id);
        profile.addProperty("environment", id);

        Activation activation = new Activation();
        activation.setActiveByDefault(activeByDefault);
        activation.setJdk("21");
        ActivationOS os = new ActivationOS();
        os.setFamily("unix");
        os.setName("Linux");
        activation.setOs(os);
        ActivationProperty property = new ActivationProperty();
        property.setName("profile");
        property.setValue(id);
        activation.setProperty(property);
        ActivationFile file = new ActivationFile();
        file.setMissing("never-created.marker");
        activation.setFile(file);
        profile.setActivation(activation);

        Repository repository = new Repository();
        repository.setId(id + "-repository");
        repository.setName(id + " repository");
        repository.setUrl("https://repo.example.test/" + id);
        RepositoryPolicy releases = new RepositoryPolicy();
        releases.setEnabled(true);
        releases.setUpdatePolicy("daily");
        releases.setChecksumPolicy("warn");
        repository.setReleases(releases);
        RepositoryPolicy snapshots = new RepositoryPolicy();
        snapshots.setEnabled(false);
        repository.setSnapshots(snapshots);
        profile.addRepository(repository);
        return profile;
    }

    private static final class RecordingProblemCollector implements SettingsProblemCollector {
        private final List<CollectedProblem> problems = new ArrayList<>();

        @Override
        public void add(SettingsProblem.Severity severity, String message, int line, int column, Exception cause) {
            problems.add(new CollectedProblem(severity, message));
        }
    }

    private static final class CollectedProblem {
        private final SettingsProblem.Severity severity;
        private final String fieldName;
        private final String message;

        private CollectedProblem(SettingsProblem.Severity severity, String message) {
            this.severity = severity;
            this.fieldName = message.substring(1, message.indexOf(' ', 1) - 1);
            this.message = message;
        }
    }
}
