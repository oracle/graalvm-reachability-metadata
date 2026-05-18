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
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.codehaus.plexus.components.cipher.internal.AESGCMNoPadding;
import org.codehaus.plexus.components.cipher.internal.Cipher;
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
    private static final String MASTER_SOURCE_NAME = "literal";
    private static final String MASTER_SOURCE_PREFIX = MASTER_SOURCE_NAME + ":";

    @TempDir
    Path temporaryDirectory;

    @Test
    void dispatcherDecryptsDecoratedPasswordsWithMasterPasswordFromSettingsSecurity() throws Exception {
        DefaultPlexusCipher cipher = cipher();
        String masterPassword = "master-password";
        String serverPassword = "server-password";
        String encryptedServerPassword = cipher.encryptAndDecorate(AESGCMNoPadding.CIPHER_ALG, serverPassword,
                masterPassword);
        Path configurationFile = temporaryDirectory.resolve("settings-security.xml");
        writeSettingsSecurity(configurationFile, settingsSecurity(masterPassword));

        DefaultSecDispatcher dispatcher = dispatcher(cipher, new HashMap<>(), configurationFile);

        assertThat(dispatcher.getConfigurationFile()).isEqualTo(configurationFile.toString());
        assertThat(dispatcher.availableCiphers()).contains(AESGCMNoPadding.CIPHER_ALG);
        assertThat(dispatcher.decrypt("not encrypted")).isEqualTo("not encrypted");
        assertThat(dispatcher.decrypt(encryptedServerPassword)).isEqualTo(serverPassword);
        assertThat(dispatcher.encrypt(serverPassword, null)).startsWith("{");
    }

    @Test
    void dispatcherDelegatesTypedPasswordsToConfiguredDispatcher() throws Exception {
        DefaultPlexusCipher cipher = cipher();
        Path configurationFile = temporaryDirectory.resolve("typed-settings-security.xml");
        SettingsSecurity settingsSecurity = settingsSecurity("master-password");
        settingsSecurity.addConfiguration(
                config("vault", property("endpoint", "local-vault"), property("profile", "test")));
        writeSettingsSecurity(configurationFile, settingsSecurity);
        Map<String, Dispatcher> dispatchers = new HashMap<>();
        dispatchers.put("vault", new Dispatcher() {
            @Override
            public String encrypt(String str, Map<String, String> attributes, Map<String, String> config) {
                return config.get("endpoint") + ":" + attributes.get("alias") + ":" + str;
            }

            @Override
            public String decrypt(String str, Map<String, String> attributes, Map<String, String> config) {
                assertThat(str).isEqualTo("credential-id");
                assertThat(attributes).containsEntry("name", "vault").containsEntry("alias", "database");
                assertThat(config)
                        .containsEntry("endpoint", "local-vault")
                        .containsEntry("profile", "test")
                        .containsEntry(Dispatcher.CONF_MASTER_PASSWORD, "master-password");
                return config.get("endpoint") + ":" + attributes.get("alias") + ":" + str;
            }
        });
        DefaultSecDispatcher dispatcher = dispatcher(cipher, dispatchers, configurationFile);
        String typedPassword = cipher.decorate("[name=vault,alias=database]credential-id");

        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("name", "vault");
        attributes.put("alias", "database");

        assertThat(dispatcher.availableDispatchers()).containsExactlyInAnyOrder("vault");
        assertThat(dispatcher.decrypt(typedPassword)).isEqualTo("local-vault:database:credential-id");
        assertThat(dispatcher.encrypt("credential-id", attributes))
                .isEqualTo(cipher.decorate("[name=vault,alias=database]local-vault:database:credential-id"));
    }

    @Test
    void dispatcherReportsMissingTypedPasswordDispatcher() throws Exception {
        DefaultPlexusCipher cipher = cipher();
        Path configurationFile = temporaryDirectory.resolve("missing-dispatcher-settings-security.xml");
        writeSettingsSecurity(configurationFile, settingsSecurity("master-password"));
        DefaultSecDispatcher dispatcher = dispatcher(cipher, new HashMap<>(), configurationFile);
        String typedPassword = cipher.decorate("[name=missing]credential-id");

        assertThatThrownBy(() -> dispatcher.decrypt(typedPassword))
                .isInstanceOf(SecDispatcherException.class)
                .hasMessageContaining("no dispatcher for name missing");
    }

    @Test
    void dispatcherUsesSystemPropertyConfigurationLocationOverride() throws Exception {
        DefaultPlexusCipher cipher = cipher();
        String configuredMasterPassword = "configured-master-password";
        String overrideMasterPassword = "override-master-password";
        Path configuredFile = temporaryDirectory.resolve("configured-settings-security.xml");
        Path overrideFile = temporaryDirectory.resolve("override-settings-security.xml");
        writeSettingsSecurity(configuredFile, settingsSecurity(configuredMasterPassword));
        writeSettingsSecurity(overrideFile, settingsSecurity(overrideMasterPassword));
        DefaultSecDispatcher dispatcher = dispatcher(cipher, new HashMap<>(), configuredFile);
        String previousLocation = System.getProperty(SecDispatcher.SYSTEM_PROPERTY_CONFIGURATION_LOCATION);

        try {
            System.setProperty(SecDispatcher.SYSTEM_PROPERTY_CONFIGURATION_LOCATION, overrideFile.toString());

            assertThat(dispatcher.decrypt(cipher.encryptAndDecorate(AESGCMNoPadding.CIPHER_ALG, "server-password",
                    overrideMasterPassword))).isEqualTo("server-password");
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
        Path configurationFile = temporaryDirectory.resolve("written-settings-security.xml");
        SettingsSecurity initial = settingsSecurity("initial-master");
        initial.addConfiguration(config("initial", property("one", "first")));
        SecUtil.write(configurationFile, initial, true);

        SettingsSecurity replacement = settingsSecurity("replacement-master");
        replacement.addConfiguration(config("replacement", property("two", "second"), property("three", "third")));
        SecUtil.write(configurationFile, replacement, true);

        SettingsSecurity actual = SecUtil.read(configurationFile);

        assertThat(actual.getMasterSource()).isEqualTo(MASTER_SOURCE_PREFIX + "replacement-master");
        assertThat(actual.getMasterCipher()).isEqualTo(AESGCMNoPadding.CIPHER_ALG);
        assertThat(SecUtil.getConfig(actual, "replacement"))
                .containsEntry("two", "second")
                .containsEntry("three", "third");
        assertThat(SecUtil.getConfig(actual, "absent")).isNull();
        assertThat(SecUtil.getConfig(actual, null)).isNull();
        assertThat(SecUtil.read(temporaryDirectory.resolve("missing-settings-security.xml"))).isNull();
        assertThat(Files.exists(configurationFile.resolveSibling(configurationFile.getFileName() + ".bak"))).isTrue();
    }

    @Test
    void secDispatcherReadsAndWritesEffectiveConfiguration() throws Exception {
        DefaultPlexusCipher cipher = cipher();
        Path configurationFile = temporaryDirectory.resolve("effective-settings-security.xml");
        DefaultSecDispatcher dispatcher = dispatcher(cipher, new HashMap<>(), configurationFile);
        SettingsSecurity configuration = settingsSecurity("effective-master");
        configuration.addConfiguration(config("server", property("url", "https://repo.example")));

        assertThat(dispatcher.readConfiguration(false)).isNull();
        assertThat(dispatcher.readConfiguration(true).getConfigurations()).isEmpty();
        dispatcher.writeConfiguration(configuration);
        SettingsSecurity actual = dispatcher.readConfiguration(false);

        assertThat(actual.getMasterSource()).isEqualTo(MASTER_SOURCE_PREFIX + "effective-master");
        assertThat(SecUtil.getConfig(actual, "server")).containsEntry("url", "https://repo.example");
    }

    @Test
    void staxWriterAndReaderRoundTripSettingsSecurityFromStreams() throws Exception {
        SettingsSecurity expected = settingsSecurity("master<&>value");
        expected.setModelVersion("test-model-version");
        expected.setModelEncoding(StandardCharsets.UTF_8.name());
        expected.addConfiguration(config("server", property("url", "https://repo.example/path?a=1&b=2")));
        expected.addConfiguration(config("token", property("id", "abc-123"), property("scope", "read")));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        new SecurityConfigurationStaxWriter().write(outputStream, expected);
        String xml = outputStream.toString(StandardCharsets.UTF_8.name());
        SettingsSecurity actual = new SecurityConfigurationStaxReader()
                .read(new ByteArrayInputStream(outputStream.toByteArray()));

        assertThat(xml).contains("<settingsSecurity", "<masterSource>");
        assertThat(actual.getMasterSource()).isEqualTo(expected.getMasterSource());
        assertThat(actual.getMasterCipher()).isEqualTo(expected.getMasterCipher());
        assertThat(actual.getModelVersion()).isEqualTo("test-model-version");
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
                  <masterSource> literal:raw-master </masterSource>
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

        assertThat(settingsSecurity.getMasterSource()).isEqualTo("literal:raw-master");
        assertThat(settingsSecurity.getMasterCipher()).isEqualTo(AESGCMNoPadding.CIPHER_ALG);
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
        assertThat(settingsSecurity.getMasterSource()).isEqualTo(MASTER_SOURCE_PREFIX + "master");
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

    private static DefaultPlexusCipher cipher() {
        Map<String, Cipher> ciphers = Map.of(AESGCMNoPadding.CIPHER_ALG, new AESGCMNoPadding());
        return new DefaultPlexusCipher(ciphers);
    }

    private static DefaultSecDispatcher dispatcher(
            DefaultPlexusCipher cipher, Map<String, Dispatcher> dispatchers, Path configurationFile) {
        return new DefaultSecDispatcher(cipher, masterPasswordSources(), dispatchers, configurationFile.toString());
    }

    private static Map<String, MasterPasswordSource> masterPasswordSources() {
        return Map.of(MASTER_SOURCE_NAME, masterSource -> {
            if (masterSource == null || !masterSource.startsWith(MASTER_SOURCE_PREFIX)) {
                return null;
            }
            return masterSource.substring(MASTER_SOURCE_PREFIX.length());
        });
    }

    private static SettingsSecurity settingsSecurity(String masterPassword) {
        SettingsSecurity settingsSecurity = new SettingsSecurity();
        settingsSecurity.setMasterSource(MASTER_SOURCE_PREFIX + masterPassword);
        settingsSecurity.setMasterCipher(AESGCMNoPadding.CIPHER_ALG);
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
