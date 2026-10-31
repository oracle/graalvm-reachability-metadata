/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;
import com.opencsv.RFC4180Parser;
import com.opencsv.RFC4180ParserBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class CSVParserWriterTest {
    @Test
    void sharesParserSettingsBetweenWritingAndReading() throws Exception {
        RFC4180Parser parser = new RFC4180ParserBuilder().withSeparator(';').build();
        StringWriter output = new StringWriter();
        List<String[]> rows = List.of(
                new String[]{"name", "note", "path"},
                new String[]{"Ada", "two;parts and \"quotes\"", "C:\\reports"});
        try (ICSVWriter writer = new CSVWriterBuilder(output)
                .withParser(parser)
                .withLineEnd(ICSVWriter.RFC4180_LINE_END)
                .build()) {
            writer.writeAll(rows, false);
            assertThat(writer.checkError()).isFalse();
        }

        assertThat(output.toString()).isEqualTo(
                "name;note;path\r\nAda;\"two;parts and \"\"quotes\"\"\";C:\\reports\r\n");
        try (CSVReader reader = new CSVReaderBuilder(new StringReader(output.toString()))
                .withCSVParser(parser).build()) {
            assertThat(reader.readNext()).containsExactly(rows.get(0));
            assertThat(reader.readNext()).containsExactly(rows.get(1));
            assertThat(reader.readNext()).isNull();
        }
    }

    @Test
    void appliesQuotesToEveryFieldWhenRequested() throws Exception {
        StringWriter output = new StringWriter();
        try (ICSVWriter writer = new CSVWriterBuilder(output)
                .withParser(new RFC4180Parser())
                .build()) {
            writer.writeNext(new String[]{"plain", "", "say \"yes\""}, true);
            assertThat(writer.checkError()).isFalse();
        }

        assertThat(output.toString()).isEqualTo("\"plain\",\"\",\"say \"\"yes\"\"\"\n");
    }
}
