/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import io.micrometer.core.instrument.simple.CountingMode;
import io.micrometer.core.instrument.simple.SimpleConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyValidatorTest {
    @Test
    void resolvesSimpleRegistryCountingModeFromConfiguredEnumProperty() {
        SimpleConfig config = property -> {
            if ("simple.mode".equals(property)) {
                return "sTeP";
            }
            return null;
        };

        assertThat(config.mode()).isEqualTo(CountingMode.STEP);
    }
}
