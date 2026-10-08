/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.CSVReaderHeaderAware;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CSVReaderHeaderAwareTest {
    @Test
    void readMapReturnsValuesByHeaderName() throws Exception {
        try (CSVReaderHeaderAware reader = new CSVReaderHeaderAware(new StringReader("name,age\nAlice,30\n"))) {
            Map<String, String> row = reader.readMap();

            assertThat(row)
                    .containsEntry("name", "Alice")
                    .containsEntry("age", "30");
        }
    }

    @Test
    void readNextReportsDataLengthMismatch() throws Exception {
        try (CSVReaderHeaderAware reader = new CSVReaderHeaderAware(new StringReader("name,age\nAlice\n"))) {
            assertThatThrownBy(() -> reader.readNext("name"))
                    .isInstanceOf(IOException.class)
                    .satisfies(exception -> assertThat(exception.getMessage()).isNotBlank());
        }
    }

    @Test
    void readNextReportsMissingHeaderName() throws Exception {
        try (CSVReaderHeaderAware reader = new CSVReaderHeaderAware(new StringReader("name,age\nAlice,30\n"))) {
            assertThatThrownBy(() -> reader.readNext("missing"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .satisfies(exception -> assertThat(exception.getMessage()).isNotBlank());
        }
    }

    @Test
    void readMapReportsDataLengthMismatch() throws Exception {
        try (CSVReaderHeaderAware reader = new CSVReaderHeaderAware(new StringReader("name,age\nAlice\n"))) {
            assertThatThrownBy(reader::readMap)
                    .isInstanceOf(IOException.class)
                    .satisfies(exception -> assertThat(exception.getMessage()).isNotBlank());
        }
    }
}
