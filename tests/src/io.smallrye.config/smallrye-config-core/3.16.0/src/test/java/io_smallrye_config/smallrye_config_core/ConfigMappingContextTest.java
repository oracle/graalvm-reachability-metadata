/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_config.smallrye_config_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.microprofile.config.spi.Converter;
import org.junit.jupiter.api.Test;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.WithConverter;

public class ConfigMappingContextTest {
    @Test
    void instantiatesExplicitMappingConverterThroughContext() {
        CountingUppercaseConverter.instances.set(0);

        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new PropertiesConfigSource(Map.of(
                        "application.name", "smallrye",
                        "application.description", "config"),
                        "mapping-converter-properties"))
                .withMapping(ApplicationMapping.class)
                .build();

        ApplicationMapping mapping = config.getConfigMapping(ApplicationMapping.class);

        assertThat(mapping.name()).isEqualTo("SMALLRYE");
        assertThat(mapping.description()).isEqualTo("CONFIG");
        assertThat(CountingUppercaseConverter.instances).hasValue(1);
    }

    @ConfigMapping(prefix = "application")
    public interface ApplicationMapping {
        @WithConverter(CountingUppercaseConverter.class)
        String name();

        @WithConverter(CountingUppercaseConverter.class)
        String description();
    }

    public static class CountingUppercaseConverter implements Converter<String> {
        static final AtomicInteger instances = new AtomicInteger();

        public CountingUppercaseConverter() {
            instances.incrementAndGet();
        }

        @Override
        public String convert(final String value) {
            return value.toUpperCase();
        }
    }
}
