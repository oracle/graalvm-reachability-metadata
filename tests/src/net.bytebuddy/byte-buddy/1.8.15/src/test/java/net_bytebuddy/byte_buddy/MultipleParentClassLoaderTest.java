/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.dynamic.loading.MultipleParentClassLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;

import static org.assertj.core.api.Assertions.assertThat;

public class MultipleParentClassLoaderTest {
    private static final String RESOURCE_NAME = "net_bytebuddy/byte_buddy/"
            + "multiple-parent-class-loader.txt";

    @Test
    void delegatesClassLoadingAndResourceLookupToListedParent() throws Exception {
        ClassLoader parentClassLoader = MultipleParentClassLoaderTest.class.getClassLoader();
        MultipleParentClassLoader classLoader = new MultipleParentClassLoader(
                Collections.singletonList(parentClassLoader));

        Class<?> loadedType = classLoader.loadClass(String.class.getName());
        URL resource = classLoader.getResource(RESOURCE_NAME);
        Enumeration<URL> resources = classLoader.getResources(RESOURCE_NAME);

        assertThat(loadedType).isSameAs(String.class);
        assertThat(resource).isNotNull();
        assertThat(resources.hasMoreElements()).isTrue();
        assertThat(resources.nextElement()).isNotNull();
    }

    @Test
    void fallsBackToExplicitParentForResourceLookup() throws IOException {
        ClassLoader parentClassLoader = MultipleParentClassLoaderTest.class.getClassLoader();
        MultipleParentClassLoader classLoader = new MultipleParentClassLoader(
                parentClassLoader,
                Collections.<ClassLoader>emptyList());

        URL resource = classLoader.getResource(RESOURCE_NAME);
        Enumeration<URL> resources = classLoader.getResources(RESOURCE_NAME);

        assertThat(resource).isNotNull();
        assertThat(resources.hasMoreElements()).isTrue();
        assertThat(resources.nextElement()).isNotNull();
    }
}
