/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package wsdl4j.wsdl4j;

import static org.assertj.core.api.Assertions.assertThat;

import javax.wsdl.Definition;
import javax.wsdl.factory.WSDLFactory;
import javax.xml.namespace.QName;
import org.junit.jupiter.api.Test;

public class WSDLFactoryAnonymous1Test {
    @Test
    void defaultFactoryCreatesPopulatedDefinition() throws Exception {
        WSDLFactory factory = WSDLFactory.newInstance();
        Definition definition = factory.newDefinition();
        QName serviceName = new QName("https://example.org/weather", "WeatherService");

        definition.setQName(serviceName);
        definition.setTargetNamespace(serviceName.getNamespaceURI());

        assertThat(definition.getQName()).isEqualTo(serviceName);
        assertThat(definition.getTargetNamespace()).isEqualTo("https://example.org/weather");
        assertThat(definition.getExtensionRegistry()).isNotNull();
    }
}
