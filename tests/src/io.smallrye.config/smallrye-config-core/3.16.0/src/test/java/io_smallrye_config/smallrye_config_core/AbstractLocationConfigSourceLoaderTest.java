/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_config.smallrye_config_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.ConfigSourceProvider;
import org.junit.jupiter.api.Test;

import io.smallrye.config.AbstractLocationConfigSourceLoader;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

public class AbstractLocationConfigSourceLoaderTest {
    @Test
    void loadsMainAndProfileClasspathResourcesThroughUnknownProtocolFallback() {
        InMemoryUnknownProtocolClassLoader classLoader = new InMemoryUnknownProtocolClassLoader(Map.of(
                "unknown-config.properties", """
                        unknown.protocol.value=from-main
                        main.only=available
                        """,
                "unknown-config-dev.properties", """
                        unknown.protocol.value=from-profile
                        """));

        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .forClassLoader(classLoader)
                .withSources(new UnknownProtocolPropertiesSourceProvider("unknown-config.properties", 200))
                .withProfile("dev")
                .build();

        assertThat(config.getValue("unknown.protocol.value", String.class)).isEqualTo("from-profile");
        assertThat(config.getValue("main.only", String.class)).isEqualTo("available");
        assertThat(Collections.frequency(classLoader.requestedResourceNames(), "unknown-config.properties"))
                .isGreaterThanOrEqualTo(2);
        assertThat(classLoader.requestedResourceNames()).contains("unknown-config-dev.properties");
    }

    private static final class UnknownProtocolPropertiesSourceProvider extends AbstractLocationConfigSourceLoader
            implements ConfigSourceProvider {
        private final String location;
        private final int ordinal;

        private UnknownProtocolPropertiesSourceProvider(final String location, final int ordinal) {
            this.location = location;
            this.ordinal = ordinal;
        }

        @Override
        public List<ConfigSource> getConfigSources(final ClassLoader classLoader) {
            return loadConfigSources(location, ordinal, classLoader);
        }

        @Override
        protected String[] getFileExtensions() {
            return new String[] {"properties" };
        }

        @Override
        protected ConfigSource loadConfigSource(final URL url, final int ordinal) throws IOException {
            return new PropertiesConfigSource(url, ordinal);
        }
    }

    private static final class InMemoryUnknownProtocolClassLoader extends ClassLoader {
        private final Map<String, String> resources;
        private final List<String> requestedResourceNames = new ArrayList<>();

        private InMemoryUnknownProtocolClassLoader(final Map<String, String> resources) {
            super(null);
            this.resources = resources;
        }

        @Override
        public Enumeration<URL> getResources(final String name) throws IOException {
            requestedResourceNames.add(name);
            String content = resources.get(name);
            if (content == null) {
                return Collections.emptyEnumeration();
            }

            return Collections.enumeration(List.of(new URL(null, "memory:" + name,
                    new InMemoryUrlStreamHandler(content))));
        }

        private List<String> requestedResourceNames() {
            return requestedResourceNames;
        }
    }

    private static final class InMemoryUrlStreamHandler extends URLStreamHandler {
        private final String content;

        private InMemoryUrlStreamHandler(final String content) {
            this.content = content;
        }

        @Override
        protected URLConnection openConnection(final URL url) {
            return new URLConnection(url) {
                @Override
                public void connect() {
                    connected = true;
                }

                @Override
                public InputStream getInputStream() {
                    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
                }
            };
        }
    }
}
