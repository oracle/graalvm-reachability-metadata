/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.CSVWriter;
import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringWriter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class CSVWriterTest {
    @Test
    void quotesAndEscapesSpecialFieldsWithoutQuotingPlainFields() throws Exception {
        StringWriter output = new StringWriter();
        try (CSVWriter writer = new CSVWriter(output)) {
            writer.writeNext(new String[]{"plain", "left,right", "say \"hello\"", "first\nsecond", null, ""}, false);
            writer.writeNext(new String[]{"tail", "done"});

            assertThat(writer.checkError()).isFalse();
            assertThat(writer.getException()).isNull();
        }

        assertThat(output.toString()).isEqualTo("""
                plain,"left,right","say ""hello""\","first
                second",,
                "tail","done"
                """);
    }

    @Test
    void writesBatchesWithConfiguredDelimiterQuotesEscapeAndLineEnding() throws Exception {
        StringWriter output = new StringWriter();
        try (ICSVWriter writer = new CSVWriterBuilder(output)
                .withSeparator(';')
                .withQuoteChar('\'')
                .withEscapeChar('\\')
                .withLineEnd(ICSVWriter.RFC4180_LINE_END)
                .build()) {
            List<String[]> rows = List.of(
                    new String[]{"name", "note", "path"},
                    new String[]{"O'Reilly", "one;two", "folder\\file"});
            writer.writeAll(rows, false);
            assertThat(writer.checkError()).isFalse();
        }

        assertThat(output.toString()).isEqualTo(
                "name;note;path\r\n'O\\'Reilly';'one;two';'folder\\\\file'\r\n");
    }
}
