/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package xerces.xercesImpl;

import org.apache.xerces.parsers.SAXParser;
import org.apache.xerces.util.ObjectFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises provider loading through Xerces' public object factory API.
 * §FS-repository-functional-spec.5.2
 */
public class ObjectFactoryTest {
    private static final String SAX_PARSER = SAXParser.class.getName();

    @Test
    void createsSaxParserProvidersWithAvailableClassLoaders() throws Exception {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);

            assertThat(ObjectFactory.findClassLoader()).isEqualTo(ObjectFactory.class.getClassLoader());
            assertThat(ObjectFactory.newInstance(SAX_PARSER, null, false)).isInstanceOf(SAXParser.class);
            assertThat(ObjectFactory.newInstance(SAX_PARSER, ClassLoader.getSystemClassLoader(), false))
                    .isInstanceOf(SAXParser.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }
}
