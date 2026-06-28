/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_config.smallrye_config_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.eclipse.microprofile.config.spi.Converter;
import org.junit.jupiter.api.Test;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.WithConverter;

public class ConfigMappingContextTest {
    @Test
    void instantiatesMappingConverterFromPublicNoArgumentConstructor() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new PropertiesConfigSource(Map.of(
                        "service.endpoint", "smallrye.io:443"), "test-properties"))
                .withMapping(ServiceMapping.class)
                .withValidateUnknown(false)
                .build();

        ServiceMapping mapping = config.getConfigMapping(ServiceMapping.class);

        assertThat(mapping.endpoint()).isEqualTo(new Endpoint("smallrye.io", 443));
    }

    @ConfigMapping(prefix = "service")
    public interface ServiceMapping {
        @WithConverter(EndpointConverter.class)
        Endpoint endpoint();
    }

    public record Endpoint(String host, int port) {
    }

    public static final class EndpointConverter implements Converter<Endpoint> {
        @Override
        public Endpoint convert(final String value) {
            String[] parts = value.split(":", 2);
            return new Endpoint(parts[0], Integer.parseInt(parts[1]));
        }
    }
}
