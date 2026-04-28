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
import io.micrometer.core.instrument.simple.CountingMode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyValidatorTest {
    @Test
    void resolvesEnumPropertyCaseInsensitively() {
        MeterRegistryConfig config = configWith("sample.mode", "step");

        Validated<CountingMode> validated = PropertyValidator.getEnum(config, CountingMode.class, "mode");

        assertThat(validated.isValid()).isTrue();
        assertThat(validated.get()).isEqualTo(CountingMode.STEP);
    }

    @Test
    void reportsAllowedEnumValuesWhenPropertyIsUnknown() {
        MeterRegistryConfig config = configWith("sample.mode", "delta");

        Validated<CountingMode> validated = PropertyValidator.getEnum(config, CountingMode.class, "mode");

        assertThat(validated.isInvalid()).isTrue();
        assertThat(validated.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.getProperty()).isEqualTo("sample.mode");
            assertThat(failure.getValue()).isEqualTo("delta");
            assertThat(failure.getReason()).isEqualTo(InvalidReason.MALFORMED);
            assertThat(failure.getMessage()).contains("'CUMULATIVE'", "'STEP'");
        });
    }

    private static MeterRegistryConfig configWith(String property, String value) {
        return new MapBackedConfig(Map.of(property, value));
    }

    private static final class MapBackedConfig implements MeterRegistryConfig {
        private final Map<String, String> properties;

        private MapBackedConfig(Map<String, String> properties) {
            this.properties = new HashMap<>(properties);
        }

        @Override
        public String prefix() {
            return "sample";
        }

        @Override
        public String get(String key) {
            return properties.get(key);
        }
    }
}
