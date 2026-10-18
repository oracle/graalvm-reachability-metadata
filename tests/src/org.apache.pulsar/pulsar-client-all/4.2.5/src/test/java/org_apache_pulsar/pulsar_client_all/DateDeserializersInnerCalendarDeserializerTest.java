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
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.deser.std.DateDeserializers.CalendarDeserializer;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.Test;

public class DateDeserializersInnerCalendarDeserializerTest {

    @Test
    void instantiatesACustomCalendarType() throws Exception {
        final SimpleModule module = new SimpleModule();
        module.addDeserializer(Calendar.class, new CalendarDeserializer(CustomCalendar.class));
        final ObjectMapper mapper = new ObjectMapper().registerModule(module);

        final Calendar calendar = mapper.readValue("1700000000000", Calendar.class);

        assertThat(calendar).isInstanceOf(CustomCalendar.class);
        assertThat(calendar.getTimeInMillis()).isEqualTo(1700000000000L);
    }

    public static final class CustomCalendar extends GregorianCalendar {
        private static final long serialVersionUID = 1L;

        public CustomCalendar() {
        }
    }
}
