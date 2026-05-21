/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.CSVIterator;
import com.opencsv.CSVReader;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class CSVIteratorTest {
    @Test
    void removeReportsReadOnlyIteratorUsingConfiguredLocale() throws Exception {
        try (CSVReader reader = new CSVReader(new StringReader("alpha,beta\n"))) {
            CSVIterator iterator = new CSVIterator(reader);
            iterator.setErrorLocale(Locale.US);

            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(iterator::remove)
                    .withMessage("This is a read-only iterator.");
        }
    }
}
