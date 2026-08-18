/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_xml_bind.jakarta_xml_bind_api.support;

import jakarta.xml.bind.JAXBContext;

public final class LegacyContextFactory {
    private LegacyContextFactory() {
    }

    public static JAXBContext createContext(String contextPath, ClassLoader classLoader) {
        return new StubJaxbContext("legacy-context-path-factory");
    }
}
