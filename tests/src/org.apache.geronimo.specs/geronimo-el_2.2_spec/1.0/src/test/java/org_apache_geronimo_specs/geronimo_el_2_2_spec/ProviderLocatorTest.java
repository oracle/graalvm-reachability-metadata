/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_geronimo_specs.geronimo_el_2_2_spec;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.geronimo.osgi.locator.ProviderLocator;
import org.junit.jupiter.api.Test;

public class ProviderLocatorTest {
    @Test
    void loadClassFallsBackToContextClassLoaderWhenProvidedLoaderCannotFindClass() throws ClassNotFoundException {
        ClassLoader missingClassLoader = new ClassLoader(null) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                throw new ClassNotFoundException(name);
            }
        };

        Class<?> loadedClass = ProviderLocator.loadClass(LocatedProvider.class.getName(),
                ProviderLocatorTest.class, missingClassLoader);

        assertThat(loadedClass).isSameAs(LocatedProvider.class);
        assertThat(LocatedProvider.initialized).isTrue();
    }

    public static final class LocatedProvider {
        static boolean initialized;

        static {
            initialized = true;
        }

        private LocatedProvider() {
        }
    }
}
