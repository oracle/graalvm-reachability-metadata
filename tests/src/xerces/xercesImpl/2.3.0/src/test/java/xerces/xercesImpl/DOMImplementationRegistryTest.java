/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package xerces.xercesImpl;

import org.apache.xerces.dom3.DOMImplementationRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises DOM implementation source discovery through its public registry API.
 * §FS-repository-functional-spec.5.2
 */
public class DOMImplementationRegistryTest {
    private static final String DOM_IMPLEMENTATION_SOURCE_LIST =
            "org.w3c.dom.DOMImplementationSourceList";
    private static final String XERCES_DOM_IMPLEMENTATION_SOURCE =
            "org.apache.xerces.dom.DOMImplementationSourceImpl";

    @Test
    void discoversConfiguredDomImplementationSource() throws Exception {
        String originalSourceList = System.getProperty(DOM_IMPLEMENTATION_SOURCE_LIST);
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            System.setProperty(DOM_IMPLEMENTATION_SOURCE_LIST, XERCES_DOM_IMPLEMENTATION_SOURCE);
            Thread.currentThread().setContextClassLoader(ClassLoader.getSystemClassLoader());

            DOMImplementationRegistry contextLoaderRegistry = DOMImplementationRegistry.newInstance();

            assertThat(contextLoaderRegistry).isNotNull();

            Thread.currentThread().setContextClassLoader(null);
            DOMImplementationRegistry fallbackRegistry = DOMImplementationRegistry.newInstance();

            assertThat(fallbackRegistry).isNotNull();
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
            if (originalSourceList == null) {
                System.clearProperty(DOM_IMPLEMENTATION_SOURCE_LIST);
            } else {
                System.setProperty(DOM_IMPLEMENTATION_SOURCE_LIST, originalSourceList);
            }
        }
    }
}
