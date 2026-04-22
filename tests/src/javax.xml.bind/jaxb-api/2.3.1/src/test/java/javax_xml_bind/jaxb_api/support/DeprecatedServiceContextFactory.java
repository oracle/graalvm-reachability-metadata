/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_xml_bind.jaxb_api.support;

import java.util.Map;

import javax.xml.bind.JAXBContext;

public final class DeprecatedServiceContextFactory {
    private DeprecatedServiceContextFactory() {
    }

    public static JAXBContext createContext(String contextPath, ClassLoader classLoader) {
        return new StubJaxbContext("deprecated-service-context-factory-string");
    }

    public static JAXBContext createContext(Class<?>[] classes, Map<?, ?> properties) {
        return new StubJaxbContext("deprecated-service-context-factory-classes");
    }
}
