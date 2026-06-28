/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_config.smallrye_config_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMappingContext;
import io.smallrye.config.ConfigMappings.ConfigClass;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

public class ConfigMappingLoaderTest {
    @Test
    void mappingUsesPreGeneratedImplementationWhenItIsAlreadyLoadable() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new PropertiesConfigSource(Map.of(
                        "generated.host", "localhost",
                        "generated.port", "9443"), "pre-generated-mapping-properties"))
                .withMapping(PreGeneratedMapping.class)
                .withValidateUnknown(false)
                .build();

        PreGeneratedMapping mapping = config.getConfigMapping(PreGeneratedMapping.class);

        assertThat(mapping.host()).isEqualTo("localhost");
        assertThat(mapping.port()).isEqualTo(9443);
        assertThat(mapping.getClass().getName()).endsWith("PreGeneratedMapping$$CMImpl");
    }

    @Test
    void configClassCanReuseAlreadyLoadableGeneratedMappingInterface() {
        ConfigClass firstRegistration = ConfigClass.configClass(PreGeneratedMapping.class);
        ConfigClass secondRegistration = ConfigClass.configClass(PreGeneratedMapping.class);

        assertThat(firstRegistration.getProperties()).containsKeys("generated.host", "generated.port");
        assertThat(secondRegistration.getProperties()).containsKeys("generated.host", "generated.port");
        assertThat(secondRegistration.getSecrets()).isEmpty();

        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new PropertiesConfigSource(Map.of(
                        "generated.host", "127.0.0.1",
                        "generated.port", "8181"), "config-mapping-class-properties"))
                .withMapping(secondRegistration)
                .withValidateUnknown(false)
                .build();

        PreGeneratedMapping properties = config.getConfigMapping(PreGeneratedMapping.class);

        assertThat(properties.host()).isEqualTo("127.0.0.1");
        assertThat(properties.port()).isEqualTo(8181);
        assertThat(properties.getClass().getName()).endsWith("PreGeneratedMapping$$CMImpl");
    }

    @ConfigMapping(prefix = "generated")
    public interface PreGeneratedMapping {
        String host();

        int port();
    }

    public static final class PreGeneratedMapping$$CMImpl implements PreGeneratedMapping {
        private static final Map<String, String> PROPERTIES;

        static {
            Map<String, String> properties = new HashMap<>();
            properties.put("host", null);
            properties.put("port", null);
            PROPERTIES = Collections.unmodifiableMap(properties);
        }

        private final String host;
        private final int port;

        public PreGeneratedMapping$$CMImpl(final ConfigMappingContext context) {
            this.host = ConfigMappingContext.ObjectCreator.stringValue(context, true, "host");
            this.port = ConfigMappingContext.ObjectCreator.intValue(context, true, "port");
        }

        public static Map<String, String> getProperties() {
            return PROPERTIES;
        }

        public static Set<String> getSecrets() {
            return Set.of();
        }

        @Override
        public String host() {
            return host;
        }

        @Override
        public int port() {
            return port;
        }
    }
}
