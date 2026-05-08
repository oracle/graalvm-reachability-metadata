/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_config.smallrye_config_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.microprofile.config.spi.Converter;
import org.junit.jupiter.api.Test;

import io.smallrye.config.Converters;

public class ConvertersInnerArrayConverterTest {
    @Test
    void convertsCommaSeparatedValueToTypedArray() {
        Converter<String[]> converter = Converters.newArrayConverter(
                Converters.getImplicitConverter(String.class), String[].class);

        String[] values = converter.convert("alpha,beta,gamma");

        assertThat(values).containsExactly("alpha", "beta", "gamma");
        assertThat(values).isInstanceOf(String[].class);
    }
}
