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
import io.micrometer.core.instrument.simple.SimpleConfig;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyValidatorTest {
    @Test
    void simpleConfigModeResolvesEnumValueIgnoringCase() {
        SimpleConfig config = key -> Collections.singletonMap("simple.mode", "step").get(key);

        assertThat(config.mode()).isEqualTo(CountingMode.STEP);
    }

    @Test
    void getEnumReportsMalformedValueWithAllowedEnumConstants() {
        MeterRegistryConfig config = new MapBackedMeterRegistryConfig(
                "simple",
                Collections.singletonMap("simple.mode", "rate")
        );

        Validated<CountingMode> validated = PropertyValidator.getEnum(config, CountingMode.class, "mode");

        assertThat(validated.isInvalid()).isTrue();
        assertThat(validated.failures()).hasSize(1);

        Validated.Invalid<?> failure = validated.failures().get(0);
        assertThat(failure.getProperty()).isEqualTo("simple.mode");
        assertThat(failure.getValue()).isEqualTo("rate");
        assertThat(failure.getReason()).isEqualTo(InvalidReason.MALFORMED);
        assertThat(failure.getMessage()).contains("'CUMULATIVE'", "'STEP'");
    }

    private static final class MapBackedMeterRegistryConfig implements MeterRegistryConfig {
        private final String prefix;
        private final Map<String, String> values;

        private MapBackedMeterRegistryConfig(String prefix, Map<String, String> values) {
            this.prefix = prefix;
            this.values = new HashMap<>(values);
        }

        @Override
        public String prefix() {
            return prefix;
        }

        @Override
        public String get(String key) {
            return values.get(key);
        }
    }
}
