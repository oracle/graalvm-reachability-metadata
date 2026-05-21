/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.CSVReaderHeaderAware;
import com.opencsv.CSVReaderHeaderAwareBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CSVReaderHeaderAwareTest {
    @Test
    void readNextRejectsRowsWithTooFewColumns() {
        assertThatThrownBy(() -> {
            try (CSVReaderHeaderAware reader = readerFor("first,last\nAda\n")) {
                reader.readNext("first");
            }
        })
                .isInstanceOf(IOException.class)
                .hasMessageContaining("The number of data elements is not the same as the number of header elements")
                .hasMessageContaining("Expected 2, found 1");
    }

    @Test
    void readNextRejectsUnknownHeaderNames() {
        assertThatThrownBy(() -> {
            try (CSVReaderHeaderAware reader = readerFor("first,last\nAda,Lovelace\n")) {
                reader.readNext("first", "middle");
            }
        })
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No column found for header [middle]");
    }

    @Test
    void readMapRejectsRowsWithTooManyColumns() {
        assertThatThrownBy(() -> {
            try (CSVReaderHeaderAware reader = readerFor("first,last\nAda,Lovelace,Byron\n")) {
                reader.readMap();
            }
        })
                .isInstanceOf(IOException.class)
                .hasMessageContaining("The number of data elements is not the same as the number of header elements")
                .hasMessageContaining("Expected 2, found 3");
    }

    private static CSVReaderHeaderAware readerFor(String csv) {
        return new CSVReaderHeaderAwareBuilder(new StringReader(csv))
                .withErrorLocale(Locale.US)
                .build();
    }
}
