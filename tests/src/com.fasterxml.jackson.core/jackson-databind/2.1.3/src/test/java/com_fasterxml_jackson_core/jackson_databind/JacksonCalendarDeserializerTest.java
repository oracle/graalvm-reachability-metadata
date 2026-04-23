/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import java.util.GregorianCalendar;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonCalendarDeserializerTest {

    @Test
    void calendarDeserializerCreatesConcreteCalendarType() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        GregorianCalendar calendar = mapper.readValue("0", GregorianCalendar.class);
        assertThat(calendar.getTimeInMillis()).isEqualTo(0L);
    }
}
