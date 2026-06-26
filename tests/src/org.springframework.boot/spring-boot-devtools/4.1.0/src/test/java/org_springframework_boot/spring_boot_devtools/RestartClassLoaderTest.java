/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_devtools;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.net.URL;
import java.util.Enumeration;

import org.junit.jupiter.api.Test;

import org.springframework.boot.devtools.restart.classloader.RestartClassLoader;

public class RestartClassLoaderTest {

    @Test
    void loadClassFallsBackToParentClassLoader() throws Exception {
        final RestartClassLoader classLoader = new RestartClassLoader(getClass().getClassLoader(), new URL[0]);

        final Class<?> loadedClass = classLoader.loadClass(String.class.getName());

        assertSame(String.class, loadedClass);
    }

    @Test
    void getResourceDelegatesMissingResourceToParentClassLoader() {
        final RestartClassLoader classLoader = new RestartClassLoader(getClass().getClassLoader(), new URL[0]);

        final URL resource = classLoader.getResource("restart-class-loader/missing-resource.txt");

        assertNull(resource);
    }

    @Test
    void getResourcesDelegatesMissingResourceEnumerationToParentClassLoader() throws Exception {
        final RestartClassLoader classLoader = new RestartClassLoader(getClass().getClassLoader(), new URL[0]);

        final Enumeration<URL> resources = classLoader.getResources("restart-class-loader/missing-resources.txt");

        assertFalse(resources.hasMoreElements());
    }
}
