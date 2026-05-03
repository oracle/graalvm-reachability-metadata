/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_threeten.threetenbp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.threeten.bp.chrono.ThaiBuddhistDate;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.format.DateTimeFormatterBuilder;
import org.threeten.bp.format.TextStyle;

public class DateTimeFormatterBuilderInnerChronoPrinterParserTest {
    @Test
    void appendChronologyTextFormatsChronologyNameFromResourceBundle() {
        DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                .appendChronologyText(TextStyle.FULL)
                .toFormatter(Locale.ENGLISH);

        String formatted = formatter.format(ThaiBuddhistDate.of(2567, 1, 1));

        assertThat(formatted).isEqualTo("Buddhist Calendar");
    }
}
