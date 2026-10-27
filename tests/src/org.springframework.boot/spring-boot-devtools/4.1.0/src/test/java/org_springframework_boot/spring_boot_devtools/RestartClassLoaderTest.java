/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_devtools;

import java.net.URL;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.boot.devtools.restart.classloader.RestartClassLoader;

import static org.assertj.core.api.Assertions.assertThat;

public class RestartClassLoaderTest {

    private static final String DEVTOOLS_PROPERTIES = "META-INF/spring-devtools.properties";

    @Test
    void delegatesClassAndResourceLookupsToTheParentClassLoader() throws Exception {
        ClassLoader parent = RestartClassLoader.class.getClassLoader();

        try (RestartClassLoader classLoader = new RestartClassLoader(parent, new URL[0])) {
            URL resource = classLoader.getResource(DEVTOOLS_PROPERTIES);
            List<URL> resources = Collections.list(classLoader.getResources(DEVTOOLS_PROPERTIES));
            Class<?> loadedClass = classLoader.loadClass(String.class.getName());

            assertThat(resource).isNotNull();
            assertThat(resources).contains(resource);
            assertThat(loadedClass).isSameAs(String.class);
        }
    }
}
