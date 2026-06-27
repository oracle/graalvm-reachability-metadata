/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.GregorianCalendar;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.databind.ObjectMapper;

public class DateDeserializersInnerCalendarDeserializerTest {
    @Test
    void deserializesGregorianCalendarUsingItsDefaultConstructor() throws Exception {
        final ObjectMapper objectMapper = new ObjectMapper();
        final long timestampMillis = 1_700_000_000_000L;

        final GregorianCalendar calendar = objectMapper.readValue(
                Long.toString(timestampMillis), GregorianCalendar.class);

        assertThat(calendar).isInstanceOf(GregorianCalendar.class);
        assertThat(calendar.getTimeInMillis()).isEqualTo(timestampMillis);
    }
}
