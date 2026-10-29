/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_settings_builder;

import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.io.DefaultSettingsReader;
import org.apache.maven.settings.io.DefaultSettingsWriter;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultSettingsWriterTest {
    @TempDir
    Path tempDir;

    @Test
    void writesEscapedSettingsAndServerConfigurationToCharacterWriter() throws Exception {
        StringWriter output = new StringWriter();

        new DefaultSettingsWriter().write(output, null, settings());

        assertThat(output.toString()).contains("secret&lt;&amp;", "X-Token");
        Settings restored = new DefaultSettingsReader().read(new StringReader(output.toString()), null);
        assertRoundTrip(restored);
    }

    @Test
    void writesUtf8SettingsToByteStream() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Settings original = settings();
        original.setModelEncoding("UTF-8");

        new DefaultSettingsWriter().write(output, null, original);

        assertThat(output.toString(StandardCharsets.UTF_8)).contains("Andr\u00e9");
        Settings restored = new DefaultSettingsReader().read(new ByteArrayInputStream(output.toByteArray()), null);
        assertRoundTrip(restored);
    }

    @Test
    void createsParentDirectoriesWhenWritingSettingsFile() throws Exception {
        Path output = tempDir.resolve("config").resolve("maven").resolve("settings.xml");

        new DefaultSettingsWriter().write(output.toFile(), null, settings());

        assertThat(Files.isRegularFile(output)).isTrue();
        Settings restored = new DefaultSettingsReader().read(output.toFile(), null);
        assertRoundTrip(restored);
    }

    private Settings settings() {
        Settings settings = new Settings();
        settings.setLocalRepository(tempDir.resolve("repository").toString());
        settings.setOffline(true);
        settings.addActiveProfile("ci");
        settings.addPluginGroup("com.example.plugins");
        Server server = new Server();
        server.setId("deployment");
        server.setUsername("Andr\u00e9");
        server.setPassword("secret<&>");
        server.setPrivateKey("deployment-key");
        server.setPassphrase("key-secret");
        Xpp3Dom configuration = new Xpp3Dom("configuration");
        Xpp3Dom token = new Xpp3Dom("X-Token");
        token.setValue("token<&>");
        configuration.addChild(token);
        server.setConfiguration(configuration);
        settings.addServer(server);
        return settings;
    }

    private void assertRoundTrip(Settings settings) {
        assertThat(settings.getLocalRepository()).isEqualTo(tempDir.resolve("repository").toString());
        assertThat(settings.isOffline()).isTrue();
        assertThat(settings.getActiveProfiles()).containsExactly("ci");
        assertThat(settings.getPluginGroups()).containsExactly("com.example.plugins");
        assertThat(settings.getServers()).hasSize(1);
        Server server = settings.getServers().get(0);
        assertThat(server.getId()).isEqualTo("deployment");
        assertThat(server.getUsername()).isEqualTo("Andr\u00e9");
        assertThat(server.getPassword()).isEqualTo("secret<&>");
        assertThat(server.getPrivateKey()).isEqualTo("deployment-key");
        assertThat(server.getPassphrase()).isEqualTo("key-secret");
        Xpp3Dom configuration = (Xpp3Dom) server.getConfiguration();
        assertThat(configuration.getChild("X-Token").getValue()).isEqualTo("token<&>");
    }
}
