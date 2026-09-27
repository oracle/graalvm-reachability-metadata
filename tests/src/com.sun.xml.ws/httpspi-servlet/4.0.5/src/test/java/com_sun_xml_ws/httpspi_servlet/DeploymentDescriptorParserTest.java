/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_ws.httpspi_servlet;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.sun.xml.ws.transport.httpspi.servlet.DeploymentDescriptorParser;
import com.sun.xml.ws.transport.httpspi.servlet.ServletResourceLoader;
import jakarta.jws.WebService;
import org.junit.jupiter.api.Test;

public class DeploymentDescriptorParserTest {
    private static final String ENDPOINT_CLASS_NAME =
            "com_sun_xml_ws.httpspi_servlet.DeploymentDescriptorParserTest"
                    + "$GreetingEndpoint";

    @Test
    void loadsEndpointImplementationDeclaredByWebApplication() throws Exception {
        String descriptor = """
                <endpoints xmlns="http://java.sun.com/xml/ns/jax-ws/ri/runtime" version="2.0">
                    <endpoint name="greeting"
                              implementation="%s"
                              url-pattern="/services/greeting/*"/>
                </endpoints>
                """.formatted(ENDPOINT_CLASS_NAME);
        HttpspiServletTest.RecordingServletContext servletContext =
                new HttpspiServletTest.RecordingServletContext(Map.of());
        DeploymentDescriptorParser.AdapterFactory<String> factory =
                (name, urlPattern, implementation, serviceName, portName, bindingId, metadata, features) ->
                        implementation.getName();
        DeploymentDescriptorParser<String> parser = new DeploymentDescriptorParser<>(
                Thread.currentThread().getContextClassLoader(),
                new ServletResourceLoader(servletContext),
                factory);

        List<String> implementations;
        try (InputStream input = new ByteArrayInputStream(descriptor.getBytes(StandardCharsets.UTF_8))) {
            implementations = parser.parse("memory:/WEB-INF/sun-jaxws.xml", input);
        }

        assertThat(implementations).containsExactly(ENDPOINT_CLASS_NAME);
        assertThat(servletContext.requestedResources).isEmpty();
    }

    @WebService
    public static final class GreetingEndpoint {
        public String greet(final String name) {
            return "Hello, " + name;
        }
    }
}
