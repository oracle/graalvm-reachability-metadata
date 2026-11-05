/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.enums.CSVReaderNullFieldIndicator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/// Exercises OpenCSV's configurable low-level parser for nullable, multiline telemetry records.
public class CsvTelemetryParserTest {
    @Test
    @Timeout(120)
    public void parsesPipeDelimitedTelemetryWithNullsAndMultilineNotes() throws IOException {
        CSVParser parser = new CSVParserBuilder()
                .withSeparator('|')
                .withFieldAsNull(CSVReaderNullFieldIndicator.BOTH)
                .build();

        String[] reading = parser.parseLine(
                "sensor-17||\"\"|\"Loading|Dock\"|\"Battery \"\"low\"\"\"");
        assertArrayEquals(new String[]{"sensor-17", null, null, "Loading|Dock", "Battery \"low\""},
                reading);

        String[] note = parser.parseLineMulti("sensor-17|\"Line one\nLine two\"|ready");
        assertArrayEquals(new String[]{"sensor-17", "Line one\nLine two", "ready"}, note);
        assertFalse(parser.isPending());
    }
}
