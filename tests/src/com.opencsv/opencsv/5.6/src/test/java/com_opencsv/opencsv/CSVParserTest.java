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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CSVParserTest {

    @Test
    void buildRejectsDuplicateSpecialCharacters() {
        CSVParserBuilder builder = new CSVParserBuilder()
                .withSeparator(';')
                .withQuoteChar(';')
                .withErrorLocale(Locale.US);

        assertThatThrownBy(builder::build)
                .isInstanceOf(UnsupportedOperationException.class)
                .satisfies(exception -> assertThat(exception.getMessage()).isNotBlank());
    }

    @Test
    void buildRejectsUndefinedSeparator() {
        CSVParserBuilder builder = new CSVParserBuilder()
                .withSeparator(ICSVParser.NULL_CHARACTER)
                .withErrorLocale(Locale.US);

        assertThatThrownBy(builder::build)
                .isInstanceOf(UnsupportedOperationException.class)
                .satisfies(exception -> assertThat(exception.getMessage()).isNotBlank());
    }

    @Test
    void parseLineReportsUnterminatedQuotedField() {
        CSVParser parser = new CSVParserBuilder()
                .withErrorLocale(Locale.US)
                .build();

        assertThatThrownBy(() -> parser.parseLine("\"unterminated"))
                .isInstanceOf(IOException.class)
                .satisfies(exception -> assertThat(exception.getMessage()).isNotBlank());
    }
}
