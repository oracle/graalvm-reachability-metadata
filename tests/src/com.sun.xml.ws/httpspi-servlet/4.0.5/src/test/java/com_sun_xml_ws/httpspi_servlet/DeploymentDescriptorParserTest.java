/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_ws.httpspi_servlet;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.transform.Source;

import com.sun.xml.ws.transport.httpspi.servlet.DeploymentDescriptorParser;
import com.sun.xml.ws.transport.httpspi.servlet.ResourceLoader;
import jakarta.jws.WebService;
import jakarta.xml.ws.WebServiceFeature;
import jakarta.xml.ws.soap.MTOMFeature;
import org.junit.jupiter.api.Test;

public class DeploymentDescriptorParserTest {
    private static final String IMPLEMENTATION_NAME =
            "com_sun_xml_ws.httpspi_servlet.DeploymentDescriptorParserTest$EchoEndpoint";
    private static final String SOAP_HTTP_BINDING =
            "http://schemas.xmlsoap.org/wsdl/soap/http";
    private static final String DESCRIPTOR =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <endpoints xmlns="http://java.sun.com/xml/ns/jax-ws/ri/runtime" version="2.0">
              <endpoint name="echo"
                        implementation="%s"
                        service="{urn:test}EchoService"
                        port="{urn:test}EchoPort"
                        binding="##SOAP11_HTTP"
                        enable-mtom="true"
                        mtom-threshold-value="1024"
                        url-pattern="/echo/*"/>
            </endpoints>
            """
                    .formatted(IMPLEMENTATION_NAME);

    @Test
    void parsesEndpointConfigurationAndLoadsItsImplementation() throws IOException {
        DeploymentDescriptorParser<EndpointDefinition> parser =
                new DeploymentDescriptorParser<>(
                        getClass().getClassLoader(),
                        new EmptyResourceLoader(),
                        DeploymentDescriptorParserTest::endpointDefinition);

        List<EndpointDefinition> endpoints =
                parser.parse(
                        "memory:sun-jaxws.xml",
                        new ByteArrayInputStream(DESCRIPTOR.getBytes(StandardCharsets.UTF_8)));

        assertThat(endpoints).singleElement().satisfies(this::assertEndpointDefinition);
    }

    private void assertEndpointDefinition(EndpointDefinition endpoint) {
        assertThat(endpoint.name()).isEqualTo("echo");
        assertThat(endpoint.urlPattern()).isEqualTo("/echo/*");
        assertThat(endpoint.implementationName()).isEqualTo(IMPLEMENTATION_NAME);
        assertThat(endpoint.serviceName()).isEqualTo(new QName("urn:test", "EchoService"));
        assertThat(endpoint.portName()).isEqualTo(new QName("urn:test", "EchoPort"));
        assertThat(endpoint.bindingId()).isEqualTo(SOAP_HTTP_BINDING);
        assertThat(endpoint.metadata()).isEmpty();
        assertThat(endpoint.features()).singleElement().isInstanceOf(MTOMFeature.class);

        MTOMFeature mtom = (MTOMFeature) endpoint.features().get(0);
        assertThat(mtom.isEnabled()).isTrue();
        assertThat(mtom.getThreshold()).isEqualTo(1024);
    }

    private static EndpointDefinition endpointDefinition(
            String name,
            String urlPattern,
            Class<?> implementation,
            QName serviceName,
            QName portName,
            String bindingId,
            List<Source> metadata,
            WebServiceFeature... features) {
        return new EndpointDefinition(
                name,
                urlPattern,
                implementation.getName(),
                serviceName,
                portName,
                bindingId,
                metadata,
                List.of(features));
    }

    private record EndpointDefinition(
            String name,
            String urlPattern,
            String implementationName,
            QName serviceName,
            QName portName,
            String bindingId,
            List<Source> metadata,
            List<WebServiceFeature> features) {}

    private static final class EmptyResourceLoader implements ResourceLoader {
        @Override
        public URL getResource(String path) throws MalformedURLException {
            return null;
        }

        @Override
        public URL getCatalogFile() throws MalformedURLException {
            return null;
        }

        @Override
        public Set<String> getResourcePaths(String path) {
            return Set.of();
        }
    }

    @WebService(
            targetNamespace = "urn:test",
            serviceName = "EchoService",
            portName = "EchoPort")
    public static class EchoEndpoint {
        public String echo(String request) {
            return request;
        }
    }
}
