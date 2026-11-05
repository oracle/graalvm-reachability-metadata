/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_univocity.univocity_parsers;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the CSV format-detection subsystem to import a partner feed with an unknown delimiter.
 */
public class CsvFormatDetectionTest {
    @Test
    @Timeout(60)
    public void detectsSemicolonFormatBeforeImportingPartnerFeed() {
        CsvParserSettings settings = new CsvParserSettings();
        settings.detectFormatAutomatically(';', ',', '\t');
        settings.setHeaderExtractionEnabled(true);
        CsvParser parser = new CsvParser(settings);
        String partnerFeed = "partner_id;company;notes\n"
                + "P-1042;\"Ada, Lovelace Partners\";priority\n"
                + "P-1043;Grace Hopper Labs;\"review; required\"\n";

        List<String[]> partners = parser.parseAll(new StringReader(partnerFeed));

        assertEquals(2, partners.size());
        assertArrayEquals(new String[]{"P-1042", "Ada, Lovelace Partners", "priority"}, partners.get(0));
        assertArrayEquals(new String[]{"P-1043", "Grace Hopper Labs", "review; required"}, partners.get(1));
        assertEquals(';', parser.getDetectedFormat().getDelimiter());
        assertEquals('"', parser.getDetectedFormat().getQuote());
    }
}
