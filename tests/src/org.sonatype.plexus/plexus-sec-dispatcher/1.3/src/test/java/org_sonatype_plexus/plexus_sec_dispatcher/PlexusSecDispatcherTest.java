/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_plexus.plexus_sec_dispatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
import org.sonatype.plexus.components.cipher.PlexusCipher;
import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.PasswordDecryptor;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;
import org.sonatype.plexus.components.sec.dispatcher.SecUtil;
import org.sonatype.plexus.components.sec.dispatcher.model.Config;
import org.sonatype.plexus.components.sec.dispatcher.model.ConfigProperty;
import org.sonatype.plexus.components.sec.dispatcher.model.SettingsSecurity;
import org.sonatype.plexus.components.sec.dispatcher.model.io.xpp3.SecurityConfigurationXpp3Reader;
import org.sonatype.plexus.components.sec.dispatcher.model.io.xpp3.SecurityConfigurationXpp3Writer;

public class PlexusSecDispatcherTest {
    private static final String MASTER_PASSWORD_PROPERTY = "settings.security";

    @Test
    void modelObjectsExposeMutableSecurityConfiguration() {
        SettingsSecurity security = new SettingsSecurity();
        Config wagonConfiguration = config("wagon", property("timeout", "30"), property("proxy", "disabled"));
        Config gpgConfiguration = config("gpg", property("keyname", "release"));

        assertThat(security.getModelEncoding()).isEqualTo("UTF-8");
        assertThat(security.getConfigurations()).isEmpty();

        security.setMaster("master-value");
        security.setRelocation("file:///tmp/settings-security.xml");
        security.setModelEncoding("ISO-8859-1");
        security.addConfiguration(wagonConfiguration);
        security.addConfiguration(gpgConfiguration);

        assertThat(security.getMaster()).isEqualTo("master-value");
        assertThat(security.getRelocation()).isEqualTo("file:///tmp/settings-security.xml");
        assertThat(security.getModelEncoding()).isEqualTo("ISO-8859-1");
        assertThat(security.getConfigurations()).containsExactly(wagonConfiguration, gpgConfiguration);

        wagonConfiguration.removeProperty((ConfigProperty) wagonConfiguration.getProperties().get(0));
        security.removeConfiguration(gpgConfiguration);

        assertThat(wagonConfiguration.getProperties()).extracting(property -> ((ConfigProperty) property).getName())
                .containsExactly("proxy");
        assertThat(security.getConfigurations()).containsExactly(wagonConfiguration);
    }

    @Test
    void xpp3WriterAndReaderRoundTripFullSettingsSecurityModel() throws Exception {
        SettingsSecurity original = new SettingsSecurity();
        original.setMaster("{encrypted-master&value}");
        original.setRelocation("file:///opt/maven/settings-security.xml");
        original.addConfiguration(config("custom", property("alpha", "one"), property("escaped", "<two & three>")));

        StringWriter xml = new StringWriter();
        new SecurityConfigurationXpp3Writer().write(xml, original);

        assertThat(xml.toString())
                .contains("<settingsSecurity>")
                .contains("<master>{encrypted-master&amp;value}</master>")
                .contains("<value>&lt;two &amp; three></value>");

        SettingsSecurity parsed = new SecurityConfigurationXpp3Reader().read(new StringReader(xml.toString()));

        assertThat(parsed.getMaster()).isEqualTo(original.getMaster());
        assertThat(parsed.getRelocation()).isEqualTo(original.getRelocation());
        assertThat(parsed.getConfigurations()).hasSize(1);
        Config parsedConfig = (Config) parsed.getConfigurations().get(0);
        assertThat(parsedConfig.getName()).isEqualTo("custom");
        assertThat(SecUtil.getConfig(parsed, "custom"))
                .containsEntry("alpha", "one")
                .containsEntry("escaped", "<two & three>");
    }

    @Test
    void readerCanBeConfiguredForDefaultEntitiesAndStrictUnknownElementHandling() throws Exception {
        String xml = """
                <settingsSecurity>
                  <master>&amp;lt;encoded&amp;gt;</master>
                </settingsSecurity>
                """;
        SecurityConfigurationXpp3Reader reader = new SecurityConfigurationXpp3Reader();

        reader.setAddDefaultEntities(false);
        SettingsSecurity parsed = reader.read(new StringReader(xml), false);

        assertThat(reader.getAddDefaultEntities()).isFalse();
        assertThat(parsed.getMaster()).isEqualTo("&lt;encoded&gt;");
        assertThatThrownBy(() -> reader.read(new StringReader("""
                <settingsSecurity>
                  <unknown>value</unknown>
                </settingsSecurity>
                """), true)).hasMessageContaining("Unrecognised tag: 'unknown'");
    }

    @Test
    void secUtilReadsFilesUrlsAndFollowsRelocationsWhenRequested() throws Exception {
        SettingsSecurity relocatedSecurity = new SettingsSecurity();
        relocatedSecurity.setMaster("relocated-master");
        relocatedSecurity.addConfiguration(config("target", property("name", "value")));
        Path relocatedFile = writeSecurityFile("relocated-settings-security.xml", relocatedSecurity);

        SettingsSecurity primarySecurity = new SettingsSecurity();
        primarySecurity.setMaster("primary-master");
        primarySecurity.setRelocation(relocatedFile.toUri().toString());
        Path primaryFile = writeSecurityFile("primary-settings-security.xml", primarySecurity);

        SettingsSecurity withoutRelocation = SecUtil.read(primaryFile.toString(), false);
        SettingsSecurity withRelocation = SecUtil.read(primaryFile.toUri().toString(), true);

        assertThat(withoutRelocation.getMaster()).isEqualTo("primary-master");
        assertThat(withoutRelocation.getRelocation()).isEqualTo(relocatedFile.toUri().toString());
        assertThat(withRelocation.getMaster()).isEqualTo("relocated-master");
        assertThat(SecUtil.getConfig(withRelocation, "target")).containsEntry("name", "value");
        assertThat(SecUtil.getConfig(withRelocation, null)).isNull();
        assertThat(SecUtil.getConfig(withRelocation, "missing")).isNull();
        assertThatThrownBy(() -> SecUtil.read(null, true))
                .isInstanceOf(SecDispatcherException.class)
                .hasMessageContaining("location to read from is null");
    }

