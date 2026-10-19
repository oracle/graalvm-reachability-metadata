/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Calendar;
import java.util.GregorianCalendar;
import org.junit.jupiter.api.Test;

public class CalendarHandleTest {
    @Test
    void restoresCalendarImplementations() throws Exception {
        Calendar source = new GregorianCalendar(2020, Calendar.JANUARY, 2, 3, 4, 5);
        source.setTimeInMillis(1577934245000L);
        SpecialCalendar specialized = new SpecialCalendar();
        specialized.setTimeInMillis(source.getTimeInMillis());

        Calendar copy = (Calendar) HessianTestSupport.roundTrip(source);
        Calendar specializedCopy = (Calendar) HessianTestSupport.roundTrip(specialized);

        assertThat(copy.getClass()).isEqualTo(GregorianCalendar.class);
        assertThat(copy.getTimeInMillis()).isEqualTo(source.getTimeInMillis());
        assertThat(specializedCopy).isInstanceOf(SpecialCalendar.class);
        assertThat(specializedCopy.getTimeInMillis()).isEqualTo(specialized.getTimeInMillis());
    }

    public static class SpecialCalendar extends GregorianCalendar {
        public SpecialCalendar() { }
    }
}
