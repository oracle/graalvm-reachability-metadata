/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

import java.util.Map;

import jakarta.xml.bind.JAXBContext;
import jakarta_xml_bind.jakarta_xml_bind_api.support.StubJaxbContext;

public final class DefaultPropertiesContextFactory {
    private DefaultPropertiesContextFactory() {
    }

    public static JAXBContext createContext(String contextPath, ClassLoader classLoader, Map<?, ?> properties) {
        return new StubJaxbContext("properties-context-path-factory");
    }

    public static JAXBContext createContext(Class<?>[] classes, Map<?, ?> properties) {
        return new StubJaxbContext("properties-classes-factory");
    }
}
