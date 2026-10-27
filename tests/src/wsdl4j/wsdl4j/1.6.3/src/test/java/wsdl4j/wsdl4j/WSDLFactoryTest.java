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

public class WSDLFactoryTest {
    private static final String FACTORY_IMPLEMENTATION = "com.ibm.wsdl.factory.WSDLFactoryImpl";

    @Test
    void factoryCreatesDefinitionsFromImplementationName() throws Exception {
        WSDLFactory factory = WSDLFactory.newInstance(FACTORY_IMPLEMENTATION);

        assertDefinitionCanStoreNamespace(factory.newDefinition());
    }

    @Test
    void factoryCreatesDefinitionsUsingProvidedClassLoader() throws Exception {
        WSDLFactory factory = WSDLFactory.newInstance(FACTORY_IMPLEMENTATION, WSDLFactory.class.getClassLoader());

        assertDefinitionCanStoreNamespace(factory.newDefinition());
    }

    private void assertDefinitionCanStoreNamespace(Definition definition) {
        QName name = new QName("https://example.org/weather", "WeatherService");
        definition.setQName(name);
        definition.setTargetNamespace(name.getNamespaceURI());

        assertThat(definition.getQName()).isEqualTo(name);
        assertThat(definition.getTargetNamespace()).isEqualTo("https://example.org/weather");
        assertThat(definition.getExtensionRegistry()).isNotNull();
    }
}
