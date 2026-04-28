/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import com.thoughtworks.xstream.converters.extended.ISO8601GregorianCalendarConverter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ISO8601GregorianCalendarConverterTest {
    @Test
    void convertsGregorianCalendarToIso8601Text() {
        ISO8601GregorianCalendarConverter converter = new ISO8601GregorianCalendarConverter();
        GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.ROOT);
        calendar.clear();
        calendar.set(2024, GregorianCalendar.APRIL, 28, 10, 15, 30);
        calendar.set(GregorianCalendar.MILLISECOND, 123);

        assertThat(converter.canConvert(GregorianCalendar.class)).isTrue();
        assertThat(converter.toString(calendar)).isEqualTo("2024-04-28T10:15:30.123Z");
    }

    @Test
    void convertsIso8601TextToGregorianCalendar() {
        ISO8601GregorianCalendarConverter converter = new ISO8601GregorianCalendarConverter();
        GregorianCalendar expected = new GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.ROOT);
        expected.clear();
        expected.set(2024, GregorianCalendar.APRIL, 28, 10, 15, 30);
        expected.set(GregorianCalendar.MILLISECOND, 123);

        Object converted = converter.fromString("2024-04-28T10:15:30.123Z");

        assertThat(converted).isInstanceOf(GregorianCalendar.class);
        assertThat(((GregorianCalendar)converted).getTimeInMillis()).isEqualTo(expected.getTimeInMillis());
    }
}
