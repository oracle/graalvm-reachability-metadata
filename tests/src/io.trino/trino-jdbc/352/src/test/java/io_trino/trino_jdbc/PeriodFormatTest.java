/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.joda.time.Period;
import io.trino.jdbc.$internal.joda.time.format.PeriodFormat;
import io.trino.jdbc.$internal.joda.time.format.PeriodFormatter;
import java.util.Locale;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PeriodFormatTest {
    @Test
    void readsAndWritesEnglishPeriodWords() {
        PeriodFormatter formatter = PeriodFormat.wordBased(Locale.ENGLISH);
        Period period = Period.days(2).withHours(3);
        assertEquals("2 days and 3 hours", formatter.print(period));
        assertEquals(period, formatter.parsePeriod("2 days and 3 hours"));
    }
}
