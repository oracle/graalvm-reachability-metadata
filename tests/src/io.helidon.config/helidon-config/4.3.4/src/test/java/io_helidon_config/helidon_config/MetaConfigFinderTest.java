/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_config.helidon_config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.config.MetaConfig;

import org.junit.jupiter.api.Test;

public class MetaConfigFinderTest {
    private static final String META_CONFIG_PROPERTY = "io.helidon.config.meta-config";
    private static final String META_CONFIG_RESOURCE = "io_helidon_config/helidon_config/meta-config-finder.properties";

    @Test
    void metaConfigSystemPropertyFindsClasspathResource() {
        final String previousMetaConfig = System.getProperty(META_CONFIG_PROPERTY);
        System.setProperty(META_CONFIG_PROPERTY, META_CONFIG_RESOURCE);
        try {
            final Optional<Config> metaConfig = MetaConfig.metaConfig();

            assertThat(metaConfig).isPresent();
            assertThat(metaConfig.orElseThrow().get("app.message").asString().get())
                    .isEqualTo("loaded from classpath meta config");
        } finally {
            if (previousMetaConfig == null) {
                System.clearProperty(META_CONFIG_PROPERTY);
            } else {
                System.setProperty(META_CONFIG_PROPERTY, previousMetaConfig);
            }
        }
    }
}
