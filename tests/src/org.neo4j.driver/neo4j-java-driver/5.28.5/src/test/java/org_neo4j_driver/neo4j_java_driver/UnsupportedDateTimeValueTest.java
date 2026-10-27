/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_neo4j_driver.neo4j_java_driver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.zone.ZoneRulesException;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Value;
import org.neo4j.driver.internal.value.UnsupportedDateTimeValue;

public class UnsupportedDateTimeValueTest {
    @Test
    void rethrowsUnsupportedDateTimeAsFreshExceptionOfSameType() {
        ZoneRulesException originalException =
                new ZoneRulesException("Unknown time-zone ID: Mars/Colony");
        Value value = new UnsupportedDateTimeValue(originalException);

        Throwable thrown = catchThrowable(value::asZonedDateTime);

        assertThat(thrown)
                .isInstanceOf(ZoneRulesException.class)
                .hasMessage(originalException.getMessage())
                .isNotSameAs(originalException);
        assertThat(thrown.getCause()).isSameAs(originalException);
    }
}
