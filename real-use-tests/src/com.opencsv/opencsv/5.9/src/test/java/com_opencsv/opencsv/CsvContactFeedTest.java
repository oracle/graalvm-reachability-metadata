/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReaderHeaderAware;
import com.opencsv.CSVReaderHeaderAwareBuilder;
import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/// Exercises OpenCSV's header-aware parsing and configurable writing for a contact feed.
public class CsvContactFeedTest {
    @Test
    @Timeout(120)
    public void importsDelimitedContactsAndExportsReviewReport() throws Exception {
        String incoming = """
                contact_id;name;notes
                c-17;Ada Lovelace;"Prefers email; responds in morning"
                c-23;Grace Hopper;"Asked for a CSV guide"
                """;
        List<String[]> reportRows = new ArrayList<>();

        try (CSVReaderHeaderAware reader = new CSVReaderHeaderAwareBuilder(new StringReader(incoming))
                .withCSVParser(new CSVParserBuilder().withSeparator(';').build())
                .build()) {
            Map<String, String> firstContact = reader.readMap();
            reportRows.add(new String[]{firstContact.get("contact_id"), firstContact.get("name"),
                    firstContact.get("notes")});

            Map<String, String> secondContact = reader.readMap();
            reportRows.add(new String[]{secondContact.get("contact_id"), secondContact.get("name"),
                    secondContact.get("notes")});
            assertEquals(null, reader.readMap());
        }

        StringWriter report = new StringWriter();
        try (ICSVWriter writer = new CSVWriterBuilder(report).withLineEnd("\n").build()) {
            writer.writeNext(new String[]{"contact_id", "name", "notes"});
            writer.writeAll(reportRows);
            assertFalse(writer.checkError());
        }

        assertEquals("""
                "contact_id","name","notes"
                "c-17","Ada Lovelace","Prefers email; responds in morning"
                "c-23","Grace Hopper","Asked for a CSV guide"
                """, report.toString());
    }
}
