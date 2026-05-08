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
import org.junit.jupiter.api.Test;

import io.smallrye.config.PropertiesConfigSourceLoader;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

public class AbstractLocationConfigSourceLoaderTest {
    private static final String CONFIG_LOCATION = "fallback-config.properties";
    private static final String PROFILE_LOCATION = "fallback-config-dev.properties";
    private static final String CONFIG_VALUE_NAME = "abstract.location.loader.message";

    @Test
    void fallsBackToClassLoaderResourcesForUnknownProtocolsAndLoadsProfileResources() throws Exception {
        UnknownProtocolClassLoader classLoader = new UnknownProtocolClassLoader(Map.of(
                CONFIG_LOCATION, "abstract.location.loader.message=main\n",
                PROFILE_LOCATION, "abstract.location.loader.message=profile\n"));

        List<ConfigSource> sources = PropertiesConfigSourceLoader.inClassPath(CONFIG_LOCATION, 100, classLoader);
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(sources)
                .withProfile("dev")
                .build();

        assertThat(sources).hasSize(2);
        assertThat(config.getValue(CONFIG_VALUE_NAME, String.class)).isEqualTo("profile");
        assertThat(classLoader.requestedResources()).contains(CONFIG_LOCATION, PROFILE_LOCATION);
    }

    private static final class UnknownProtocolClassLoader extends ClassLoader {
        private final Map<String, String> resources;
        private final List<String> requestedResources = new ArrayList<>();

        private UnknownProtocolClassLoader(final Map<String, String> resources) {
            super(AbstractLocationConfigSourceLoaderTest.class.getClassLoader());
            this.resources = resources;
        }

        @Override
        public Enumeration<URL> getResources(final String name) throws IOException {
            requestedResources.add(name);
            String content = resources.get(name);
            if (content == null) {
                return Collections.emptyEnumeration();
            }
            return Collections.enumeration(List.of(unknownProtocolUrl(name, content)));
        }

        private List<String> requestedResources() {
            return requestedResources;
        }

        private static URL unknownProtocolUrl(final String name, final String content) throws IOException {
            return new URL(null, "memory:/" + name, new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(final URL url) {
                    return new URLConnection(url) {
                        @Override
                        public void connect() {
                            connected = true;
                        }

                        @Override
                        public InputStream getInputStream() {
                            connect();
                            return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
                        }
                    };
                }
            });
        }
    }
}
