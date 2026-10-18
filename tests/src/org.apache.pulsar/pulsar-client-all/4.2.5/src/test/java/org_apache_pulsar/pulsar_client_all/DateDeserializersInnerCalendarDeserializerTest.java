/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.GregorianCalendar;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

public class DateDeserializersInnerCalendarDeserializerTest {

    @Test
    void instantiatesACustomCalendarType() throws Exception {
        final CustomCalendar calendar = new ObjectMapper().readValue("1700000000000", CustomCalendar.class);

        assertThat(calendar.getTimeInMillis()).isEqualTo(1700000000000L);
    }

    public static final class CustomCalendar extends GregorianCalendar {
        private static final long serialVersionUID = 1L;

        public CustomCalendar() {
        }
    }
}
