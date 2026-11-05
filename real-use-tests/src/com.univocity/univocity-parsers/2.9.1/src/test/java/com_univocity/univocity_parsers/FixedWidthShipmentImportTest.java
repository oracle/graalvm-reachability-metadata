/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_univocity.univocity_parsers;

import com.univocity.parsers.fixed.FixedWidthFields;
import com.univocity.parsers.fixed.FixedWidthParser;
import com.univocity.parsers.fixed.FixedWidthParserSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the fixed-width parsing subsystem to import a warehouse shipment feed.
 */
public class FixedWidthShipmentImportTest {
    @Test
    @Timeout(60)
    public void importsShipmentRowsWithPaddedIdentifiersAndQuantities() {
        FixedWidthFields fields = new FixedWidthFields(8, 20, 5);
        FixedWidthParserSettings settings = new FixedWidthParserSettings(fields);
        settings.setRecordEndsOnNewline(true);
        FixedWidthParser parser = new FixedWidthParser(settings);
        String shipmentFeed = String.format(Locale.ROOT,
                "%-8s%-20s%05d\n%-8s%-20s%05d\n",
                "SHP-1042", "Ada Lovelace", 120,
                "SHP-1043", "Grace Hopper", 85);

        List<String[]> shipments = parser.parseAll(new StringReader(shipmentFeed));

        assertEquals(2, shipments.size());
        assertEquals("SHP-1042", shipments.get(0)[0]);
        assertEquals("Ada Lovelace", shipments.get(0)[1]);
        assertEquals("00120", shipments.get(0)[2]);
        assertEquals("SHP-1043", shipments.get(1)[0]);
        assertEquals("Grace Hopper", shipments.get(1)[1]);
        assertEquals("00085", shipments.get(1)[2]);
    }
}
