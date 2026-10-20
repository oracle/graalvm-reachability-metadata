/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import java.util.Calendar;

import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.deser.std.DateDeserializers.CalendarDeserializer;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DateDeserializersInnerCalendarDeserializerTest {
    @Test
    void createsAConcreteCalendarSubtype() throws Exception {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(SampleCalendar.class, new CalendarDeserializer(SampleCalendar.class));
        ObjectMapper mapper = new ObjectMapper().registerModule(module);

        SampleCalendar calendar = mapper.readValue("1234", SampleCalendar.class);

        assertThat(calendar.getTimeInMillis()).isEqualTo(1234L);
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
