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
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.WithName;

public class ConfigMappingGeneratorTest {
    @Test
    void mapsConfigPropertiesClassFieldsAndInitializerDefaults() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new PropertiesConfigSource(Map.of(
                        "server.endpoint.host", "smallrye.io",
                        "server.tls", "true"), "test-properties"))
                .withMapping(ServerProperties.class)
                .withValidateUnknown(false)
                .build();

        ServerProperties server = config.getConfigMapping(ServerProperties.class);

        assertThat(server.host).isEqualTo("smallrye.io");
        assertThat(server.port).isEqualTo(8080);
        assertThat(server.tls).isTrue();
        assertThat(server.requestTimeoutSeconds).isEqualTo(30L);
    }

    @ConfigProperties(prefix = "server")
    public static class ServerProperties {
        @ConfigProperty(name = "endpoint.host")
        public String host;

        public int port = 8080;

        public boolean tls;

        @WithName("request-timeout-seconds")
        public long requestTimeoutSeconds = 30L;
    }
}
