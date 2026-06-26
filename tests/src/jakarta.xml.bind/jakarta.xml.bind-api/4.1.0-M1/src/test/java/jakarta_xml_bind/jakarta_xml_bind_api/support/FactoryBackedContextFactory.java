/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_xml_bind.jakarta_xml_bind_api.support;

import java.util.Map;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBContextFactory;
import jakarta.xml.bind.JAXBException;

public class FactoryBackedContextFactory implements JAXBContextFactory {
    @Override
    public JAXBContext createContext(Class<?>[] classesToBeBound, Map<String, ?> properties) throws JAXBException {
        return new StubJaxbContext("factory-backed-classes-factory");
    }

    @Override
    public JAXBContext createContext(String contextPath, ClassLoader classLoader, Map<String, ?> properties)
            throws JAXBException {
        return new StubJaxbContext("factory-backed-context-path-factory");
    }
}
