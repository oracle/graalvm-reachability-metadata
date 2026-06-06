/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_config.helidon_config_mp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.helidon.config.mp.MpConfigSources;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MpConfigSourcesTest {
    private static final String CONFIG_RESOURCE = "mp-config-sources.properties";
    private static final String PROFILE_CONFIG_RESOURCE = "mp-config-sources-dev.properties";

    @Test
    void classPathCreatesConfigSourcesFromMatchingResources() {
        URL configUrl = propertiesUrl("/config/mp-config-sources.properties", "plain.value=default\n");
        ClassLoader classLoader = new ResourceClassLoader(Map.of(CONFIG_RESOURCE, List.of(configUrl)));

        List<ConfigSource> sources = MpConfigSources.classPath(classLoader, CONFIG_RESOURCE);

        assertThat(sources).hasSize(1);
        assertThat(sources.get(0).getValue("plain.value")).isEqualTo("default");
    }

    @Test
    void classPathCombinesProfileResourceWithMatchingBaseResource() {
        ClassLoader classLoader = new ResourceClassLoader(Map.of(
                CONFIG_RESOURCE, List.of(propertiesUrl("/config/mp-config-sources.properties", "profile.value=base\n"
                        + "fallback.value=base-only\n")),
                PROFILE_CONFIG_RESOURCE, List.of(propertiesUrl("/config/mp-config-sources-dev.properties",
                        "profile.value=profile\n"))));

        List<ConfigSource> sources = MpConfigSources.classPath(classLoader, CONFIG_RESOURCE, "dev");

        assertThat(sources).hasSize(1);
        ConfigSource source = sources.get(0);
        assertThat(source.getValue("profile.value")).isEqualTo("profile");
        assertThat(source.getValue("fallback.value")).isEqualTo("base-only");
        assertThat(source.getPropertyNames()).containsExactlyInAnyOrder("profile.value", "fallback.value");
    }

    private static URL propertiesUrl(String path, String content) {
        try {
            return new URL(null, "memory:" + path, new PropertiesUrlStreamHandler(content));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create in-memory URL for " + path, e);
        }
    }

    private static final class ResourceClassLoader extends ClassLoader {
        private final Map<String, List<URL>> resources;

        private ResourceClassLoader(Map<String, List<URL>> resources) {
            super(MpConfigSourcesTest.class.getClassLoader());
            this.resources = new LinkedHashMap<>(resources);
        }

        @Override
        public Enumeration<URL> getResources(String name) {
            return Collections.enumeration(resources.getOrDefault(name, List.of()));
        }
    }

    private static final class PropertiesUrlStreamHandler extends URLStreamHandler {
        private final byte[] content;

        private PropertiesUrlStreamHandler(String content) {
            this.content = content.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        protected URLConnection openConnection(URL url) {
            return new URLConnection(url) {
                @Override
                public void connect() {
                    connected = true;
                }

                @Override
                public InputStream getInputStream() {
                    return new ByteArrayInputStream(content);
                }
            };
        }
    }
}
