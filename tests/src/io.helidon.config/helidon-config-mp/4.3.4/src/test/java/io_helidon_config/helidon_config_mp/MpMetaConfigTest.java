/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_config.helidon_config_mp;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MpMetaConfigTest {
    private static final String META_CONFIG_PROPERTY = "io.helidon.config.mp.meta-config";
    private static final String MISSING_META_CONFIG = "missing-mp-meta-config-for-test.properties";
    private static final String DEFAULT_SOURCE_PROPERTY = "helidon.mp.meta.default.source.value";

    @Test
    void getConfigFallsBackToDefaultSourcesWhenMetaConfigResourceIsAbsent() {
        ConfigProviderResolver resolver = ConfigProviderResolver.instance();
        ClassLoader classLoader = new IsolatedConfigClassLoader();
        String previousMetaConfig = System.getProperty(META_CONFIG_PROPERTY);
        String previousDefaultSource = System.getProperty(DEFAULT_SOURCE_PROPERTY);
        Config config = null;

        try {
            System.setProperty(META_CONFIG_PROPERTY, MISSING_META_CONFIG);
            System.setProperty(DEFAULT_SOURCE_PROPERTY, "from-system-properties");

            config = resolver.getConfig(classLoader);

            assertEquals("from-system-properties", config.getValue(DEFAULT_SOURCE_PROPERTY, String.class));
        } finally {
            if (config != null) {
                resolver.releaseConfig(config);
            }
            restoreProperty(META_CONFIG_PROPERTY, previousMetaConfig);
            restoreProperty(DEFAULT_SOURCE_PROPERTY, previousDefaultSource);
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private static final class IsolatedConfigClassLoader extends ClassLoader {
        private IsolatedConfigClassLoader() {
            super(MpMetaConfigTest.class.getClassLoader());
        }
    }
}
