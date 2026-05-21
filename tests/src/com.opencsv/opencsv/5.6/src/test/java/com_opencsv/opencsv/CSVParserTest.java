/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.ICSVParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CSVParserTest {
    @Test
    void rejectsDuplicateSeparatorQuoteAndEscapeCharacters() {
        assertThatThrownBy(() -> new CSVParserBuilder()
                .withSeparator(';')
                .withQuoteChar(';')
                .withErrorLocale(Locale.US)
                .build())
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("separator, quote, and escape characters must be different");
    }

    @Test
    void rejectsUndefinedSeparatorCharacter() {
        assertThatThrownBy(() -> new CSVParserBuilder()
                .withSeparator(ICSVParser.NULL_CHARACTER)
                .withErrorLocale(Locale.US)
                .build())
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("separator character must be defined");
    }

    @Test
    void reportsUnterminatedQuotedField() {
        CSVParser parser = new CSVParserBuilder()
                .withErrorLocale(Locale.US)
                .build();

        assertThatThrownBy(() -> parser.parseLine("\"unterminated"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Unterminated quoted field")
                .hasMessageContaining("unterminated");
    }
}
