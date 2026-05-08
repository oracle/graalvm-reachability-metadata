/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_config.smallrye_config_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.config.spi.Converter;
import org.junit.jupiter.api.Test;

import io.smallrye.config.ConfigMappingInterface;
import io.smallrye.config.WithConverter;

public class ConfigMappingInterfaceTest {
    @Test
    void createsMetadataForMappingsWithSuperTypesConvertersAndGenericArrays() {
        ConfigMappingInterface mapping = ConfigMappingInterface.getConfigurationInterface(ApplicationMapping.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.getInterfaceType()).isEqualTo(ApplicationMapping.class);
        assertThat(mapping.getSuperTypes())
                .extracting(ConfigMappingInterface::getInterfaceType)
                .containsExactly(BaseMapping.class);
        assertThat(mapping.getProperties())
                .extracting(ConfigMappingInterface.Property::getMemberName)
                .contains("baseName", "featureName", "rolesByName", "tags");
        assertThat(mapping.getProperties())
                .filteredOn(ConfigMappingInterface.Property::isDefaultMethod)
                .singleElement()
                .satisfies(property -> assertThat(property.asDefaultMethod().getDefaultProperty().isCollection())
                        .isTrue());
        assertThat(mapping.getProperties())
                .filteredOn(property -> "featureName".equals(property.getMemberName()))
                .singleElement()
                .satisfies(property -> assertThat(property.asLeaf().getConvertWith()).isEqualTo(UppercaseConverter.class));
        assertThat(mapping.getProperties())
                .filteredOn(property -> "rolesByName".equals(property.getMemberName()))
                .singleElement()
                .satisfies(property -> assertThat(property.asMap().getValueProperty().asLeaf().getValueRawType())
                        .isEqualTo(List[].class));
    }

    interface BaseMapping {
        String baseName();
    }

    interface ApplicationMapping extends BaseMapping {
        @WithConverter(UppercaseConverter.class)
        String featureName();

        Map<String, List<String>[]> rolesByName();

        List<String> tags();

        class DefaultImpls {
            public static List<String> tags(final ApplicationMapping mapping) {
                return List.of("default");
            }
        }
    }

    public static class UppercaseConverter implements Converter<String> {
        @Override
        public String convert(final String value) {
            return value.toUpperCase();
        }
    }
}
