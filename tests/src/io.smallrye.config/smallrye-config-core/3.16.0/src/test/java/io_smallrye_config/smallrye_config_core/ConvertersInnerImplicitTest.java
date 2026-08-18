/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_config.smallrye_config_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

public class ConvertersInnerImplicitTest {
    @Test
    void propertyCanBeReadUsingImplicitStaticOfConverter() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new PropertiesConfigSource(Map.of(
                        "service.endpoint", "example.com:8443"), "implicit-converter-properties"))
                .build();

        Endpoint endpoint = config.getValue("service.endpoint", Endpoint.class);

        assertThat(endpoint.host()).isEqualTo("example.com");
        assertThat(endpoint.port()).isEqualTo(8443);
    }

    public static final class Endpoint {
        private final String host;
        private final int port;

        private Endpoint(String host, int port) {
            this.host = host;
            this.port = port;
        }

        public static Endpoint of(String value) {
            int separator = value.lastIndexOf(':');
            return new Endpoint(value.substring(0, separator), Integer.parseInt(value.substring(separator + 1)));
        }

        public String host() {
            return host;
        }

        public int port() {
            return port;
        }
    }
}
