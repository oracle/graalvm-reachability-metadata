/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_config.smallrye_config_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.Optional;

import org.eclipse.microprofile.config.spi.Converter;
import org.junit.jupiter.api.Test;

import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

public class ConvertersInnerImplicitTest {
    @Test
    void findsImplicitConverterFromPublicStaticFactoryMethod() {
        final SmallRyeConfig config = new SmallRyeConfigBuilder().build();

        final Optional<Converter<FactoryBackedValue>> converter = config.getConverter(FactoryBackedValue.class);

        assertThat(converter).isPresent();
    }

    public static final class FactoryBackedValue {
        private FactoryBackedValue() {
        }

        public static FactoryBackedValue of(final String value) {
            Objects.requireNonNull(value);
            return new FactoryBackedValue();
        }
    }
}
