/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

public class DateDeserializersInnerCalendarDeserializerTest {
    @Test
    void createsRequestedCalendarSubtype() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setTimeZone(TimeZone.getTimeZone("UTC"));

        TestCalendar calendar = mapper.readValue("0", TestCalendar.class);

        assertThat(calendar).isInstanceOf(TestCalendar.class);
        assertThat(calendar.getTimeInMillis()).isZero();
        assertThat(calendar.getTimeZone().getID()).isEqualTo("UTC");
    }

    public static class TestCalendar extends GregorianCalendar {
        public TestCalendar() {
        }
    }
}
