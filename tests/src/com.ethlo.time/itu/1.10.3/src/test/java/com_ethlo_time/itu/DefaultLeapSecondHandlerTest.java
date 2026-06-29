/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ethlo_time.itu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.OffsetDateTime;

import com.ethlo.time.ITU;
import com.ethlo.time.LeapSecondException;

import org.junit.jupiter.api.Test;

public class DefaultLeapSecondHandlerTest {
    @Test
    void knownLeapSecondIsVerifiedFromBundledLeapSecondTable() {
        LeapSecondException exception = assertThrows(
                LeapSecondException.class,
                () -> ITU.parseDateTime("2016-12-31T23:59:60Z"));

        assertThat(exception.getSecondsInMinute()).isEqualTo(60);
        assertThat(exception.isVerifiedValidLeapYearMonth()).isTrue();
        assertThat(exception.getNearestDateTime())
                .isEqualTo(OffsetDateTime.parse("2017-01-01T00:00:00Z"));
    }
}
