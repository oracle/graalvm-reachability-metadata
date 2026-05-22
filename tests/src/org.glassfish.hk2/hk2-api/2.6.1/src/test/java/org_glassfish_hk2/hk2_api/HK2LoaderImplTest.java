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
        final RecordingClassLoader classLoader = new RecordingClassLoader(String.class);
        final HK2LoaderImpl loader = new HK2LoaderImpl(classLoader);

        final Class<?> loadedClass = loader.loadClass(HK2LoaderImpl.class.getName());

        assertThat(loadedClass).isSameAs(String.class);
        assertThat(classLoader.requestedClassName).isEqualTo(HK2LoaderImpl.class.getName());
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private final Class<?> classToReturn;
        private String requestedClassName;

        private RecordingClassLoader(Class<?> classToReturn) {
            this.classToReturn = classToReturn;
        }

        @Override
        public Class<?> loadClass(String name) {
            requestedClassName = name;
            return classToReturn;
        }
    }
}
