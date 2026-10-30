/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_config.helidon_config_mp;

import java.util.List;

import io.helidon.config.mp.MpConfigSources;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MpConfigSourcesTest {
    private static final String RESOURCE = "mp-config-sources/service.properties";

    @Test
    void loadsClasspathPropertiesUsingTheContextClassLoader() {
        List<ConfigSource> sources = MpConfigSources.classPath(RESOURCE);

        assertEquals(1, sources.size());
        Config config = config(sources);
        assertEquals("base.example", config.getValue("service.host", String.class));
        assertEquals(8080, config.getValue("service.port", Integer.class));
        assertEquals(30, config.getValue("service.timeout", Integer.class));
    }

    @Test
    void profileOverridesBasePropertiesAndKeepsFallbackValues() {
        List<ConfigSource> sources = MpConfigSources.classPath(
                Thread.currentThread().getContextClassLoader(), RESOURCE, "dev");

        assertEquals(1, sources.size());
        Config config = config(sources);
        assertEquals("base.example", config.getValue("service.host", String.class));
        assertEquals(9090, config.getValue("service.port", Integer.class));
        assertEquals(30, config.getValue("service.timeout", Integer.class));
        assertEquals(true, config.getValue("service.debug", Boolean.class));
    }

    @Test
    void usesBasePropertiesWhenTheRequestedProfileHasNoResource() {
        List<ConfigSource> sources = MpConfigSources.classPath(RESOURCE, "production");

        assertEquals(1, sources.size());
        Config config = config(sources);
        assertEquals("base.example", config.getValue("service.host", String.class));
        assertEquals(8080, config.getValue("service.port", Integer.class));
        assertEquals(30, config.getValue("service.timeout", Integer.class));
        assertEquals(false, config.getOptionalValue("service.debug", Boolean.class).isPresent());
    }

    private static Config config(List<ConfigSource> sources) {
        return ConfigProviderResolver.instance().getBuilder()
                .withSources(sources.toArray(ConfigSource[]::new))
                .build();
    }
}
