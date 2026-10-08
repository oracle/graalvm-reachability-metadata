/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_ws.httpspi_servlet;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.util.List;
import java.util.Set;

import com.sun.xml.ws.transport.httpspi.servlet.DeploymentDescriptorParser;
import com.sun.xml.ws.transport.httpspi.servlet.ResourceLoader;
import org.junit.jupiter.api.Test;

public class DeploymentDescriptorParserTest {
    @Test
    void parsesEndpointAndCreatesAdapter() throws Exception {
        String descriptor = """
                <endpoints xmlns="http://java.sun.com/xml/ns/jax-ws/ri/runtime" version="2.0">
                  <endpoint name="echo"
                            implementation="%s"
                            url-pattern="/echo"/>
                </endpoints>
                """.formatted(DeploymentDescriptorParserTest.class.getName());
        DeploymentDescriptorParser<String> parser = new DeploymentDescriptorParser<>(
                DeploymentDescriptorParserTest.class.getClassLoader(),
                new EmptyResourceLoader(),
                (name, urlPattern, implementation, serviceName, portName, bindingId, metadata, features) ->
                        name + ":" + urlPattern + ":" + implementation.getName());

        List<String> adapters = parser.parse(
                "memory:sun-jaxws.xml", new ByteArrayInputStream(descriptor.getBytes(UTF_8)));

        assertThat(adapters).containsExactly(
                "echo:/echo:" + DeploymentDescriptorParserTest.class.getName());
    }

    private static final class EmptyResourceLoader implements ResourceLoader {
        @Override
        public URL getResource(final String path) {
            return null;
        }

        @Override
        public URL getCatalogFile() {
            return null;
        }

        @Override
        public Set<String> getResourcePaths(final String path) {
            return Set.of();
        }
    }
}
