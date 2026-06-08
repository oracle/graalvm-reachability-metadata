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
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import org.springframework.context.support.ResourceBundleMessageSource;

public class ResourceBundleMessageSourceInnerMessageSourceControlTest {

    private static final String BASENAME = "org_springframework.spring_context.reloadable_control_messages";
    private static final String RESOURCE_NAME = "org_springframework/spring_context/reloadable_control_messages.properties";

    @Test
    void reloadsExpiredBundleThroughConfiguredClassLoaderResource() {
        RefreshingBundleClassLoader classLoader = new RefreshingBundleClassLoader();
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBundleClassLoader(classLoader);
        messageSource.setBasename(BASENAME);
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setCacheMillis(0);

        try {
            assertEquals("first", messageSource.getMessage("greeting", null, Locale.ROOT));

            awaitNextMillisecond();
            classLoader.update("greeting=second\n", System.currentTimeMillis() + 60_000L);

            assertEquals("second", messageSource.getMessage("greeting", null, Locale.ROOT));
        } finally {
            ResourceBundle.clearCache(classLoader);
        }
    }

    private static void awaitNextMillisecond() {
        long currentTimeMillis = System.currentTimeMillis();
        while (System.currentTimeMillis() == currentTimeMillis) {
            Thread.onSpinWait();
        }
    }

    private static final class RefreshingBundleClassLoader extends ClassLoader {

        private final AtomicReference<String> properties = new AtomicReference<>("greeting=first\n");

        private final AtomicLong lastModified = new AtomicLong(System.currentTimeMillis());

        private RefreshingBundleClassLoader() {
            super(RefreshingBundleClassLoader.class.getClassLoader());
        }

        @Override
        public URL getResource(String name) {
            if (RESOURCE_NAME.equals(name)) {
                try {
                    return new URL(null, "memory:" + name, new MemoryUrlStreamHandler(this.properties, this.lastModified));
                } catch (IOException ex) {
                    throw new IllegalStateException("Unable to create in-memory resource URL", ex);
                }
            }
            return super.getResource(name);
        }

        private void update(String updatedProperties, long updatedLastModified) {
            this.properties.set(updatedProperties);
            this.lastModified.set(updatedLastModified);
        }
    }

    private static final class MemoryUrlStreamHandler extends URLStreamHandler {

        private final AtomicReference<String> properties;

        private final AtomicLong lastModified;

        private MemoryUrlStreamHandler(AtomicReference<String> properties, AtomicLong lastModified) {
            this.properties = properties;
            this.lastModified = lastModified;
        }

        @Override
        protected URLConnection openConnection(URL url) {
            return new MemoryURLConnection(url, this.properties, this.lastModified);
        }
    }

    private static final class MemoryURLConnection extends URLConnection {

        private final AtomicReference<String> properties;

        private final AtomicLong lastModified;

        private MemoryURLConnection(URL url, AtomicReference<String> properties, AtomicLong lastModified) {
            super(url);
            this.properties = properties;
            this.lastModified = lastModified;
        }

        @Override
        public void connect() {
            this.connected = true;
        }

        @Override
        public long getLastModified() {
            return this.lastModified.get();
        }

        @Override
        public InputStream getInputStream() {
            connect();
            byte[] bytes = this.properties.get().getBytes(StandardCharsets.ISO_8859_1);
            return new ByteArrayInputStream(bytes);
        }
    }
}
