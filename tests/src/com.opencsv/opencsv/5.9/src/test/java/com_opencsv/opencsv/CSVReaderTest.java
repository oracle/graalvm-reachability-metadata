/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvMalformedLineException;
import com.opencsv.exceptions.CsvMultilineLimitBrokenException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(120)
public class CSVReaderTest {
    @Test
    void readsQuotedMultilineRecords() throws Exception {
        try (CSVReader reader = new CSVReaderBuilder(new StringReader("\"first\nsecond\",tail\n"))
                .withMultilineLimit(2).build()) {
            assertThat(reader.readNext()).containsExactly("first\nsecond", "tail");
            assertThat(reader.readNext()).isNull();
        }
    }

    @Test
    void reportsUnterminatedQuotedRecord() throws Exception {
        try (CSVReader reader = new CSVReaderBuilder(new StringReader("\"unfinished\n"))
                .withErrorLocale(Locale.US).build()) {
            assertThatThrownBy(reader::readNext)
                    .isInstanceOf(CsvMalformedLineException.class)
                    .hasMessageContaining("Unterminated quoted field")
                    .hasMessageContaining("unfinished");
        }
    }

    @Test
    void enforcesConfiguredMultilineLimit() throws Exception {
        try (CSVReader reader = new CSVReaderBuilder(new StringReader("\"first\nsecond\nthird\"\n"))
                .withMultilineLimit(2).withErrorLocale(Locale.US).build()) {
            assertThatThrownBy(reader::readNext)
                    .isInstanceOf(CsvMultilineLimitBrokenException.class)
                    .hasMessageContaining("upper limit of 2")
                    .hasMessageContaining("first");
        }
    }
}
