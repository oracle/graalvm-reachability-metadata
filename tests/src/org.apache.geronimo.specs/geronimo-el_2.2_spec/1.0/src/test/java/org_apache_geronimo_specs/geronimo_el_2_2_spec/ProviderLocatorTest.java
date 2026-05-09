/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_geronimo_specs.geronimo_el_2_2_spec;

import org.apache.geronimo.osgi.locator.ProviderLocator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProviderLocatorTest {

    @Test
    void loadClassFallsBackToClassForNameWhenClassLoaderIsUnavailable() throws ClassNotFoundException {
        Class<?> loadedClass = ProviderLocator.loadClass(String.class.getName(), ProviderLocatorTest.class, null);

        assertThat(loadedClass).isSameAs(String.class);
    }
}
