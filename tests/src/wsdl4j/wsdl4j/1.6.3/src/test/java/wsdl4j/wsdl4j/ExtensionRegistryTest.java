/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package wsdl4j.wsdl4j;

import static org.assertj.core.api.Assertions.assertThat;

import javax.wsdl.Binding;
import javax.wsdl.Definition;
import javax.wsdl.extensions.ExtensibilityElement;
import javax.wsdl.extensions.ExtensionRegistry;
import javax.wsdl.extensions.soap.SOAPBinding;
import javax.wsdl.factory.WSDLFactory;
import javax.xml.namespace.QName;
import org.junit.jupiter.api.Test;

public class ExtensionRegistryTest {
    private static final String FACTORY_IMPLEMENTATION = "com.ibm.wsdl.factory.WSDLFactoryImpl";
    private static final String SOAP_HTTP_TRANSPORT = "https://schemas.xmlsoap.org/soap/http";
    private static final QName SOAP_BINDING_ELEMENT = new QName(
            "http://schemas.xmlsoap.org/wsdl/soap/", "binding");

    @Test
    void populatedRegistryCreatesAndConfiguresSoapBindingExtension() throws Exception {
        Definition definition = WSDLFactory.newInstance(FACTORY_IMPLEMENTATION).newDefinition();
        Binding binding = definition.createBinding();
        binding.setQName(new QName("https://example.org/weather", "WeatherBinding"));
        ExtensionRegistry registry = definition.getExtensionRegistry();

        ExtensibilityElement extension = registry.createExtension(
                Binding.class, SOAP_BINDING_ELEMENT);
        SOAPBinding soapBinding = (SOAPBinding) extension;
        soapBinding.setStyle("document");
        soapBinding.setTransportURI(SOAP_HTTP_TRANSPORT);
        binding.addExtensibilityElement(soapBinding);

        assertThat(soapBinding.getElementType()).isEqualTo(SOAP_BINDING_ELEMENT);
        assertThat(soapBinding.getStyle()).isEqualTo("document");
        assertThat(soapBinding.getTransportURI()).isEqualTo(SOAP_HTTP_TRANSPORT);
        assertThat(binding.getExtensibilityElements()).containsExactly(soapBinding);
    }
}
