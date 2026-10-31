/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.RFC4180Parser;
import com.opencsv.RFC4180ParserBuilder;
import com.opencsv.enums.CSVReaderNullFieldIndicator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class RFC4180ParserTest {
    @Test
    void parsesDoubledQuotesAndPreservesBackslashesWhitespaceAndTrailingFields() throws Exception {
        RFC4180Parser parser = new RFC4180Parser();

        assertThat(parser.parseLine("\"Doe, Jane\",\"said \"\"hello\"\"\",C:\\reports,  padded  ,"))
                .containsExactly("Doe, Jane", "said \"hello\"", "C:\\reports", "  padded  ", "");
    }

    @Test
    void convertsQuotedAndUnquotedEmptyFieldsToNullWhenConfigured() throws Exception {
        RFC4180Parser parser = new RFC4180ParserBuilder()
                .withFieldAsNull(CSVReaderNullFieldIndicator.BOTH)
                .build();

        assertThat(parser.parseLine("alpha,,\"\",omega,"))
                .containsExactly("alpha", null, null, "omega", null);
    }

    @Test
    void readsMultilineQuotedRecordsThroughCsvReader() throws Exception {
        String csv = "id,note\r\n7,\"first\r\nsecond with \"\"quotes\"\"\"\r\n";
        try (CSVReader reader = new CSVReaderBuilder(new StringReader(csv))
                .withCSVParser(new RFC4180Parser())
                .build()) {
            assertThat(reader.readNext()).containsExactly("id", "note");
            assertThat(reader.readNext()).containsExactly("7", "first\nsecond with \"quotes\"");
            assertThat(reader.readNext()).isNull();
        }
    }
}
