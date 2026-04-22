/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_xml_bind.jaxb_api;

import javax.xml.bind.JAXBContext;
import javax_xml_bind.jaxb_api.noprovider.NoProviderBoundType;
import javax_xml_bind.jaxb_api.support.InterfaceContextFactory;
import javax_xml_bind.jaxb_api.support.StubJaxbContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ContextFinder2Test {
    private final String previousFactoryProperty = System.getProperty(JAXBContext.JAXB_CONTEXT_FACTORY);

    @AfterEach
    public void restoreFactoryProperty() {
        if (previousFactoryProperty == null) {
            System.clearProperty(JAXBContext.JAXB_CONTEXT_FACTORY);
            return;
        }
        System.setProperty(JAXBContext.JAXB_CONTEXT_FACTORY, previousFactoryProperty);
    }

    @Test
    public void instantiatesJaxbContextFactoryProviders() throws Exception {
        System.setProperty(JAXBContext.JAXB_CONTEXT_FACTORY, InterfaceContextFactory.class.getName());

        JAXBContext context = JAXBContext.newInstance(NoProviderBoundType.class);

        assertThat(context).isInstanceOf(StubJaxbContext.class);
        assertThat(((StubJaxbContext) context).getSource()).isEqualTo("interface-context-factory-classes");
    }
}
