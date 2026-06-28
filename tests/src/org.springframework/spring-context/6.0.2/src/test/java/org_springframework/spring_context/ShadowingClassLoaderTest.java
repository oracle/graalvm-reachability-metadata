/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import org.springframework.instrument.classloading.ShadowingClassLoader;

public class ShadowingClassLoaderTest {

    private static final String SPRING_CLASS_RESOURCE =
            "org/springframework/instrument/classloading/ShadowingClassLoader.class";

    @Test
    void delegatesExcludedClassLoadingToEnclosingClassLoader() throws Exception {
        final ClassLoader enclosingClassLoader = ShadowingClassLoaderTest.class.getClassLoader();
        final ShadowingClassLoader classLoader = new ShadowingClassLoader(enclosingClassLoader);

        final Class<?> loadedClass = classLoader.loadClass(String.class.getName());

        assertSame(String.class, loadedClass);
    }

    @Test
    void shadowsEligibleClassUsingBytesFromEnclosingClassLoader() throws Exception {
        final ShadowingClassLoader classLoader = new ShadowingClassLoader(
                ShadowingClassLoaderTest.class.getClassLoader());

        try {
            final Class<?> loadedClass = classLoader.loadClass(ShadowingClassLoaderTest.class.getName());

            assertSame(classLoader, loadedClass.getClassLoader());
            assertNotSame(ShadowingClassLoaderTest.class, loadedClass);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void delegatesSingleResourceLookupToEnclosingClassLoader() {
        final ShadowingClassLoader classLoader = new ShadowingClassLoader(
                ShadowingClassLoaderTest.class.getClassLoader());

        final URL resource = classLoader.getResource(SPRING_CLASS_RESOURCE);

        assertNotNull(resource);
    }

    @Test
    void delegatesResourceStreamLookupToEnclosingClassLoader() throws Exception {
        final ShadowingClassLoader classLoader = new ShadowingClassLoader(
                ShadowingClassLoaderTest.class.getClassLoader());

        try (InputStream resourceStream = classLoader.getResourceAsStream(SPRING_CLASS_RESOURCE)) {
            assertNotNull(resourceStream);
        }
    }

    @Test
    void delegatesResourceEnumerationLookupToEnclosingClassLoader() throws Exception {
        final ShadowingClassLoader classLoader = new ShadowingClassLoader(
                ShadowingClassLoaderTest.class.getClassLoader());

        final Enumeration<URL> resources = classLoader.getResources(SPRING_CLASS_RESOURCE);

        assertTrue(resources.hasMoreElements());
    }
}
