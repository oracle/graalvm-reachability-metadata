/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import java.util.Collections;
import java.util.Map;

import io.micrometer.core.instrument.config.MeterRegistryConfig;
import io.micrometer.core.instrument.config.validate.PropertyValidator;
import io.micrometer.core.instrument.config.validate.Validated;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyValidatorTest {
    @Test
    void returnsEnumConfiguredByCaseInsensitiveProperty() {
        MeterRegistryConfig config = new TestConfig("metrics",
                Collections.singletonMap("metrics.format", "prometheus"));

        Validated<ExportFormat> exportFormat = PropertyValidator.getEnum(config, ExportFormat.class,
                "format");

        assertThat(exportFormat.isValid()).isTrue();
        assertThat(exportFormat.get()).isEqualTo(ExportFormat.PROMETHEUS);
    }

    @Test
    void reportsSupportedEnumValuesForInvalidProperty() {
        MeterRegistryConfig config = new TestConfig("metrics",
                Collections.singletonMap("metrics.format", "datadog"));

        Validated<ExportFormat> exportFormat = PropertyValidator.getEnum(config, ExportFormat.class,
                "format");

        assertThat(exportFormat.isInvalid()).isTrue();
        assertThat(exportFormat.failures()).hasSize(1);
        Validated.Invalid<?> failure = exportFormat.failures().get(0);
        assertThat(failure.getProperty()).isEqualTo("metrics.format");
        assertThat(failure.getValue()).isEqualTo("datadog");
        assertThat(failure.getMessage()).contains("'PROMETHEUS'", "'ATLAS'");
    }

    public enum ExportFormat {
        PROMETHEUS,
        ATLAS
    }

    private static final class TestConfig implements MeterRegistryConfig {
        private final String prefix;

        private final Map<String, String> values;

        private TestConfig(String prefix, Map<String, String> values) {
            this.prefix = prefix;
            this.values = values;
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
