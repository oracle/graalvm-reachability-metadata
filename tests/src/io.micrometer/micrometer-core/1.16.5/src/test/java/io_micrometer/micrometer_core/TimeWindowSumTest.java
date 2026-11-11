/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import java.time.Duration;

import io.micrometer.core.instrument.distribution.TimeWindowSum;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TimeWindowSumTest {

    @Test
    void accumulatesDurationsInMovingWindow() {
        TimeWindowSum sum = new TimeWindowSum(3, Duration.ofMinutes(1));

        assertThat(sum.poll()).isZero();
        sum.record(15);
        sum.record(25);
        assertThat(sum.poll()).isEqualTo(40.0);
        sum.record(10);
        assertThat(sum.poll()).isEqualTo(50.0);
    }
}
