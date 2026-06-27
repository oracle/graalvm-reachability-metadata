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
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicInteger;

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
    void reloadsResourceBundleThroughConfiguredResourceBundleControl() {
        ReloadingResourceClassLoader classLoader = new ReloadingResourceClassLoader();
        ResourceBundle.clearCache(classLoader);
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBundleClassLoader(classLoader);
        messageSource.setBasename(ReloadingResourceClassLoader.BASENAME);
        messageSource.setCacheMillis(0);

        String firstMessage = messageSource.getMessage("greeting", null, Locale.ENGLISH);
        String secondMessage = messageSource.getMessage("greeting", null, Locale.ENGLISH);

        assertEquals("Hello through reload path", firstMessage);
        assertEquals("Hello through reload path", secondMessage);
        assertTrue(classLoader.resourceLookups.get() >= 2, "ResourceBundle reload should use ClassLoader#getResource");
    }

    @Test
    void fallsBackToPlainResourceBundleLookupWhenControlLookupFails() {
        ResourceBundleMessageSource messageSource = new ControlFailingMessageSource();
        messageSource.setBasename("org_springframework.spring_context.resource_bundle_plain_messages");

        String message = messageSource.getMessage("greeting", null, Locale.ENGLISH);

        assertEquals("Hello through plain ResourceBundle lookup", message);
    }

    static class ControlFailingMessageSource extends ResourceBundleMessageSource {

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

    private static final class ReloadingResourceClassLoader extends ClassLoader {

        private static final String BASENAME = "org_springframework.spring_context.reloadable_messages";
        private static final String RESOURCE_NAME = "org_springframework/spring_context/reloadable_messages.properties";
        private static final byte[] RESOURCE_BYTES = "greeting=Hello through reload path\n"
                .getBytes(StandardCharsets.ISO_8859_1);

        private final AtomicInteger resourceLookups = new AtomicInteger();

        @Override
        public URL getResource(String name) {
            if (RESOURCE_NAME.equals(name)) {
                resourceLookups.incrementAndGet();
                try {
                    return new URL(null, "memory:/" + name, new ReloadingResourceHandler());
                }
                catch (IOException ex) {
                    throw new IllegalStateException("Could not create in-memory resource URL", ex);
                }
            }
            return super.getResource(name);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (RESOURCE_NAME.equals(name)) {
                return new ByteArrayInputStream(RESOURCE_BYTES);
            }
            return super.getResourceAsStream(name);
        }
    }

    private static final class ReloadingResourceHandler extends URLStreamHandler {

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
                public InputStream getInputStream() {
                    return new ByteArrayInputStream(ReloadingResourceClassLoader.RESOURCE_BYTES);
                }
            };
        }
    }
}
