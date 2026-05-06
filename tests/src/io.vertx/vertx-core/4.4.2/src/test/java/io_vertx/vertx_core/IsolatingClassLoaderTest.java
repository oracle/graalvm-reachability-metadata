/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_core;

import io.vertx.core.impl.IsolatingClassLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IsolatingClassLoaderTest {

    private static final URL[] NO_URLS = new URL[0];
    private static final String PARENT_RESOURCE = "isolating-class-loader-parent-resource.txt";

    @Test
    void loadsSystemClassFromParentForIsolatedName() throws ClassNotFoundException, IOException {
        List<String> isolatedClasses = Collections.singletonList(String.class.getName());

        try (IsolatingClassLoader loader = newLoader(isolatedClasses)) {
            Class<?> loadedClass = loader.loadClass(String.class.getName());

            assertSame(String.class, loadedClass);
        }
    }

    @Test
    void fallsBackToParentWhenIsolatedClassIsNotFoundLocally() throws ClassNotFoundException, IOException {
        List<String> isolatedClasses = Collections.singletonList(IsolatingClassLoaderTest.class.getName());

        try (IsolatingClassLoader loader = newLoader(isolatedClasses)) {
            Class<?> loadedClass = loader.loadClass(IsolatingClassLoaderTest.class.getName());
            Class<?> alreadyLoadedClass = loader.loadClass(IsolatingClassLoaderTest.class.getName());

            assertSame(IsolatingClassLoaderTest.class, loadedClass);
            assertSame(loadedClass, alreadyLoadedClass);
        }
    }

    @Test
    void getsParentResourceWhenResourceIsNotAvailableLocally() throws IOException {
        try (IsolatingClassLoader loader = newLoader(Collections.emptyList())) {
            URL resource = loader.getResource(PARENT_RESOURCE);

            assertNotNull(resource);
        }
    }

    @Test
    void getsParentResourcesWhenResourcesAreNotAvailableLocally() throws IOException {
        try (IsolatingClassLoader loader = newLoader(Collections.emptyList())) {
            Enumeration<URL> resources = loader.getResources(PARENT_RESOURCE);

            assertTrue(resources.hasMoreElements());
            assertNotNull(resources.nextElement());
        }
    }

    @Test
    void closeMarksLoaderAsClosed() throws IOException {
        IsolatingClassLoader loader = newLoader(Collections.emptyList());

        assertFalse(loader.isClosed());
        loader.close();

        assertTrue(loader.isClosed());
    }

    private IsolatingClassLoader newLoader(List<String> isolatedClasses) {
        return new IsolatingClassLoader(NO_URLS, IsolatingClassLoaderTest.class.getClassLoader(), isolatedClasses);
    }
}
