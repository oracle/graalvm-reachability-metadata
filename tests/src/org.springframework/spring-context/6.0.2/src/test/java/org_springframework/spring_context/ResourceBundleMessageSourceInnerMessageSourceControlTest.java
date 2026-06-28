/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void fallsBackToPlainResourceBundleLookupWhenControlLookupFails() {
        ResourceBundleMessageSource messageSource = new ControlFailingMessageSource();
        messageSource.setBasename("org_springframework.spring_context.resource_bundle_plain_messages");

        String message = messageSource.getMessage("greeting", null, Locale.ENGLISH);

        assertEquals("Hello through plain ResourceBundle lookup", message);
    }

    @Test
    void reloadsBundleThroughResourceUrlWhenNativeCacheExpires() throws InterruptedException {
        String basename = "org_springframework.spring_context.reloadable_messages";
        String resourceName = "org_springframework/spring_context/reloadable_messages.properties";
        ReloadableBundleClassLoader classLoader = new ReloadableBundleClassLoader(resourceName,
                "greeting=Hello before reload\n", "greeting=Hello after reload\n");
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename(basename);
        messageSource.setBundleClassLoader(classLoader);
        messageSource.setCacheMillis(0);

        String firstMessage = messageSource.getMessage("greeting", null, Locale.ENGLISH);
        classLoader.useReloadContent();
        ResourceBundle.clearCache(classLoader);
        String reloadedMessage = awaitReloadedMessage(messageSource, firstMessage);

        assertEquals("Hello before reload", firstMessage);
        assertEquals("Hello after reload", reloadedMessage);
    }

    private static String awaitReloadedMessage(ResourceBundleMessageSource messageSource, String previousMessage)
            throws InterruptedException {
        String currentMessage = previousMessage;
        for (int attempt = 0; attempt < 20; attempt++) {
            currentMessage = messageSource.getMessage("greeting", null, Locale.ENGLISH);
            if (!previousMessage.equals(currentMessage)) {
                return currentMessage;
            }
            Thread.sleep(25L);
        }
        return currentMessage;
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

    static class ReloadableBundleClassLoader extends ClassLoader {

        private final String resourceName;

        private final byte[] initialContent;

        private final byte[] reloadContent;

        private boolean useReloadContent;

        private long lastModified = System.currentTimeMillis();

        ReloadableBundleClassLoader(String resourceName, String initialContent, String reloadContent) {
            super(ResourceBundleMessageSourceInnerMessageSourceControlTest.class.getClassLoader());
            this.resourceName = resourceName;
            this.initialContent = initialContent.getBytes(StandardCharsets.ISO_8859_1);
            this.reloadContent = reloadContent.getBytes(StandardCharsets.ISO_8859_1);
        }

        void useReloadContent() {
            this.useReloadContent = true;
            this.lastModified = Math.max(System.currentTimeMillis(), this.lastModified + 1L);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (!this.resourceName.equals(name)) {
                return super.getResourceAsStream(name);
            }
            return new ByteArrayInputStream(currentContent());
        }

        @Override
        public URL getResource(String name) {
            if (!this.resourceName.equals(name)) {
                return super.getResource(name);
            }
            try {
                return new URL(null, "memory:/" + name, new ReloadableResourceHandler(this));
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to create in-memory bundle URL", ex);
            }
        }

        byte[] currentContent() {
            return (this.useReloadContent ? this.reloadContent : this.initialContent);
        }
    }

    static class ReloadableResourceHandler extends URLStreamHandler {

        private final ReloadableBundleClassLoader classLoader;

        ReloadableResourceHandler(ReloadableBundleClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        @Override
        protected URLConnection openConnection(URL url) {
            return new ReloadableResourceConnection(url, this.classLoader);
        }
    }

    static class ReloadableResourceConnection extends URLConnection {

        private final ReloadableBundleClassLoader classLoader;

        ReloadableResourceConnection(URL url, ReloadableBundleClassLoader classLoader) {
            super(url);
            this.classLoader = classLoader;
        }

        @Override
        public void connect() {
            this.connected = true;
        }

        @Override
        public long getLastModified() {
            return this.classLoader.lastModified;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(this.classLoader.currentContent());
        }
    }
}
