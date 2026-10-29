/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_settings_builder;

import org.apache.maven.settings.Settings;
import org.apache.maven.settings.io.DefaultSettingsReader;
import org.apache.maven.settings.io.SettingsParseException;
import org.apache.maven.settings.io.SettingsReader;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DefaultSettingsReaderTest {
    @Test
    void readsUtf8SettingsAndNestedServerConfigurationFromStream() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <settings>
                  <servers>
                    <server>
                      <id>deployment</id><username>Andr\u00e9</username><password>p&amp;ss&lt;word&gt;</password>
                      <configuration><httpHeaders><property><name>X-Repository</name><value>private</value></property></httpHeaders></configuration>
                    </server>
                  </servers>
                  <profiles>
                    <profile>
                      <id>ci</id>
                      <activation><property><name>ci.build</name><value>true</value></property></activation>
                      <properties><build.channel>continuous</build.channel></properties>
                      <pluginRepositories>
                        <pluginRepository><id>plugins</id><url>https://plugins.example/repo</url></pluginRepository>
                      </pluginRepositories>
                    </profile>
                  </profiles>
                </settings>
                """;
        SettingsReader reader = new DefaultSettingsReader();

        Settings settings = reader.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), null);

        assertThat(settings.getServers()).hasSize(1);
        assertThat(settings.getServers().get(0).getUsername()).isEqualTo("Andr\u00e9");
        assertThat(settings.getServers().get(0).getPassword()).isEqualTo("p&ss<word>");
        Xpp3Dom configuration = (Xpp3Dom) settings.getServers().get(0).getConfiguration();
        Xpp3Dom header = configuration.getChild("httpHeaders").getChild("property");
        assertThat(header.getChild("name").getValue()).isEqualTo("X-Repository");
        assertThat(header.getChild("value").getValue()).isEqualTo("private");
        assertThat(settings.getProfiles()).hasSize(1);
        assertThat(settings.getProfiles().get(0).getActivation().getProperty().getName()).isEqualTo("ci.build");
        assertThat(settings.getProfiles().get(0).getProperties().getProperty("build.channel")).isEqualTo("continuous");
        assertThat(settings.getProfiles().get(0).getPluginRepositories().get(0).getUrl())
                .isEqualTo("https://plugins.example/repo");
    }

    @Test
    void supportsExplicitLenientParsingOfUnknownElements() throws Exception {
        String xml = """
                <settings>
                  <extension><option>custom</option></extension>
                  <offline>true</offline>
                </settings>
                """;
        SettingsReader reader = new DefaultSettingsReader();

        SettingsParseException exception = assertThrows(SettingsParseException.class,
                () -> reader.read(new StringReader(xml), null));
        assertThat(exception.getMessage()).contains("extension");
        assertThat(exception.getLineNumber()).isPositive();
        assertThat(exception.getColumnNumber()).isPositive();

        Settings settings = reader.read(new StringReader(xml), Map.of(SettingsReader.IS_STRICT, false));
        assertThat(settings.isOffline()).isTrue();
    }
}
