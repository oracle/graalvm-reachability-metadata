/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
import jakarta.xml.bind.JAXBContext;
import jakarta_xml_bind.jakarta_xml_bind_api.support.LegacyContextFactory;

public final class UnqualifiedLegacyContextFactory {
    private UnqualifiedLegacyContextFactory() {
    }

    public static JAXBContext createContext(String contextPath, ClassLoader classLoader) {
        return LegacyContextFactory.createContext(contextPath, classLoader);
    }
}
