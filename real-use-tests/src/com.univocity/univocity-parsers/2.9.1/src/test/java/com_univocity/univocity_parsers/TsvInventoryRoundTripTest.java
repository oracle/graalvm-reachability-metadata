/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_univocity.univocity_parsers;

import com.univocity.parsers.tsv.TsvParser;
import com.univocity.parsers.tsv.TsvParserSettings;
import com.univocity.parsers.tsv.TsvWriter;
import com.univocity.parsers.tsv.TsvWriterSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the TSV parsing and writing subsystem to round-trip a stock feed.
 */
public class TsvInventoryRoundTripTest {
    @Test
    @Timeout(60)
    public void roundTripsInventoryRowsWithTabAndNewlineContent() {
        StringWriter output = new StringWriter();
        TsvWriterSettings writerSettings = new TsvWriterSettings();
        TsvWriter writer = new TsvWriter(output, writerSettings);
        writer.writeHeaders("sku", "description", "quantity");
        writer.writeRow("SKU-1042", "Laptop stand", "18");
        writer.writeRow("SKU-1043", "Keyboard\tcompact\nwireless", "7");
        writer.close();

        TsvParserSettings parserSettings = new TsvParserSettings();
        parserSettings.setHeaderExtractionEnabled(true);
        TsvParser parser = new TsvParser(parserSettings);
        List<String[]> rows = parser.parseAll(new StringReader(output.toString()));

        assertEquals(2, rows.size());
        assertEquals("SKU-1042", rows.get(0)[0]);
        assertEquals("Laptop stand", rows.get(0)[1]);
        assertEquals("18", rows.get(0)[2]);
        assertEquals("SKU-1043", rows.get(1)[0]);
        assertEquals("Keyboard\tcompact\nwireless", rows.get(1)[1]);
        assertEquals("7", rows.get(1)[2]);
    }
}
