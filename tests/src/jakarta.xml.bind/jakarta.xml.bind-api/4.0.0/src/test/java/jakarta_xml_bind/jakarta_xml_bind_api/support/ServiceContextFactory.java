/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_xml_bind.jakarta_xml_bind_api.support;

import java.util.Map;

import jakarta.xml.bind.JAXBContext;

public final class ServiceContextFactory {
    private ServiceContextFactory() {
    }

    public static JAXBContext createContext(String contextPath, ClassLoader classLoader) {
        return new StubJaxbContext("service-context-factory-string");
    }

    public static JAXBContext createContext(Class<?>[] classes, Map<?, ?> properties) {
        return new StubJaxbContext("service-context-factory-classes");
    }
}
