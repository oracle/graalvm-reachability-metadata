/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2.hk2_api;

import static org.assertj.core.api.Assertions.assertThat;

import org.glassfish.hk2.utilities.HK2LoaderImpl;
import org.junit.jupiter.api.Test;

public class HK2LoaderImplTest {
    @Test
    void loadClassUsesConfiguredClassLoader() {
        final ClassLoader classLoader = HK2LoaderImplTest.class.getClassLoader();
        final HK2LoaderImpl loader = new HK2LoaderImpl(classLoader);

        final Class<?> loadedClass = loader.loadClass(HK2LoaderImpl.class.getName());

        assertThat(loadedClass).isSameAs(HK2LoaderImpl.class);
    }
}
