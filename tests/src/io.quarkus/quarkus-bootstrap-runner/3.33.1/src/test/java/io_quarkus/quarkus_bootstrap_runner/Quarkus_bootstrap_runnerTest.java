/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_bootstrap_runner;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import io.quarkus.bootstrap.runner.Timing;
import org.junit.jupiter.api.Test;

public class Quarkus_bootstrap_runnerTest {
    @Test
    void formatsNanosecondDurationsAsRoundedSeconds() {
        assertThat(Timing.convertToSecondsString(0)).isEqualTo("0.000");
        assertThat(Timing.convertToSecondsString(1_000_000)).isEqualTo("0.001");
        assertThat(Timing.convertToSecondsString(9_000_000)).isEqualTo("0.009");
        assertThat(Timing.convertToSecondsString(99_000_000)).isEqualTo("0.099");
        assertThat(Timing.convertToSecondsString(999_499_999)).isEqualTo("0.999");
        assertThat(Timing.convertToSecondsString(999_500_000)).isEqualTo("1.000");
        assertThat(Timing.convertToSecondsString(1_234_567_890)).isEqualTo("1.235");
        assertThat(Timing.convertToSecondsString(61_000_000_000L)).isEqualTo("61.000");
    }

    @Test
    void convertsNanosecondDurationsToRoundedDecimalSeconds() {
        assertThat(Timing.convertToBigDecimalSeconds(0)).isEqualByComparingTo(BigDecimal.valueOf(0).setScale(3));
        assertThat(Timing.convertToBigDecimalSeconds(999_499_999))
                .isEqualByComparingTo(BigDecimal.valueOf(999, 3));
        assertThat(Timing.convertToBigDecimalSeconds(999_500_000))
                .isEqualByComparingTo(BigDecimal.valueOf(1).setScale(3));
        assertThat(Timing.convertToBigDecimalSeconds(1_234_567_890))
                .isEqualByComparingTo(BigDecimal.valueOf(1_235, 3));
    }
}
