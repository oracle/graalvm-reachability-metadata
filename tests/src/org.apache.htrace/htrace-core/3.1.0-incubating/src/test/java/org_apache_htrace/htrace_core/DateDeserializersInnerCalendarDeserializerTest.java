/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_htrace.htrace_core;

import java.util.GregorianCalendar;

import org.apache.htrace.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DateDeserializersInnerCalendarDeserializerTest {
    @Test
    void deserializesTimestampIntoGregorianCalendar() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        GregorianCalendar calendar = mapper.readValue("0", GregorianCalendar.class);

        assertThat(calendar.getTimeInMillis()).isZero();
    }
}
