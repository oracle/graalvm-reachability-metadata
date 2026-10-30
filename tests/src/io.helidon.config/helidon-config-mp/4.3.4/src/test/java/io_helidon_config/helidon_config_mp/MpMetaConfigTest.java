/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_config.helidon_config_mp;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MpMetaConfigTest {
    private static final String META_CONFIG_PROPERTY = "io.helidon.config.mp.meta-config";

    @Test
    void loadsClasspathMetaConfigurationAndAppliesItsSourceAndProfile() {
        String previous = System.getProperty(META_CONFIG_PROPERTY);
        ConfigProviderResolver resolver = ConfigProviderResolver.instance();
        Config config = null;
        try {
            System.setProperty(META_CONFIG_PROPERTY, "mp-config-sources/meta-config.properties");

            config = resolver.getConfig();

            assertEquals("base.example", config.getValue("service.host", String.class));
            assertEquals(9090, config.getValue("service.port", Integer.class));
            assertEquals(30, config.getValue("service.timeout", Integer.class));
            assertEquals(true, config.getValue("service.debug", Boolean.class));
            ConfigSource source = config.getConfigSources().iterator().next();
            assertEquals("configured-service", source.getName());
            assertEquals(650, source.getOrdinal());
        } finally {
            if (config != null) {
                resolver.releaseConfig(config);
            }
            if (previous == null) {
                System.clearProperty(META_CONFIG_PROPERTY);
            } else {
                System.setProperty(META_CONFIG_PROPERTY, previous);
            }
        }
    }
}
