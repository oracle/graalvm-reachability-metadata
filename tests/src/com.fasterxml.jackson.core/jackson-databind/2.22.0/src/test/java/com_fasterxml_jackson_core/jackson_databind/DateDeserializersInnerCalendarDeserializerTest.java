/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import java.util.GregorianCalendar;
import java.util.TimeZone;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DateDeserializersInnerCalendarDeserializerTest {

    @Test
    void deserializesGregorianCalendarWithConfiguredTimeZone() throws Exception {
        TimeZone timeZone = TimeZone.getTimeZone("Europe/Paris");
        ObjectMapper objectMapper = new ObjectMapper()
                .setTimeZone(timeZone);

        GregorianCalendar calendar = objectMapper.readValue("1700000000000", GregorianCalendar.class);

        assertThat(calendar.getTimeInMillis()).isEqualTo(1700000000000L);
        assertThat(calendar.getTimeZone()).isEqualTo(timeZone);
    }
}
