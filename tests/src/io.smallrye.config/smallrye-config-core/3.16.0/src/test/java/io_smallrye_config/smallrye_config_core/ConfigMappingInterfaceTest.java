/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_config.smallrye_config_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.spi.Converter;
import org.junit.jupiter.api.Test;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMappingInterface;
import io.smallrye.config.WithConverter;

public class ConfigMappingInterfaceTest {
    @Test
    void describesMappingPropertiesThatUseDefaultImplementationsConvertersAndGenericArrays() {
        ConfigMappingInterface mapping = ConfigMappingInterface.getConfigurationInterface(CompleteMapping.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.hasConfigMapping()).isTrue();
        assertThat(mapping.getSuperTypes())
                .extracting(ConfigMappingInterface::getInterfaceType)
                .containsExactly(BaseMapping.class);

        Map<String, ConfigMappingInterface.Property> properties = Arrays.stream(mapping.getProperties())
                .collect(Collectors.toMap(ConfigMappingInterface.Property::getMemberName, Function.identity()));
        assertThat(properties).containsOnlyKeys("endpoint", "fallback", "inherited", "matrix");

        ConfigMappingInterface.Property fallback = properties.get("fallback");
        assertThat(fallback.isDefaultMethod()).isTrue();
        assertThat(fallback.asDefaultMethod().getDefaultMethod().getDeclaringClass())
                .isEqualTo(CompleteMapping.DefaultImpls.class);
        assertThat(fallback.asDefaultMethod().getDefaultProperty().isLeaf()).isTrue();

        ConfigMappingInterface.Property endpoint = properties.get("endpoint");
        assertThat(endpoint.isLeaf()).isTrue();
        assertThat(endpoint.asLeaf().getValueRawType()).isEqualTo(Object.class);
        assertThat(endpoint.asLeaf().getConvertWith()).isEqualTo(HostAndPortObjectConverter.class);

        ConfigMappingInterface.Property matrix = properties.get("matrix");
        assertThat(matrix.isLeaf()).isTrue();
        assertThat(matrix.asLeaf().getValueRawType()).isEqualTo(List[].class);
    }

    @ConfigMapping(prefix = "base")
    public interface BaseMapping {
        String inherited();
    }

    @ConfigMapping(prefix = "complete")
    public interface CompleteMapping extends BaseMapping {
        @WithConverter(HostAndPortObjectConverter.class)
        Object endpoint();

        String fallback();

        List<String>[] matrix();

        class DefaultImpls {
            public static String fallback(final CompleteMapping mapping) {
                return "generated fallback";
            }
        }
    }

    public record HostAndPort(String host, int port) {
    }

    public static final class HostAndPortObjectConverter implements Converter<Object> {
        @Override
        public Object convert(final String value) {
            String[] parts = value.split(":", 2);
            return new HostAndPort(parts[0], Integer.parseInt(parts[1]));
        }
    }
}
