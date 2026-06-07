/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import java.io.StringReader;
import java.util.Iterator;
import java.util.Locale;
import org.junit.jupiter.api.Test;

public class CSVIteratorTest {
    @Test
    void removeIsUnsupportedWithLocalizedMessage() throws Exception {
        try (CSVReader reader = new CSVReaderBuilder(new StringReader("name,value\nalpha,beta\n"))
                .withErrorLocale(Locale.ROOT)
                .build()) {
            Iterator<String[]> iterator = reader.iterator();

            UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                    iterator::remove);

            assertEquals("This is a read-only iterator.", exception.getMessage());
        }
    }
}
