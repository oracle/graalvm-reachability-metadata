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

import org.apache.pulsar.shade.com.fasterxml.jackson.databind.JsonDeserializer;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.deser.std.DateDeserializers.CalendarDeserializer;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.Test;

public class DateDeserializersInnerCalendarDeserializerTest {
    @Test
    @SuppressWarnings("unchecked")
    void createsRequestedCalendarSubtype() throws Exception {
        JsonDeserializer<TestCalendar> deserializer = (JsonDeserializer<TestCalendar>) (JsonDeserializer<?>)
                new CalendarDeserializer(TestCalendar.class);
        SimpleModule module = new SimpleModule();
        module.addDeserializer(TestCalendar.class, deserializer);
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(module);
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
