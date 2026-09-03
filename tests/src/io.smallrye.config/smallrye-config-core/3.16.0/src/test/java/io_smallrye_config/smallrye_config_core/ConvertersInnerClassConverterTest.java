/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_config.smallrye_config_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

public class ConvertersInnerClassConverterTest {
    @Test
    void propertyCanBeReadAsClass() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new PropertiesConfigSource(Map.of(
                        "implementation.class", ArrayList.class.getName()), "class-converter-properties"))
                .build();

        Class<?> implementationClass = config.getValue("implementation.class", Class.class);

        assertThat(implementationClass).isSameAs(ArrayList.class);
    }
}
