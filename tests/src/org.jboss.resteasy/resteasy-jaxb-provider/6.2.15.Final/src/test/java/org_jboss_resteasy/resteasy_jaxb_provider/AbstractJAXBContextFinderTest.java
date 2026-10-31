/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_jaxb_provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.annotation.Annotation;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;

import org.jboss.resteasy.plugins.providers.jaxb.XmlJAXBContextFinder;
import org.junit.jupiter.api.Test;
import org_jboss_resteasy.resteasy_jaxb_provider.model.Item;
import org_jboss_resteasy.resteasy_jaxb_provider.model.ObjectFactory;

public class AbstractJAXBContextFinderTest {
    @Test
    void createsContextWithTheDiscoveredElementDeclarations() throws Exception {
        XmlJAXBContextFinder finder = new XmlJAXBContextFinder();
        JAXBContext context = finder.createContext(new Annotation[0], Item.class);
        StringWriter output = new StringWriter();

        context.createMarshaller().marshal(new ObjectFactory().createItem(new Item("sextant")), output);
        Object result = context.createUnmarshaller().unmarshal(new StringReader(output.toString()));

        assertThat(result).isInstanceOf(JAXBElement.class);
        JAXBElement<?> element = (JAXBElement<?>) result;
        assertThat(element.getName().getLocalPart()).isEqualTo("item");
        assertThat(element.getValue()).isInstanceOf(Item.class);
        assertThat(((Item) element.getValue()).name).isEqualTo("sextant");
    }
}
