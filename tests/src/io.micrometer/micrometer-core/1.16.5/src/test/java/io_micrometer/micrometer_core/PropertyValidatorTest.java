/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import java.util.Map;

import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.simple.CountingMode;
import io.micrometer.core.instrument.simple.SimpleConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyValidatorTest {

    @Test
    void resolvesCaseInsensitiveCountingMode() {
        Map<String, String> properties = Map.of("simple.mode", "sTeP");
        SimpleConfig config = properties::get;

        assertThat(config.mode()).isEqualTo(CountingMode.STEP);
        assertThat(config.validate().isValid()).isTrue();
    }

    @Test
    void reportsInvalidCountingMode() {
        Map<String, String> properties = Map.of("simple.mode", "sliding");
        SimpleConfig config = properties::get;
        Validated<?> validation = config.validate();

        assertThat(validation.isValid()).isFalse();
        assertThat(validation.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.getProperty()).isEqualTo("simple.mode");
            assertThat(failure.getMessage()).contains("CUMULATIVE", "STEP");
        });
    }
}
