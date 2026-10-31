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
import jakarta.xml.bind.Unmarshaller;

import org.jboss.resteasy.plugins.providers.jaxb.SecureUnmarshaller;
import org.jboss.resteasy.plugins.providers.jaxb.XmlJAXBContextFinder;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import org_jboss_resteasy.resteasy_jaxb_provider.model.Item;

public class AbstractJAXBContextFinderTest {
    @Test
    void createsContextWithTheDiscoveredElementDeclarations() throws Exception {
        XmlJAXBContextFinder finder = new XmlJAXBContextFinder();
        JAXBContext context = finder.createContext(new Annotation[0], Item.class);
        Unmarshaller unmarshaller = new SecureUnmarshaller(context.createUnmarshaller(), true, true, true);
        Object original = unmarshaller.unmarshal(new InputSource(new StringReader("""
                <inv:item xmlns:inv="urn:inventory"><name>sextant</name></inv:item>
                """)));
        StringWriter output = new StringWriter();

        context.createMarshaller().marshal(original, output);
        Object result = unmarshaller.unmarshal(new InputSource(new StringReader(output.toString())));

        assertThat(result).isInstanceOf(JAXBElement.class);
        JAXBElement<?> element = (JAXBElement<?>) result;
        assertThat(element.getName().getLocalPart()).isEqualTo("item");
        assertThat(element.getValue()).isInstanceOf(Item.class);
        assertThat(((Item) element.getValue()).name).isEqualTo("sextant");
    }
}
