/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.ParseException;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.commons.lang3.time.FastDateFormat;
import org.junit.jupiter.api.Test;

public class FastDateFormatTest {

    @Test
    public void serializedFormatRetainsFormattingAndParsingBehavior()
            throws IOException, ClassNotFoundException, ParseException {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        FastDateFormat format = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss", utc, Locale.ROOT);
        Date instant = new Date(1_704_110_400_000L);

        FastDateFormat copy = roundTrip(format);

        assertThat(copy).isNotSameAs(format);
        assertThat(copy.format(instant)).isEqualTo("2024-01-01 12:00:00");
        assertThat(copy.parse("2024-01-01 12:00:00")).isEqualTo(instant);
    }

    private static FastDateFormat roundTrip(FastDateFormat format) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(format);
        }

        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (FastDateFormat) inputStream.readObject();
        }
    }
}
