/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_sec_dispatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.codehaus.plexus.components.cipher.PlexusCipher;
import org.codehaus.plexus.components.cipher.internal.AESGCMNoPadding;
import org.codehaus.plexus.components.cipher.internal.DefaultPlexusCipher;
import org.codehaus.plexus.components.secdispatcher.SecDispatcher;
import org.codehaus.plexus.components.secdispatcher.SecDispatcherException;
import org.codehaus.plexus.components.secdispatcher.internal.DefaultSecDispatcher;
import org.codehaus.plexus.components.secdispatcher.internal.Dispatcher;
import org.codehaus.plexus.components.secdispatcher.internal.MasterPasswordSource;
import org.codehaus.plexus.components.secdispatcher.internal.SecUtil;
import org.codehaus.plexus.components.secdispatcher.model.Config;
import org.codehaus.plexus.components.secdispatcher.model.ConfigProperty;
import org.codehaus.plexus.components.secdispatcher.model.SettingsSecurity;
import org.codehaus.plexus.components.secdispatcher.model.io.stax.SecurityConfigurationStaxReader;
import org.codehaus.plexus.components.secdispatcher.model.io.stax.SecurityConfigurationStaxWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Plexus_sec_dispatcherTest {
    private static final String CIPHER = AESGCMNoPadding.CIPHER_ALG;

    @TempDir
    Path temporaryDirectory;

    @Test
    void dispatcherEncryptsAndDecryptsPasswordsWithConfiguredMasterPasswordSource() throws Exception {
        PlexusCipher cipher = cipher();
        String masterPassword = "master-password";
        String serverPassword = "server-password";
        Path configurationFile = temporaryDirectory.resolve("settings-security.xml");
        writeSettingsSecurity(configurationFile, settingsSecurity(masterPassword));

        DefaultSecDispatcher dispatcher = dispatcher(cipher, configurationFile, Map.of(), Map.of());
        String encryptedPassword = dispatcher.encrypt(serverPassword, null);

        assertThat(dispatcher.getConfigurationFile()).isEqualTo(configurationFile.toString());
        assertThat(dispatcher.availableCiphers()).contains(CIPHER);
        assertThat(dispatcher.availableDispatchers()).isEmpty();
        assertThat(encryptedPassword).isNotEqualTo(serverPassword);
        assertThat(dispatcher.decrypt("not encrypted")).isEqualTo("not encrypted");
        assertThat(dispatcher.decrypt(encryptedPassword)).isEqualTo(serverPassword);
    }

    @Test
    void dispatcherDelegatesNamedPasswordsToConfiguredDispatcher() throws Exception {
        PlexusCipher cipher = cipher();
        Path configurationFile = temporaryDirectory.resolve("named-settings-security.xml");
        SettingsSecurity settingsSecurity = settingsSecurity("dispatcher-master");
        settingsSecurity.addConfiguration(
                config("vault", property("endpoint", "local-vault"), property("profile", "test")));
        writeSettingsSecurity(configurationFile, settingsSecurity);
        Map<String, Dispatcher> dispatchers = new HashMap<>();
        dispatchers.put("vault", new Dispatcher() {
            @Override
            public String encrypt(String password, Map<String, String> attributes, Map<String, String> configuration) {
                return password;
            }

            @Override
            public String decrypt(String password, Map<String, String> attributes, Map<String, String> configuration) {
                assertThat(password).isEqualTo("credential-id");
                assertThat(attributes).containsEntry(SecDispatcher.DISPATCHER_NAME_ATTR, "vault")
                        .containsEntry("alias", "database");
                assertThat(configuration).containsEntry(Dispatcher.CONF_MASTER_PASSWORD, "dispatcher-master")
                        .containsEntry("endpoint", "local-vault")
                        .containsEntry("profile", "test");
                return configuration.get("endpoint") + ":" + attributes.get("alias") + ":" + password;
            }
        });
        DefaultSecDispatcher dispatcher = dispatcher(cipher, configurationFile, Map.of(), dispatchers);
        String namedPassword = cipher.decorate("[name=vault,alias=database]credential-id");

        assertThat(dispatcher.availableDispatchers()).containsExactly("vault");
        assertThat(dispatcher.decrypt(namedPassword)).isEqualTo("local-vault:database:credential-id");
    }

    @Test
    void dispatcherReportsMissingNamedDispatcher() throws Exception {
        PlexusCipher cipher = cipher();
        Path configurationFile = temporaryDirectory.resolve("missing-dispatcher-settings-security.xml");
        writeSettingsSecurity(configurationFile, settingsSecurity("master-password"));
        DefaultSecDispatcher dispatcher = dispatcher(cipher, configurationFile, Map.of(), Map.of());
        String namedPassword = cipher.decorate("[name=missing]credential-id");

        assertThatThrownBy(() -> dispatcher.decrypt(namedPassword))
                .isInstanceOf(SecDispatcherException.class)
                .hasMessageContaining("no dispatcher for name missing");
    }

    @Test
    void dispatcherUsesSystemPropertyConfigurationLocationOverride() throws Exception {
        PlexusCipher cipher = cipher();
        String configuredMasterPassword = "configured-master-password";
        String overrideMasterPassword = "override-master-password";
        Path configuredFile = temporaryDirectory.resolve("configured-settings-security.xml");
        Path overrideFile = temporaryDirectory.resolve("override-settings-security.xml");
        writeSettingsSecurity(configuredFile, settingsSecurity(configuredMasterPassword));
        writeSettingsSecurity(overrideFile, settingsSecurity(overrideMasterPassword));
        DefaultSecDispatcher dispatcher = dispatcher(cipher, configuredFile, Map.of(), Map.of());
        String previousLocation = System.getProperty(SecDispatcher.SYSTEM_PROPERTY_CONFIGURATION_LOCATION);

        try {
            System.setProperty(SecDispatcher.SYSTEM_PROPERTY_CONFIGURATION_LOCATION, overrideFile.toString());

            assertThat(dispatcher.decrypt(cipher.encryptAndDecorate(CIPHER, "server-password", overrideMasterPassword)))
                    .isEqualTo("server-password");
        } finally {
            if (previousLocation == null) {
                System.clearProperty(SecDispatcher.SYSTEM_PROPERTY_CONFIGURATION_LOCATION);
            } else {
                System.setProperty(SecDispatcher.SYSTEM_PROPERTY_CONFIGURATION_LOCATION, previousLocation);
            }
        }
    }

    @Test
    void secUtilReadsWritesBacksUpAndExtractsNamedConfiguration() throws Exception {
        Path configurationFile = temporaryDirectory.resolve("settings-security.xml");
        SettingsSecurity first = settingsSecurity("first-master");
        first.addConfiguration(config("server", property("one", "first"), property("two", "second")));
        SecUtil.write(configurationFile, first, false);
        SettingsSecurity second = settingsSecurity("second-master");
        second.addConfiguration(config("token", property("id", "abc-123")));
        SecUtil.write(configurationFile, second, true);

        SettingsSecurity actual = SecUtil.read(configurationFile);

        assertThat(SecUtil.read(temporaryDirectory.resolve("absent-settings-security.xml"))).isNull();
        assertThat(Files.readString(configurationFile.resolveSibling("settings-security.xml.bak")))
                .contains("first-master", "server");
        assertThat(actual.getMasterSource()).isEqualTo("second-master");
        assertThat(actual.getMasterCipher()).isEqualTo(CIPHER);
        assertThat(SecUtil.getConfig(actual, "token")).containsEntry("id", "abc-123");
        assertThat(SecUtil.getConfig(actual, "absent")).isNull();
        assertThat(SecUtil.getConfig(actual, null)).isNull();
    }

    @Test
    void staxWriterAndReaderRoundTripSettingsSecurityFromStreams() throws Exception {
        SettingsSecurity expected = settingsSecurity("master<&>value");
        expected.setModelVersion("3.0.0");
        expected.setModelEncoding(StandardCharsets.UTF_8.name());
        expected.addConfiguration(config("server", property("url", "https://repo.example/path?a=1&b=2")));
        expected.addConfiguration(config("token", property("id", "abc-123"), property("scope", "read")));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        new SecurityConfigurationStaxWriter().write(outputStream, expected);
        String xml = outputStream.toString(StandardCharsets.UTF_8.name());
        SettingsSecurity actual = new SecurityConfigurationStaxReader()
                .read(new ByteArrayInputStream(outputStream.toByteArray()));

        assertThat(xml).contains("<settingsSecurity", "<masterSource>master&lt;&amp;&gt;value</masterSource>");
        assertThat(actual.getMasterSource()).isEqualTo(expected.getMasterSource());
        assertThat(actual.getMasterCipher()).isEqualTo(expected.getMasterCipher());
        assertThat(actual.getModelVersion()).isEqualTo("3.0.0");
        assertThat(actual.getModelEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(SecUtil.getConfig(actual, "server"))
                .containsEntry("url", "https://repo.example/path?a=1&b=2");
        assertThat(SecUtil.getConfig(actual, "token")).containsEntry("id", "abc-123").containsEntry("scope", "read");
    }

    @Test
    void staxReaderTrimsContentAndCanIgnoreUnknownMarkupInLenientMode() throws Exception {
        String xml = """
                <settingsSecurity>
                  <unknown><nested>ignored</nested></unknown>
                  <masterSource> raw-master </masterSource>
                  <masterCipher> AES/GCM/NoPadding </masterCipher>
                  <configurations>
                    <configuration>
                      <name> vault </name>
                      <properties>
                        <property>
                          <name> endpoint </name>
                          <value> local </value>
                        </property>
                      </properties>
                    </configuration>
                  </configurations>
                </settingsSecurity>
                """;
        SecurityConfigurationStaxReader reader = new SecurityConfigurationStaxReader();

        assertThatThrownBy(() -> reader.read(new StringReader(xml), true))
                .isInstanceOf(XMLStreamException.class)
                .hasMessageContaining("Unrecognised tag: 'unknown'");
        SettingsSecurity settingsSecurity = reader.read(new StringReader(xml), false);

        assertThat(settingsSecurity.getMasterSource()).isEqualTo("raw-master");
        assertThat(settingsSecurity.getMasterCipher()).isEqualTo(CIPHER);
        assertThat(SecUtil.getConfig(settingsSecurity, "vault")).containsEntry("endpoint", "local");
    }

    @Test
    void modelObjectsExposeMutableConfigurationCollections() {
        ConfigProperty first = property("first", "one");
        ConfigProperty second = property("second", "two");
        Config config = config("sample", first, second);
        SettingsSecurity settingsSecurity = settingsSecurity("master");

        settingsSecurity.addConfiguration(config);
        config.removeProperty(first);
        settingsSecurity.removeConfiguration(config);

        assertThat(first.getName()).isEqualTo("first");
        assertThat(first.getValue()).isEqualTo("one");
        assertThat(config.getName()).isEqualTo("sample");
        assertThat(config.getProperties()).containsExactly(second);
        assertThat(settingsSecurity.getMasterSource()).isEqualTo("master");
        assertThat(settingsSecurity.getMasterCipher()).isEqualTo(CIPHER);
        assertThat(settingsSecurity.getConfigurations()).isEmpty();
    }

    @Test
    void writerOmitsNullOptionalFieldsAndSupportsWriterOutput() throws Exception {
        SettingsSecurity security = new SettingsSecurity();
        StringWriter writer = new StringWriter();
        SecurityConfigurationStaxWriter staxWriter = new SecurityConfigurationStaxWriter();

        staxWriter.write(writer, security);
        SettingsSecurity parsed = new SecurityConfigurationStaxReader().read(new StringReader(writer.toString()));

        assertThat(writer.toString())
                .contains("<settingsSecurity")
                .doesNotContain("<masterSource>", "<masterCipher>", "<configurations>");
        assertThat(parsed.getMasterSource()).isNull();
        assertThat(parsed.getMasterCipher()).isNull();
        assertThat(parsed.getConfigurations()).isEmpty();
    }

    private static PlexusCipher cipher() {
        return new DefaultPlexusCipher(Map.of(CIPHER, new AESGCMNoPadding()));
    }

    private static DefaultSecDispatcher dispatcher(
            PlexusCipher cipher,
            Path configurationFile,
            Map<String, MasterPasswordSource> masterPasswordSources,
            Map<String, Dispatcher> dispatchers) {
        Map<String, MasterPasswordSource> sources = new HashMap<>();
        sources.put("test", source -> source);
        sources.putAll(masterPasswordSources);
        return new DefaultSecDispatcher(cipher, sources, dispatchers, configurationFile.toString());
    }

    private static SettingsSecurity settingsSecurity(String masterSource) {
        SettingsSecurity settingsSecurity = new SettingsSecurity();
        settingsSecurity.setMasterSource(masterSource);
        settingsSecurity.setMasterCipher(CIPHER);
        return settingsSecurity;
    }

    private static Config config(String name, ConfigProperty... properties) {
        Config config = new Config();
        config.setName(name);
        for (ConfigProperty property : properties) {
            config.addProperty(property);
        }
        return config;
    }

    private static ConfigProperty property(String name, String value) {
        ConfigProperty property = new ConfigProperty();
        property.setName(name);
        property.setValue(value);
        return property;
    }

    private static void writeSettingsSecurity(Path file, SettingsSecurity settingsSecurity) throws Exception {
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            new SecurityConfigurationStaxWriter().write(writer, settingsSecurity);
        }
    }
}
