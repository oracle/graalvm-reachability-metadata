/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.util.Date;

import com.thoughtworks.xstream.converters.extended.ISO8601DateConverter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ISO8601DateConverterTest {
    @Test
    void recognizesDateValues() {
        ISO8601DateConverter converter = new ISO8601DateConverter();

        assertThat(converter.canConvert(Date.class)).isTrue();
        assertThat(converter.canConvert(String.class)).isFalse();
    }

    @Test
    void convertsIso8601TextToDate() {
        ISO8601DateConverter converter = new ISO8601DateConverter();
        Date expected = new Date(1714299330123L);

        Object converted = converter.fromString("2024-04-28T10:15:30.123Z");

        assertThat(converted).isInstanceOf(Date.class);
        assertThat(converted).isEqualTo(expected);
    }

    @Test
    void serializesDateValuesForRoundTripConversion() {
        ISO8601DateConverter converter = new ISO8601DateConverter();
        Date date = new Date(1714299330123L);

        String serialized = converter.toString(date);

        assertThat(serialized).contains("T");
        assertThat(converter.fromString(serialized)).isEqualTo(date);
    }
}
