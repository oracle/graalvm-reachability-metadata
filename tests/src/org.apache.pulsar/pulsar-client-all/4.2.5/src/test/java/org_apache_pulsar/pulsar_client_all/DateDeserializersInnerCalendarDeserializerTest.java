/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Calendar;
import java.util.GregorianCalendar;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class DateDeserializersInnerCalendarDeserializerTest {
    @Test
    void constructsRequestedCalendarSubtype() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        GregorianCalendar calendar = mapper.readValue(
                "\"2024-06-15T10:20:30.000+00:00\"", GregorianCalendar.class);

        assertThat(calendar.get(Calendar.YEAR)).isEqualTo(2024);
        assertThat(calendar.get(Calendar.MONTH)).isEqualTo(Calendar.JUNE);
        assertThat(calendar.get(Calendar.DAY_OF_MONTH)).isEqualTo(15);
    }
}
