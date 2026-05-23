/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.hsqldb.lib.RefCapablePropertyResourceBundle;
import org.junit.jupiter.api.Test;

public class RefCapablePropertyResourceBundleAnonymous1Test {
    private static final String BUNDLE_NAME = "memory.refcapable";

    @Test
    void getStringLoadsEmptyPropertyValueFromReferencedTextResource() {
        InMemoryResourceClassLoader classLoader = new InMemoryResourceClassLoader();
        RefCapablePropertyResourceBundle bundle =
                RefCapablePropertyResourceBundle.getBundle(BUNDLE_NAME, Locale.ROOT, classLoader);

        String value = bundle.getString("help");

        assertThat(value).isEqualTo("loaded from referenced text resource");
        assertThat(classLoader.requestedResources()).containsKey("memory/refcapable/help.text");
    }

    private static final class InMemoryResourceClassLoader extends ClassLoader {
        private final Map<String, String> resources = new HashMap<>();
        private final Map<String, Integer> requestedResources = new HashMap<>();

        private InMemoryResourceClassLoader() {
            super(RefCapablePropertyResourceBundleAnonymous1Test.class.getClassLoader());
            resources.put("memory/refcapable.properties", "help=\n");
            resources.put("memory/refcapable/help.text", "loaded from referenced text resource\n");
        }

        @Override
        public URL getResource(String name) {
            String resource = resources.get(name);

            if (resource != null) {
                return inMemoryUrl(name, resource);
            }

            return super.getResource(name);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            requestedResources.merge(name, 1, Integer::sum);
            String resource = resources.get(name);

            if (resource != null) {
                return inputStream(resource);
            }

            return super.getResourceAsStream(name);
        }

        private URL inMemoryUrl(String name, String resource) {
            try {
                return new URL(null, "memory:///" + name, new InMemoryUrlStreamHandler(resource));
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        private InputStream inputStream(String resource) {
            byte[] bytes = resource.getBytes(StandardCharsets.ISO_8859_1);
            return new ByteArrayInputStream(bytes);
        }

        private Map<String, Integer> requestedResources() {
            return requestedResources;
        }
    }

    private static final class InMemoryUrlStreamHandler extends URLStreamHandler {
        private final String resource;

        private InMemoryUrlStreamHandler(String resource) {
            this.resource = resource;
        }

        @Override
        protected URLConnection openConnection(URL url) {
            return new InMemoryURLConnection(url, resource);
        }
    }

    private static final class InMemoryURLConnection extends URLConnection {
        private final String resource;

        private InMemoryURLConnection(URL url, String resource) {
            super(url);
            this.resource = resource;
        }

        @Override
        public void connect() {
        }

        @Override
        public InputStream getInputStream() {
            byte[] bytes = resource.getBytes(StandardCharsets.ISO_8859_1);
            return new ByteArrayInputStream(bytes);
        }
    }
}
