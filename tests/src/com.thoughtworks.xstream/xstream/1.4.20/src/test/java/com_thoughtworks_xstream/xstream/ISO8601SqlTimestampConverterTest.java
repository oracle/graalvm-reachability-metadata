/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;

import com.thoughtworks.xstream.converters.extended.ISO8601SqlTimestampConverter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ISO8601SqlTimestampConverterTest {
    @Test
    void recognizesSqlTimestampValues() {
        ISO8601SqlTimestampConverter converter = new ISO8601SqlTimestampConverter();

        assertThat(converter.canConvert(Timestamp.class)).isTrue();
        assertThat(converter.canConvert(Date.class)).isFalse();
    }

    @Test
    void convertsIso8601TextWithFractionalSecondsToSqlTimestamp() {
        ISO8601SqlTimestampConverter converter = new ISO8601SqlTimestampConverter();

        Object converted = converter.fromString("2024-04-28T10:15:30.123456789Z");

        assertThat(converted).isInstanceOf(Timestamp.class);
        Timestamp timestamp = (Timestamp)converted;
        assertThat(timestamp.getTime()).isEqualTo(1714299330123L);
        assertThat(timestamp.getNanos()).isEqualTo(123456789);
    }

    @Test
    void serializesSqlTimestampValuesWithNanosecondPrecision() {
        ISO8601SqlTimestampConverter converter = new ISO8601SqlTimestampConverter();
        Timestamp timestamp = Timestamp.from(Instant.parse("2024-04-28T10:15:30.123456789Z"));

        String serialized = converter.toString(timestamp);

        assertThat(serialized).contains("T");
        assertThat(serialized).contains(".123456789");
        assertThat(converter.fromString(serialized)).isEqualTo(timestamp);
    }
}
