/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_config.smallrye_config_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;

import org.eclipse.microprofile.config.spi.Converter;
import org.junit.jupiter.api.Test;

import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

public class ConvertersInnerImplicitInnerStaticMethodConverterTest {
    @Test
    void invokesPublicStaticFactoryMethodWhenConvertingValue() {
        final SmallRyeConfig config = new SmallRyeConfigBuilder().build();
        final Converter<FactoryMethodBackedValue> converter = config.getConverter(FactoryMethodBackedValue.class)
                .orElseThrow();

        final FactoryMethodBackedValue converted = converter.convert("configured-value");

        assertThat(converted.value()).isEqualTo("configured-value");
    }

    public static final class FactoryMethodBackedValue {
        private final String value;

        private FactoryMethodBackedValue(final String value) {
            this.value = value;
        }

        public static FactoryMethodBackedValue of(final String value) {
            return new FactoryMethodBackedValue(Objects.requireNonNull(value));
        }

        public String value() {
            return value;
        }
    }
}
