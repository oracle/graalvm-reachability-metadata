/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
import java.util.Map;

import jakarta.xml.bind.JAXBContext;
import jakarta_xml_bind.jakarta_xml_bind_api.support.StubJaxbContext;

public class DefaultPackageContextFactory {
    public static JAXBContext createContext(Class<?>[] classesToBeBound, Map<String, ?> properties) {
        return new StubJaxbContext("default-package-classes-factory");
    }

    public static JAXBContext createContext(
            String contextPath,
            ClassLoader classLoader,
            Map<String, ?> properties) {
        return new StubJaxbContext("default-package-context-path-factory");
    }
}
