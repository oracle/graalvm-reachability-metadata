/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_config.smallrye_config_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMappingContext;
import io.smallrye.config.ConfigMappingLoader;

public class ConfigMappingLoaderTest {
    @Test
    void loadsPredefinedConfigMappingImplementationFromClassLoader() {
        ConfigMappingLoader.ConfigMappingImplementation implementation = ConfigMappingLoader.ensureLoaded(PredefinedMapping.class);

        assertThat(implementation.implementation()).isEqualTo(PredefinedMapping$$CMImpl.class);
    }

    @ConfigMapping(prefix = "loader")
    public interface PredefinedMapping {
        String value();
    }

    // CheckStyle: start generated
    public static final class PredefinedMapping$$CMImpl implements PredefinedMapping {
        public PredefinedMapping$$CMImpl(final ConfigMappingContext context) {
        }

        @Override
        public String value() {
            return "predefined";
        }

        public static Map<String, String> getProperties() {
            return Map.of("value", "loader.value");
        }

        public static Set<String> getSecrets() {
            return Set.of();
        }
    }
    // CheckStyle: stop generated
}
