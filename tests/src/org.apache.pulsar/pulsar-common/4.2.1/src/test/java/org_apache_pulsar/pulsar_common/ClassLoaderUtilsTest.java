/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_common;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.common.util.ClassLoaderUtils;
import org.junit.jupiter.api.Test;

public class ClassLoaderUtilsTest {
    @Test
    void loadClassFallsBackToProvidedClassLoader() throws Exception {
        FallbackClassLoader classLoader = new FallbackClassLoader(ClassLoaderUtilsTest.class.getClassLoader());

        Class<?> loadedClass = ClassLoaderUtils.loadClass("test.only.VisibleToFallbackLoader", classLoader);

        assertThat(loadedClass).isEqualTo(ClassLoaderUtilsTest.class);
        assertThat(classLoader.wasAskedForFallbackClass()).isTrue();
    }

    private static final class FallbackClassLoader extends ClassLoader {
        private static final String FALLBACK_CLASS_NAME = "test.only.VisibleToFallbackLoader";

        private boolean askedForFallbackClass;

        private FallbackClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (FALLBACK_CLASS_NAME.equals(name)) {
                askedForFallbackClass = true;
                return ClassLoaderUtilsTest.class;
            }
            return super.loadClass(name);
        }

        private boolean wasAskedForFallbackClass() {
            return askedForFallbackClass;
        }
    }
}
