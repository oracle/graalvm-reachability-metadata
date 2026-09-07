/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Test;

import org.springframework.context.support.ResourceBundleMessageSource;

public class ResourceBundleMessageSourceInnerMessageSourceControlTest {

    @Test
    void resolvesBundleAfterImmediateCacheExpiry() {
        ClassLoader classLoader = new ReloadableBundleClassLoader(getClass().getClassLoader());
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBundleClassLoader(classLoader);
        messageSource.setBasename(ReloadableBundleClassLoader.BASENAME);
        messageSource.setCacheMillis(0);

        try {
            assertEquals(
                    "Hello through ResourceBundle.Control",
                    messageSource.getMessage("greeting", null, Locale.ROOT));
            assertEquals(
                    "Hello through ResourceBundle.Control",
                    messageSource.getMessage("greeting", null, Locale.ROOT));
        } finally {
            ResourceBundle.clearCache(classLoader);
        }
    }

    private static final class ReloadableBundleClassLoader extends ClassLoader {

        private static final String BASENAME = "org_springframework.spring_context.resource_bundle_control_messages";
        private static final String RESOURCE_NAME =
                "org_springframework/spring_context/resource_bundle_control_messages.properties";

        private ReloadableBundleClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public URL getResource(String name) {
            if (RESOURCE_NAME.equals(name)) {
                try {
                    return new URL(null, "memory:/" + name, new ReloadableBundleUrlStreamHandler(getParent(), name));
                } catch (IOException ex) {
                    throw new IllegalStateException("Could not create the bundle resource URL", ex);
                }
            }
            return super.getResource(name);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (RESOURCE_NAME.equals(name)) {
                return getParent().getResourceAsStream(name);
            }
            return super.getResourceAsStream(name);
        }
    }

    private static final class ReloadableBundleUrlStreamHandler extends URLStreamHandler {

        private final ClassLoader resourceClassLoader;
        private final String resourceName;

        private ReloadableBundleUrlStreamHandler(ClassLoader resourceClassLoader, String resourceName) {
            this.resourceClassLoader = resourceClassLoader;
            this.resourceName = resourceName;
        }

        @Override
        protected URLConnection openConnection(URL url) {
            return new URLConnection(url) {

                @Override
                public void connect() {
                    connected = true;
                }

                @Override
                public long getLastModified() {
                    return System.currentTimeMillis() + 60_000L;
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    InputStream inputStream = resourceClassLoader.getResourceAsStream(resourceName);
                    if (inputStream == null) {
                        throw new IOException("Bundle resource not found: " + resourceName);
                    }
                    return inputStream;
                }
            };
        }
    }
}
