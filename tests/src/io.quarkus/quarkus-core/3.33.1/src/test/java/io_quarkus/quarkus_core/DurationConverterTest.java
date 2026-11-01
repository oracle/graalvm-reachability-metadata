/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.runtime.configuration.ConfigUtils;
import io.quarkus.runtime.configuration.DurationConverter;
import io.smallrye.config.SmallRyeConfig;

public class DurationConverterTest {
    @Test
    void discoversConverterForDurationConfiguration() {
        SmallRyeConfig config = ConfigUtils.emptyConfigBuilder()
                .withDefaultValues(Map.of(
                        "app.delay", "25",
                        "app.interval", "1h30m",
                        "app.precision", "250ms",
                        "app.fraction", "1.5s",
                        "app.iso", "PT2M"))
                .build();

        assertThat(config.getValue("app.delay", Duration.class)).isEqualTo(Duration.ofSeconds(25));
        assertThat(config.getValue("app.interval", Duration.class)).isEqualTo(Duration.ofMinutes(90));
        assertThat(config.getValue("app.precision", Duration.class)).isEqualTo(Duration.ofMillis(250));
        assertThat(config.getValue("app.fraction", Duration.class)).isEqualTo(Duration.ofMillis(1500));
        assertThat(config.getValue("app.iso", Duration.class)).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void handlesEmptyAndInvalidDurationInputs() {
        DurationConverter converter = new DurationConverter();

        assertThat(converter.convert("  ")).isNull();
        assertThat(converter.convert(" -3s ")).isEqualTo(Duration.ofSeconds(-3));
        assertThatThrownBy(() -> converter.convert("tomorrow"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid duration");
    }
}
