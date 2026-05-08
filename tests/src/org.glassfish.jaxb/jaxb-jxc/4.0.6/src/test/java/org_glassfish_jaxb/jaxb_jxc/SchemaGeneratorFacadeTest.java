/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.jaxb_jxc;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.tools.jxc.SchemaGeneratorFacade;
import java.net.URL;
import java.net.URLClassLoader;
import org.junit.jupiter.api.Test;

public class SchemaGeneratorFacadeTest {
    private static final String CLASSPATH_DISCOVERY_REACHED = "classpath discovery reached";

    @Test
    void mainLoadsSchemaGeneratorAndInvokesItsMainMethod() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader classLoader = new FailingClasspathDiscoveryClassLoader(originalClassLoader)) {
            Thread.currentThread().setContextClassLoader(classLoader);

            assertThatThrownBy(() -> SchemaGeneratorFacade.main(new String[] {"example.Person"}))
                    .isInstanceOf(AssertionError.class)
                    .hasMessage(CLASSPATH_DISCOVERY_REACHED);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static final class FailingClasspathDiscoveryClassLoader extends URLClassLoader {
        private FailingClasspathDiscoveryClassLoader(ClassLoader parent) {
            super(new URL[0], parent);
        }

        @Override
        public URL[] getURLs() {
            throw new AssertionError(CLASSPATH_DISCOVERY_REACHED);
        }
    }
}
