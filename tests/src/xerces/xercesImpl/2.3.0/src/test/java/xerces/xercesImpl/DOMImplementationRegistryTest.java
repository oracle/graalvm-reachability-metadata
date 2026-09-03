/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package xerces.xercesImpl;

import org.apache.xerces.dom3.DOMImplementationRegistry;
import org.apache.xerces.dom3.DOMImplementationSource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.w3c.dom.DOMImplementation;

public class DOMImplementationRegistryTest implements DOMImplementationSource {
    private static final String SOURCE_CLASS = DOMImplementationRegistryTest.class.getName();

    @Test
    void loadsImplementationSourceFromContextClassLoader() throws Exception {
        String originalSources = System.getProperty(DOMImplementationRegistry.PROPERTY);
        try {
            System.setProperty(DOMImplementationRegistry.PROPERTY, SOURCE_CLASS);
            DOMImplementationRegistry registry = DOMImplementationRegistry.newInstance();

            DOMImplementation implementation = registry.getDOMImplementation("Core 2.0");

            Assertions.assertThat(implementation).isNull();
        } finally {
            restoreSources(originalSources);
        }
    }

    @Test
    void loadsImplementationSourceWithoutContextClassLoader() throws Exception {
        String originalSources = System.getProperty(DOMImplementationRegistry.PROPERTY);
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            System.setProperty(DOMImplementationRegistry.PROPERTY, SOURCE_CLASS);
            Thread.currentThread().setContextClassLoader(null);
            DOMImplementationRegistry registry = DOMImplementationRegistry.newInstance();

            Assertions.assertThat(registry.getDOMImplementation("Core 2.0")).isNull();
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
            restoreSources(originalSources);
        }
    }

    @Override
    public DOMImplementation getDOMImplementation(String features) {
        return null;
    }

    private void restoreSources(String originalSources) {
        if (originalSources == null) {
            System.clearProperty(DOMImplementationRegistry.PROPERTY);
        } else {
            System.setProperty(DOMImplementationRegistry.PROPERTY, originalSources);
        }
    }
}
