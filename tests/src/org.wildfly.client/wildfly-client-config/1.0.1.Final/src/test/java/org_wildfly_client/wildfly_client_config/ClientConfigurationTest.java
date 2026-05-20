/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wildfly_client.wildfly_client_config;

import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.wildfly.client.config.ClientConfiguration;
import org.wildfly.client.config.ConfigurationXMLStreamReader;

public class ClientConfigurationTest {
    private static final String SELECTED_NAMESPACE = "urn:example:test";
    private static final String CONFIGURATION = """
            <configuration xmlns="urn:wildfly:client:1.0">
                <selected xmlns="urn:example:test" value="loaded"/>
            </configuration>
            """;

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsConfigurationResourceFromClassLoaderRoot() throws Exception {
        Path configurationFile = writeConfiguration("wildfly-config.xml");

        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[] {temporaryDirectory.toUri().toURL()}, null)) {
            ClientConfiguration configuration = ClientConfiguration.getInstance(classLoader);

            assertThat(configuration).isNotNull();
            assertThat(configuration.getConfigurationUri()).isEqualTo(configurationFile.toUri());
            assertSelectedElementCanBeRead(configuration);
        }
    }

    @Test
    void loadsConfigurationResourceFromMetaInfWhenRootResourceIsMissing() throws Exception {
        Path configurationFile = writeConfiguration("META-INF/wildfly-config.xml");

        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[] {temporaryDirectory.toUri().toURL()}, null)) {
            ClientConfiguration configuration = ClientConfiguration.getInstance(classLoader);

            assertThat(configuration).isNotNull();
            assertThat(configuration.getConfigurationUri()).isEqualTo(configurationFile.toUri());
            assertSelectedElementCanBeRead(configuration);
        }
    }

    @Test
    void returnsNullWhenClassLoaderHasNoConfigurationResources() throws Exception {
        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[] {temporaryDirectory.toUri().toURL()}, null)) {
            ClientConfiguration configuration = ClientConfiguration.getInstance(classLoader);

            assertThat(configuration).isNull();
        }
    }

    private Path writeConfiguration(String resourcePath) throws Exception {
        Path configurationFile = temporaryDirectory.resolve(resourcePath);
        Files.createDirectories(configurationFile.getParent());
        Files.writeString(configurationFile, CONFIGURATION, StandardCharsets.UTF_8);
        return configurationFile;
    }

    private void assertSelectedElementCanBeRead(ClientConfiguration configuration) throws Exception {
        try (ConfigurationXMLStreamReader reader = configuration.readConfiguration(Set.of(SELECTED_NAMESPACE))) {
            assertThat(reader.hasNext()).isTrue();
            assertThat(reader.next()).isEqualTo(START_ELEMENT);
            assertThat(reader.getNamespaceURI()).isEqualTo(SELECTED_NAMESPACE);
            assertThat(reader.getLocalName()).isEqualTo("selected");
            assertThat(reader.getAttributeValue(null, "value")).isEqualTo("loaded");
        }
    }
}
