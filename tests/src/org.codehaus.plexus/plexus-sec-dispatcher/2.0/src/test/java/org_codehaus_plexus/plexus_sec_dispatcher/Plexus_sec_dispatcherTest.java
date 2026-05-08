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

import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.PasswordDecryptor;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;
import org.sonatype.plexus.components.sec.dispatcher.SecUtil;
import org.sonatype.plexus.components.sec.dispatcher.model.Config;
import org.sonatype.plexus.components.sec.dispatcher.model.ConfigProperty;
import org.sonatype.plexus.components.sec.dispatcher.model.SettingsSecurity;
import org.sonatype.plexus.components.sec.dispatcher.model.io.xpp3.SecurityConfigurationXpp3Reader;
import org.sonatype.plexus.components.sec.dispatcher.model.io.xpp3.SecurityConfigurationXpp3Writer;

public class Plexus_sec_dispatcherTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void dispatcherDecryptsDecoratedPasswordsWithMasterPasswordFromSettingsSecurity() throws Exception {
        DefaultPlexusCipher cipher = new DefaultPlexusCipher();
        String masterPassword = "master-password";
        String serverPassword = "server-password";
        String encryptedMaster = cipher.encryptAndDecorate(masterPassword, "settings.security");
        String encryptedServerPassword = cipher.encryptAndDecorate(serverPassword, masterPassword);
        Path configurationFile = temporaryDirectory.resolve("settings-security.xml");
        writeSettingsSecurity(configurationFile, settingsSecurity(encryptedMaster));

        DefaultSecDispatcher dispatcher = new DefaultSecDispatcher(cipher);
        dispatcher.setConfigurationFile(configurationFile.toString());

