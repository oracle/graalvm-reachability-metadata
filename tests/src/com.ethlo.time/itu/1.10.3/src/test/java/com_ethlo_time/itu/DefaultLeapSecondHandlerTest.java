/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ethlo_time.itu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;

import org.junit.jupiter.api.Test;

import com.ethlo.time.internal.util.DefaultLeapSecondHandler;

public class DefaultLeapSecondHandlerTest {
    @Test
    void loadsKnownLeapSecondTableFromClasspathResource() {
        DefaultLeapSecondHandler handler = new DefaultLeapSecondHandler();

        assertThat(handler.isValidLeapSecondDate(YearMonth.of(2016, 12))).isTrue();
        assertThat(handler.isValidLeapSecondDate(YearMonth.of(2014, 12))).isFalse();
        assertThat(handler.getLastKnownLeapSecond()).isEqualTo(YearMonth.of(2016, 12));
    }
}
