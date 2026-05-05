/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import io.micrometer.core.instrument.config.MeterRegistryConfig;
import io.micrometer.core.instrument.config.validate.InvalidReason;
import io.micrometer.core.instrument.config.validate.PropertyValidator;
import io.micrometer.core.instrument.config.validate.Validated;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyValidatorTest {
    @Test
    void getEnumAcceptsCaseInsensitiveEnumName() {
        MeterRegistryConfig config = new MapBackedMeterRegistryConfig(
                "example", Collections.singletonMap("example.unit", "seconds"));

        Validated<TimeUnit> validated = PropertyValidator.getEnum(config, TimeUnit.class, "unit");

        assertThat(validated.isValid()).isTrue();
        assertThat(validated.get()).isEqualTo(TimeUnit.SECONDS);
    }

    @Test
    void getEnumReportsAllowedValuesForMalformedEnumName() {
        MeterRegistryConfig config = new MapBackedMeterRegistryConfig(
                "example", Collections.singletonMap("example.unit", "fortnight"));

        Validated<TimeUnit> validated = PropertyValidator.getEnum(config, TimeUnit.class, "unit");

        assertThat(validated.isInvalid()).isTrue();
        assertThat(validated.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.getProperty()).isEqualTo("example.unit");
            assertThat(failure.getValue()).isEqualTo("fortnight");
            assertThat(failure.getReason()).isEqualTo(InvalidReason.MALFORMED);
            assertThat(failure.getMessage()).contains("'NANOSECONDS'", "'SECONDS'", "'DAYS'");
        });
    }

    private static final class MapBackedMeterRegistryConfig implements MeterRegistryConfig {
        private final String prefix;
        private final Map<String, String> properties;

        private MapBackedMeterRegistryConfig(String prefix, Map<String, String> properties) {
            this.prefix = prefix;
            this.properties = properties;
        }

        @Override
        public String prefix() {
            return prefix;
        }

        @Override
        public String get(String key) {
            return properties.get(key);
        }
    }
}
