/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_config.smallrye_config_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperties;
import org.junit.jupiter.api.Test;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.WithName;

public class ConfigMappingGeneratorTest {
    @Test
    void mapsConfigPropertiesClassUsingGeneratedMappingInterface() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new PropertiesConfigSource(Map.of(
                        "server.http-port", "9090",
                        "server.mode", "prod"),
                        "test-properties"))
                .withMapping(ServerProperties.class)
                .build();

        ServerProperties properties = config.getConfigMapping(ServerProperties.class);

        assertThat(properties.host).isEqualTo("localhost");
        assertThat(properties.port).isEqualTo(9090);
        assertThat(properties.mode).isEqualTo("prod");
        assertThat(properties.enabled).isTrue();
    }

    @ConfigProperties(prefix = "server")
    public static class ServerProperties {
        private String host = "localhost";

        @WithName("http-port")
        private int port = 8080;

        private String mode;

        private boolean enabled = true;
    }
}
