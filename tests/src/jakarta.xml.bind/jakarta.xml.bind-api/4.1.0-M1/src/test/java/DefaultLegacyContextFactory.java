/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
import jakarta.xml.bind.JAXBContext;
import jakarta_xml_bind.jakarta_xml_bind_api.support.StubJaxbContext;

public final class DefaultLegacyContextFactory {
    private DefaultLegacyContextFactory() {
    }

    public static JAXBContext createContext(String contextPath, ClassLoader classLoader) {
        return new StubJaxbContext("legacy-context-path-factory");
    }
}