    @Test
    void defaultDispatcherDecryptsDecoratedServerPasswordsUsingEncryptedMasterPassword() throws Exception {
        PlexusCipher cipher = new DefaultPlexusCipher();
        String masterPassword = "unit-test-master";
        String serverPassword = "server-password-123";
        SettingsSecurity security = new SettingsSecurity();
        security.setMaster(cipher.encryptAndDecorate(masterPassword, MASTER_PASSWORD_PROPERTY));
        Path securityFile = writeSecurityFile("dispatcher-settings-security.xml", security);
        TestableDispatcher dispatcher = new TestableDispatcher(cipher);
        dispatcher.setConfigurationFile(securityFile.toString());

        String encryptedServerPassword = cipher.encryptAndDecorate(serverPassword, masterPassword);

        withSecurityLocation(securityFile, () -> {
            assertThat(dispatcher.decrypt(encryptedServerPassword)).isEqualTo(serverPassword);
            assertThat(dispatcher.decrypt("already-plain-text")).isEqualTo("already-plain-text");
            assertThat(dispatcher.getConfigurationFile()).isEqualTo(securityFile.toString());
        });
    }

    @Test
    void defaultDispatcherDelegatesAttributedEncryptedStringsToRegisteredPasswordDecryptor() throws Exception {
        PlexusCipher cipher = new DefaultPlexusCipher();
        SettingsSecurity security = new SettingsSecurity();
        security.setMaster("unused-by-attributed-decryptor");
        security.addConfiguration(config("custom", property("token", "configured-token")));
        Path securityFile = writeSecurityFile("attributed-settings-security.xml", security);
        TestableDispatcher dispatcher = new TestableDispatcher(cipher);
        dispatcher.setConfigurationFile(securityFile.toString());
        dispatcher.addDecryptor("custom", (password, attributes, configuration) -> {
            assertThat(password).isEqualTo("cipher-payload");
            assertThat(attributes).containsEntry("type", "custom").containsEntry("realm", "test");
            assertThat(configuration).containsEntry("token", "configured-token");
            return "delegated-" + password;
        });

        String attributedPassword = cipher.decorate("[type=custom, realm=test]cipher-payload");

        withSecurityLocation(securityFile,
                () -> assertThat(dispatcher.decrypt(attributedPassword)).isEqualTo("delegated-cipher-payload"));
    }

    @Test
    void dispatcherReportsMissingDecryptorsForAttributedPasswords() throws Exception {
        PlexusCipher cipher = new DefaultPlexusCipher();
        SettingsSecurity security = new SettingsSecurity();
        security.setMaster("unused-by-attributed-decryptor");
        Path securityFile = writeSecurityFile("missing-decryptor-settings-security.xml", security);
        TestableDispatcher dispatcher = new TestableDispatcher(cipher);
        dispatcher.setConfigurationFile(securityFile.toString());

        withSecurityLocation(securityFile, () -> assertThatThrownBy(
                () -> dispatcher.decrypt(cipher.decorate("[type=missing]payload")))
                .isInstanceOf(SecDispatcherException.class)
                .hasMessageContaining("no dispatcher for hint missing"));
    }

    private Path writeSecurityFile(String fileName, SettingsSecurity security) throws Exception {
        StringWriter xml = new StringWriter();
        new SecurityConfigurationXpp3Writer().write(xml, security);
        Path directory = Paths.get("build", "plexus-sec-dispatcher-test-files");
        Files.createDirectories(directory);
        Path file = directory.resolve(fileName);
        Files.write(file, xml.toString().getBytes(StandardCharsets.UTF_8));
        return file.toAbsolutePath();
    }

    private static void withSecurityLocation(Path location, ThrowingRunnable action) throws Exception {
        String previousLocation = System.getProperty(DefaultSecDispatcher.SYSTEM_PROPERTY_SEC_LOCATION);
        System.setProperty(DefaultSecDispatcher.SYSTEM_PROPERTY_SEC_LOCATION, location.toString());
        try {
            action.run();
        } finally {
            if (previousLocation == null) {
                System.clearProperty(DefaultSecDispatcher.SYSTEM_PROPERTY_SEC_LOCATION);
            } else {
                System.setProperty(DefaultSecDispatcher.SYSTEM_PROPERTY_SEC_LOCATION, previousLocation);
            }
        }
    }

    private static Config config(String name, ConfigProperty... properties) {
        Config config = new Config();
        config.setName(name);
        Arrays.stream(properties).forEach(config::addProperty);
        return config;
    }

    private static ConfigProperty property(String name, String value) {
        ConfigProperty property = new ConfigProperty();
        property.setName(name);
        property.setValue(value);
        return property;
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class TestableDispatcher extends DefaultSecDispatcher {
        TestableDispatcher(PlexusCipher cipher) {
            _cipher = cipher;
            _decryptors = new HashMap<String, PasswordDecryptor>();
        }

        void addDecryptor(String type, PasswordDecryptor decryptor) {
            _decryptors.put(type, decryptor);
        }
    }
}
