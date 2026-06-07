/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.opencsv.CSVReaderHeaderAware;
import com.opencsv.CSVReaderHeaderAwareBuilder;
import java.io.IOException;
import java.io.StringReader;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class CSVReaderHeaderAwareTest {
    @Test
    void readNextReturnsValuesForRequestedHeaders() throws Exception {
        try (CSVReaderHeaderAware reader = newHeaderAwareReader("first,last,age\nAda,Lovelace,36\n")) {
            String[] values = reader.readNext("last", "first");

            assertThat(values).containsExactly("Lovelace", "Ada");
        }
    }

    @Test
    void readNextReportsMismatchedDataFieldCount() throws Exception {
        try (CSVReaderHeaderAware reader = newHeaderAwareReader("first,last\nAda\n")) {
            assertThatExceptionOfType(IOException.class)
                    .isThrownBy(() -> reader.readNext("first"))
                    .withMessageContaining("number of data elements")
                    .withMessageContaining("Expected 2, found 1");
        }
    }

    @Test
    void readNextReportsUnknownRequestedHeader() throws Exception {
        try (CSVReaderHeaderAware reader = newHeaderAwareReader("first,last\nAda,Lovelace\n")) {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> reader.readNext("middle"))
                    .withMessageContaining("No column found for header [middle]");
        }
    }

    @Test
    void readMapReturnsValuesByHeaderName() throws Exception {
        try (CSVReaderHeaderAware reader = newHeaderAwareReader("first,last\nAda,Lovelace\n")) {
            Map<String, String> row = reader.readMap();

            assertThat(row)
                    .containsEntry("first", "Ada")
                    .containsEntry("last", "Lovelace");
        }
    }

    @Test
    void readMapReportsMismatchedDataFieldCount() throws Exception {
        try (CSVReaderHeaderAware reader = newHeaderAwareReader("first,last\nAda,Lovelace,extra\n")) {
            assertThatExceptionOfType(IOException.class)
                    .isThrownBy(reader::readMap)
                    .withMessageContaining("number of data elements")
                    .withMessageContaining("Expected 2, found 3");
        }
    }

    private static CSVReaderHeaderAware newHeaderAwareReader(String csv) {
        return new CSVReaderHeaderAwareBuilder(new StringReader(csv))
                .withErrorLocale(Locale.ROOT)
                .build();
    }
}
