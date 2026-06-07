/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvMalformedLineException;
import com.opencsv.exceptions.CsvMultilineLimitBrokenException;
import java.io.StringReader;
import java.util.Locale;
import org.junit.jupiter.api.Test;

public class CSVReaderTest {
    @Test
    void readNextReportsUnterminatedQuotedRecordAtEndOfInput() throws Exception {
        try (CSVReader reader = newReader("name,\"unterminated")) {
            CsvMalformedLineException exception = assertThrows(CsvMalformedLineException.class, reader::readNext);

            assertThat(exception)
                    .hasMessageContaining("Unterminated quoted field")
                    .hasMessageContaining("unterminated");
            assertThat(exception.getLineNumber()).isEqualTo(1L);
        }
    }

    @Test
    void readNextReportsMultilineRecordLimit() throws Exception {
        try (CSVReader reader = new CSVReaderBuilder(new StringReader("name,\"first line\nsecond line\"\n"))
                .withMultilineLimit(1)
                .withErrorLocale(Locale.ROOT)
                .build()) {
            CsvMultilineLimitBrokenException exception = assertThrows(CsvMultilineLimitBrokenException.class,
                    reader::readNext);

            assertThat(exception)
                    .hasMessageContaining("more lines than the specified upper limit of 1")
                    .hasMessageContaining("row 1")
                    .hasMessageContaining("first line");
            assertThat(exception.getMultilineLimit()).isEqualTo(1);
            assertThat(exception.getRow()).isEqualTo(1L);
        }
    }

    private static CSVReader newReader(String csv) {
        return new CSVReaderBuilder(new StringReader(csv))
                .withErrorLocale(Locale.ROOT)
                .build();
    }
}
