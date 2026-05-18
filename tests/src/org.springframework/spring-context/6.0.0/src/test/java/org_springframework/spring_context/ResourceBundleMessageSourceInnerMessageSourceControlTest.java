/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Test;

import org.springframework.context.support.ResourceBundleMessageSource;

public class ResourceBundleMessageSourceInnerMessageSourceControlTest {

    @Test
    void resolvesMessageWithConfiguredResourceBundleControl() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("org_springframework.spring_context.resource_bundle_control_messages");

        String message = messageSource.getMessage("greeting", null, Locale.ENGLISH);

        assertEquals("Hello through ResourceBundle.Control", message);
    }

    @Test
    void reloadsExpiredBundleThroughResourceUrl() {
        String basename = "org_springframework.spring_context.resource_bundle_reloaded_messages";
        String resourceName = "org_springframework/spring_context/resource_bundle_reloaded_messages.properties";
        ReloadingBundleClassLoader classLoader = new ReloadingBundleClassLoader(
                getClass().getClassLoader(), resourceName, "greeting=Hello after reload\n");
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename(basename);
        messageSource.setBundleClassLoader(classLoader);
        messageSource.setCacheMillis(0);
        messageSource.setFallbackToSystemLocale(false);

        try {
            String firstMessage = messageSource.getMessage("greeting", null, Locale.ROOT);
            String reloadedMessage = messageSource.getMessage("greeting", null, Locale.ROOT);

            assertEquals("Hello after reload", firstMessage);
            assertEquals("Hello after reload", reloadedMessage);
            assertTrue(classLoader.getResourceLookupCount() >= 2);
        }
        finally {
            ResourceBundle.clearCache(classLoader);
        }
    }

    @Test
    void fallsBackToPlainResourceBundleLookupWhenControlLookupFails() {
        ResourceBundleMessageSource messageSource = new ControlFailingMessageSource();
        messageSource.setBasename("org_springframework.spring_context.resource_bundle_plain_messages");

        String message = messageSource.getMessage("greeting", null, Locale.ENGLISH);

        assertEquals("Hello through plain ResourceBundle lookup", message);
    }

    private static final class ReloadingBundleClassLoader extends ClassLoader {

        private final String resourceName;

        private final byte[] properties;

        private int resourceLookupCount;

        ReloadingBundleClassLoader(ClassLoader parent, String resourcePath, String propertyContent) {
            super(parent);
            this.resourceName = resourcePath;
            this.properties = propertyContent.getBytes(StandardCharsets.ISO_8859_1);
        }

        @Override
        public URL getResource(String name) {
            if (this.resourceName.equals(name)) {
                this.resourceLookupCount++;
                return createReloadableUrl(name);
            }
            return super.getResource(name);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (this.resourceName.equals(name)) {
                return new ByteArrayInputStream(this.properties);
            }
            return super.getResourceAsStream(name);
        }

        int getResourceLookupCount() {
            return this.resourceLookupCount;
        }

        private URL createReloadableUrl(String name) {
            try {
                return new URL(null, "memory:/" + name, new URLStreamHandler() {
                    @Override
                    protected URLConnection openConnection(URL url) {
                        return new URLConnection(url) {
                            @Override
                            public void connect() {
                                this.connected = true;
                            }

                            @Override
                            public long getLastModified() {
                                return System.currentTimeMillis() + 60_000L;
                            }

                            @Override
                            public InputStream getInputStream() {
                                return new ByteArrayInputStream(properties);
                            }
                        };
                    }
                });
            }
            catch (MalformedURLException ex) {
                throw new IllegalStateException("Failed to create in-memory bundle URL", ex);
            }
        }
    }

    private static class ControlFailingMessageSource extends ResourceBundleMessageSource {

        @Override
        protected ResourceBundle loadBundle(Reader reader) throws IOException {
            return null;
        }

        @Override
        protected Locale getDefaultLocale() {
            ClassLoader classLoader = getBundleClassLoader();
            if (classLoader != null) {
                ResourceBundle.clearCache(classLoader);
            }
            throw new UnsupportedOperationException("Simulate ResourceBundle.Control being unsupported");
        }
    }
}
