/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_xml_bind.jaxb_api.support;

import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBContextFactory;
import javax.xml.bind.JAXBException;

public final class InterfaceContextFactory implements JAXBContextFactory {
    @Override
    public JAXBContext createContext(Class<?>[] classesToBeBound, Map<String, ?> properties) throws JAXBException {
        return new StubJaxbContext("interface-context-factory-classes");
    }

    @Override
    public JAXBContext createContext(String contextPath, ClassLoader classLoader, Map<String, ?> properties)
            throws JAXBException {
        return new StubJaxbContext("interface-context-factory-string");
    }
}