        assertThat(dispatcher.getConfigurationFile()).isEqualTo(configurationFile.toString());
        assertThat(dispatcher.decrypt("not encrypted")).isEqualTo("not encrypted");
        assertThat(dispatcher.decrypt(encryptedServerPassword)).isEqualTo(serverPassword);
    }

    @Test
    void dispatcherDelegatesTypedPasswordsToConfiguredPasswordDecryptor() throws Exception {
        DefaultPlexusCipher cipher = new DefaultPlexusCipher();
        Path configurationFile = temporaryDirectory.resolve("typed-settings-security.xml");
        SettingsSecurity settingsSecurity = settingsSecurity(null);
        settingsSecurity.addConfiguration(
                config("vault", property("endpoint", "local-vault"), property("profile", "test")));
        writeSettingsSecurity(configurationFile, settingsSecurity);
        Map<String, PasswordDecryptor> decryptors = new HashMap<>();
        decryptors.put("vault", (password, attributes, configuration) -> {
            assertThat(password).isEqualTo("credential-id");
            assertThat(attributes).containsEntry("type", "vault").containsEntry("alias", "database");
            assertThat(configuration).containsEntry("endpoint", "local-vault").containsEntry("profile", "test");
            return configuration.get("endpoint") + ":" + attributes.get("alias") + ":" + password;
        });
        DefaultSecDispatcher dispatcher = new DefaultSecDispatcher(cipher, decryptors, configurationFile.toString());
        String typedPassword = cipher.decorate("[type=vault,alias=database]credential-id");

        assertThat(dispatcher.decrypt(typedPassword)).isEqualTo("local-vault:database:credential-id");
    }

    @Test
    void dispatcherReportsMissingTypedPasswordDecryptor() throws Exception {
        DefaultPlexusCipher cipher = new DefaultPlexusCipher();
        Path configurationFile = temporaryDirectory.resolve("missing-dispatcher-settings-security.xml");
        writeSettingsSecurity(configurationFile, settingsSecurity(null));
        DefaultSecDispatcher dispatcher = new DefaultSecDispatcher(
                cipher, new HashMap<>(), configurationFile.toString());
        String typedPassword = cipher.decorate("[type=missing]credential-id");

        assertThatThrownBy(() -> dispatcher.decrypt(typedPassword))
                .isInstanceOf(SecDispatcherException.class)
                .hasMessageContaining("no dispatcher for hint missing");
    }

    @Test
    void dispatcherUsesSystemPropertyConfigurationLocationOverride() throws Exception {
        DefaultPlexusCipher cipher = new DefaultPlexusCipher();
        String configuredMasterPassword = "configured-master-password";
        String overrideMasterPassword = "override-master-password";
        Path configuredFile = temporaryDirectory.resolve("configured-settings-security.xml");
        Path overrideFile = temporaryDirectory.resolve("override-settings-security.xml");
        writeSettingsSecurity(configuredFile, settingsSecurity(cipher.encryptAndDecorate(
                configuredMasterPassword, "settings.security")));
        writeSettingsSecurity(overrideFile, settingsSecurity(cipher.encryptAndDecorate(
                overrideMasterPassword, "settings.security")));
        DefaultSecDispatcher dispatcher = new DefaultSecDispatcher(cipher, new HashMap<>(), configuredFile.toString());
        String previousLocation = System.getProperty(DefaultSecDispatcher.SYSTEM_PROPERTY_SEC_LOCATION);

        try {
            System.setProperty(DefaultSecDispatcher.SYSTEM_PROPERTY_SEC_LOCATION, overrideFile.toString());

            assertThat(dispatcher.decrypt(cipher.encryptAndDecorate("server-password", overrideMasterPassword)))
                    .isEqualTo("server-password");
        } finally {
            if (previousLocation == null) {
                System.clearProperty(DefaultSecDispatcher.SYSTEM_PROPERTY_SEC_LOCATION);
            } else {
                System.setProperty(DefaultSecDispatcher.SYSTEM_PROPERTY_SEC_LOCATION, previousLocation);
            }
        }
    }

    @Test
    void secUtilReadsFilesFollowsRelocationAndExtractsNamedConfiguration() throws Exception {
        Path relocatedFile = temporaryDirectory.resolve("relocated-settings-security.xml");
        SettingsSecurity relocated = settingsSecurity("relocated-master");
        relocated.addConfiguration(config("relocated", property("one", "first"), property("two", "second")));
        writeSettingsSecurity(relocatedFile, relocated);
        Path primaryFile = temporaryDirectory.resolve("primary-settings-security.xml");
        SettingsSecurity primary = settingsSecurity("primary-master");
        primary.setRelocation(relocatedFile.toString());
        writeSettingsSecurity(primaryFile, primary);

        SettingsSecurity withoutRelocation = SecUtil.read(primaryFile.toString(), false);
        SettingsSecurity withRelocation = SecUtil.read(primaryFile.toString(), true);

        assertThat(withoutRelocation.getMaster()).isEqualTo("primary-master");
        assertThat(withoutRelocation.getRelocation()).isEqualTo(relocatedFile.toString());
        assertThat(withRelocation.getMaster()).isEqualTo("relocated-master");
        assertThat(SecUtil.getConfig(withRelocation, "relocated"))
                .containsEntry("one", "first")
                .containsEntry("two", "second");
        assertThat(SecUtil.getConfig(withRelocation, "absent")).isNull();
        assertThat(SecUtil.getConfig(withRelocation, null)).isNull();
    }

    @Test
    void secUtilReadsSettingsSecurityFromFileUrlLocation() throws Exception {
        Path configurationFile = temporaryDirectory.resolve("url-settings-security.xml");
        SettingsSecurity expected = settingsSecurity("url-master");
        expected.addConfiguration(config("url-config", property("location", "file-url")));
        writeSettingsSecurity(configurationFile, expected);

        SettingsSecurity actual = SecUtil.read(configurationFile.toUri().toString(), false);

        assertThat(actual.getMaster()).isEqualTo("url-master");
        assertThat(actual.getConfigurations()).hasSize(1);
        Config actualConfig = actual.getConfigurations().get(0);
        assertThat(actualConfig.getName()).isEqualTo("url-config");
        assertThat(actualConfig.getProperties()).hasSize(1);
        ConfigProperty actualProperty = actualConfig.getProperties().get(0);
        assertThat(actualProperty.getName()).isEqualTo("location");
        assertThat(actualProperty.getValue()).isEqualTo("file-url");
    }

    @Test
    void xpp3WriterAndReaderRoundTripSettingsSecurityFromStreams() throws Exception {
        SettingsSecurity expected = settingsSecurity("master<&>value");
        expected.setRelocation("file:///tmp/other-settings-security.xml");
        expected.setModelEncoding(StandardCharsets.UTF_8.name());
        expected.addConfiguration(config("server", property("url", "https://repo.example/path?a=1&b=2")));
        expected.addConfiguration(config("token", property("id", "abc-123"), property("scope", "read")));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        new SecurityConfigurationXpp3Writer().write(outputStream, expected);
        String xml = outputStream.toString(StandardCharsets.UTF_8.name());
        SettingsSecurity actual = new SecurityConfigurationXpp3Reader()
                .read(new ByteArrayInputStream(outputStream.toByteArray()));

        assertThat(xml).contains("<settingsSecurity>", "<master>master&lt;&amp;>value</master>");
        assertThat(actual.getMaster()).isEqualTo(expected.getMaster());
        assertThat(actual.getRelocation()).isEqualTo(expected.getRelocation());
        assertThat(actual.getModelEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(SecUtil.getConfig(actual, "server"))
                .containsEntry("url", "https://repo.example/path?a=1&b=2");
        assertThat(SecUtil.getConfig(actual, "token")).containsEntry("id", "abc-123").containsEntry("scope", "read");
    }

    @Test
    void xpp3ReaderTransformsContentAndCanIgnoreUnknownMarkupInLenientMode() throws Exception {
        String xml = """
                <settingsSecurity unexpected="ignored">
                  <unknown><nested>ignored</nested></unknown>
                  <master> raw-master </master>
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
        SecurityConfigurationXpp3Reader reader = new SecurityConfigurationXpp3Reader(
                (source, fieldName) -> fieldName + "=" + source);
        reader.setAddDefaultEntities(false);

        assertThat(reader.getAddDefaultEntities()).isFalse();
        assertThatThrownBy(() -> new SecurityConfigurationXpp3Reader().read(new StringReader(xml), true))
                .isInstanceOf(XmlPullParserException.class)
                .hasMessageContaining("Unknown attribute 'unexpected'");
        SettingsSecurity settingsSecurity = reader.read(new StringReader(xml), false);

        assertThat(settingsSecurity.getMaster()).isEqualTo("master= raw-master");
        assertThat(SecUtil.getConfig(settingsSecurity, "name= vault"))
                .containsEntry("name= endpoint", "value= local");
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
        assertThat(settingsSecurity.getMaster()).isEqualTo("master");
        assertThat(settingsSecurity.getConfigurations()).isEmpty();
    }

    @Test
    void writerOmitsNullOptionalFieldsAndSupportsWriterOutput() throws Exception {
        SettingsSecurity security = settingsSecurity(null);
        StringWriter writer = new StringWriter();
        SecurityConfigurationXpp3Writer xpp3Writer = new SecurityConfigurationXpp3Writer();

        xpp3Writer.setFileComment("not emitted by this model writer");
        xpp3Writer.write(writer, security);
        SettingsSecurity parsed = new SecurityConfigurationXpp3Reader().read(new StringReader(writer.toString()));

        assertThat(writer.toString()).contains("<settingsSecurity").doesNotContain("<master>", "<configurations>");
        assertThat(parsed.getMaster()).isNull();
        assertThat(parsed.getConfigurations()).isEmpty();
    }

    private static SettingsSecurity settingsSecurity(String master) {
        SettingsSecurity settingsSecurity = new SettingsSecurity();
        settingsSecurity.setMaster(master);
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
            new SecurityConfigurationXpp3Writer().write(writer, settingsSecurity);
        }
    }
}
