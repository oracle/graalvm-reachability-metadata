/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.ICSVParser;
import java.io.IOException;
import java.util.Locale;
import org.junit.jupiter.api.Test;

public class CSVParserTest {
    @Test
    void builderRejectsMatchingSeparatorAndQuoteCharacters() {
        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                () -> new CSVParserBuilder()
                        .withSeparator(',')
                        .withQuoteChar(',')
                        .withErrorLocale(Locale.ROOT)
                        .build());

        assertThat(exception).hasMessageContaining("separator, quote, and escape");
    }

    @Test
    void builderRejectsNullSeparatorCharacter() {
        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                () -> new CSVParserBuilder()
                        .withSeparator(ICSVParser.NULL_CHARACTER)
                        .withErrorLocale(Locale.ROOT)
                        .build());

        assertThat(exception).hasMessageContaining("separator");
    }

    @Test
    void parseLineRejectsUnterminatedQuotedField() {
        CSVParser parser = new CSVParserBuilder()
                .withErrorLocale(Locale.ROOT)
                .build();

        IOException exception = assertThrows(IOException.class,
                () -> parser.parseLine("\"unterminated"));

        assertThat(exception).hasMessageContaining("Unterminated quoted field");
    }
}
