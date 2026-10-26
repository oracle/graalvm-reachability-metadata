/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import java.io.IOException;
import java.util.Calendar;

import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.core.JsonParser;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.DeserializationContext;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.JsonDeserializer;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.deser.std.DateDeserializers.CalendarDeserializer;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DateDeserializersInnerCalendarDeserializerTest {
    @Test
    void createsAConcreteCalendarSubtype() throws Exception {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(SampleCalendar.class, new SampleCalendarDeserializer());
        ObjectMapper mapper = new ObjectMapper().registerModule(module);

        SampleCalendar calendar = mapper.readValue("1234", SampleCalendar.class);

        assertThat(calendar.getTimeInMillis()).isEqualTo(1234L);
    }

    public static class SampleCalendarDeserializer extends JsonDeserializer<SampleCalendar> {
        private final CalendarDeserializer delegate = new CalendarDeserializer(SampleCalendar.class);

        @Override
        public SampleCalendar deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            return (SampleCalendar) delegate.deserialize(parser, context);
        }
    }

    public static class SampleCalendar extends Calendar {
        public SampleCalendar() {
        }

        @Override
        protected void computeTime() {
        }

        @Override
        protected void computeFields() {
        }

        @Override
        public void add(int field, int amount) {
        }

        @Override
        public void roll(int field, boolean up) {
        }

        @Override
        public int getMinimum(int field) {
            return 0;
        }

        @Override
        public int getMaximum(int field) {
            return 0;
        }

        @Override
        public int getGreatestMinimum(int field) {
            return 0;
        }

        @Override
        public int getLeastMaximum(int field) {
            return 0;
        }
    }
}
