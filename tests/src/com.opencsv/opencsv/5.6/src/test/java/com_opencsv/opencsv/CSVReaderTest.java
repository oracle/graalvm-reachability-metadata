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

import java.io.StringReader;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class CSVReaderTest {
    @Test
    void readNextReportsUnterminatedQuotedRecordAtEndOfInput() {
        assertThatExceptionOfType(CsvMalformedLineException.class)
                .isThrownBy(() -> {
                    try (CSVReader reader = readerFor("\"unterminated")) {
                        reader.readNext();
                    }
                })
                .withMessageContaining("Unterminated quoted field at end of CSV line")
                .withMessageContaining("unterminated")
                .satisfies(exception -> {
                    assertThat(exception.getLineNumber()).isEqualTo(1L);
                    assertThat(exception.getContext()).contains("unterminated");
                });
    }

    @Test
    void readNextRejectsRecordsThatExceedTheMultilineLimit() {
        assertThatExceptionOfType(CsvMultilineLimitBrokenException.class)
                .isThrownBy(() -> {
                    try (CSVReader reader = new CSVReaderBuilder(new StringReader("\"first\nsecond\n"))
                            .withMultilineLimit(1)
                            .withErrorLocale(Locale.US)
                            .build()) {
                        reader.readNext();
                    }
                })
                .withMessageContaining("more lines than the specified upper limit of 1")
                .withMessageContaining("row 1")
                .withMessageContaining("first")
                .satisfies(exception -> {
                    assertThat(exception.getMultilineLimit()).isEqualTo(1);
                    assertThat(exception.getRow()).isEqualTo(1L);
                    assertThat(exception.getContext()).contains("first");
                });
    }

    private static CSVReader readerFor(String csv) {
        return new CSVReaderBuilder(new StringReader(csv))
                .withErrorLocale(Locale.US)
                .build();
    }
}
