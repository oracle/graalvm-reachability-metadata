/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.databind.ObjectMapper;

public class DateDeserializersInnerCalendarDeserializerTest {
    @Test
    void deserializesConcreteGregorianCalendarFromTimestamp() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        TimeZone expectedTimeZone = TimeZone.getTimeZone("America/Los_Angeles");
        mapper.setTimeZone(expectedTimeZone);

        GregorianCalendar calendar = mapper.readValue("0", GregorianCalendar.class);

        assertThat(calendar.getTimeInMillis()).isEqualTo(0L);
        assertThat(calendar.getTimeZone().getID()).isEqualTo(expectedTimeZone.getID());
    }
}
