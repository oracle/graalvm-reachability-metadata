/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_neo4j_driver.neo4j_java_driver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DateTimeException;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.internal.value.UnsupportedDateTimeValue;

public class UnsupportedDateTimeValueTest {
    @Test
    void recreatesOriginalDateTimeExceptionWhenCoercingToOffsetDateTime() {
        DateTimeException originalException = new DateTimeException("zone rules are unavailable");
        UnsupportedDateTimeValue value = new UnsupportedDateTimeValue(originalException);

        assertThatThrownBy(value::asOffsetDateTime)
                .isInstanceOf(DateTimeException.class)
                .isNotSameAs(originalException)
                .hasMessage(originalException.getMessage())
                .satisfies(exception -> assertThat(exception.getCause()).isSameAs(originalException));
    }
}
