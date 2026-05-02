/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_extras_beanshell.bsh;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import bsh.BshClassManager;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class BshClassManagerTest {
    private static final String FALLBACK_RESOURCE = "/org_apache_extras_beanshell/bsh/bsh-class-manager-resource.txt";
    private static final String EXTERNAL_RESOURCE = "org_apache_extras_beanshell/bsh/external-resource.txt";
    private static final String EXTERNAL_RESOURCE_PATH = "/" + EXTERNAL_RESOURCE;
    private static final String EXTERNAL_RESOURCE_CONTENT = "loaded from the external class loader";

    @Test
    void resolvesClassesWithDefaultClassForNameLookup() throws Exception {
        BshClassManager classManager = new BshClassManager();

        Class<?> resolvedClass = classManager.plainClassForName(BshClassManager.class.getName());

        assertThat(resolvedClass).isSameAs(BshClassManager.class);
    }

    @Test
    void resolvesClassesWithConfiguredClassLoader() throws Exception {
        BshClassManager classManager = new BshClassManager();
        classManager.setClassLoader(new ExternalResourceClassLoader());

        try {
            Class<?> resolvedClass = classManager.plainClassForName(String.class.getName());

            assertThat(resolvedClass).isSameAs(String.class);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void readsResourcesFromDefaultClassResourceLookup() {
        BshClassManager classManager = new BshClassManager();

        URL resource = classManager.getResource(FALLBACK_RESOURCE);

        assertThat(resource).isNotNull();
    }

    @Test
    void readsResourcesFromConfiguredClassLoader() throws Exception {
        BshClassManager classManager = new BshClassManager();
        classManager.setClassLoader(new ExternalResourceClassLoader());

        URL resource = classManager.getResource(EXTERNAL_RESOURCE_PATH);

        assertThat(resource).isEqualTo(ExternalResourceClassLoader.EXTERNAL_URL);
    }

    @Test
    void readsResourceStreamsFromConfiguredClassLoader() throws Exception {
        BshClassManager classManager = new BshClassManager();
        classManager.setClassLoader(new ExternalResourceClassLoader());

        try (InputStream stream = classManager.getResourceAsStream(EXTERNAL_RESOURCE_PATH)) {
            assertThat(stream).isNotNull();
            assertThat(new String(stream.readAllBytes(), UTF_8)).isEqualTo(EXTERNAL_RESOURCE_CONTENT);
        }
    }

    private static final class ExternalResourceClassLoader extends ClassLoader {
        private static final URL EXTERNAL_URL = newExternalUrl();

        private ExternalResourceClassLoader() {
            super(null);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (String.class.getName().equals(name)) {
                return String.class;
            }
            throw new ClassNotFoundException(name);
        }

        @Override
        public URL getResource(String name) {
            if (EXTERNAL_RESOURCE.equals(name)) {
                return EXTERNAL_URL;
            }
            return null;
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (EXTERNAL_RESOURCE.equals(name)) {
                return new ByteArrayInputStream(EXTERNAL_RESOURCE_CONTENT.getBytes(UTF_8));
            }
            return null;
        }

        private static URL newExternalUrl() {
            try {
                return new URL("file:/beanshell-external-resource.txt");
            } catch (MalformedURLException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
