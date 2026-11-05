/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_univocity.univocity_parsers;

import com.univocity.parsers.common.processor.ConcurrentRowProcessor;
import com.univocity.parsers.common.processor.ObjectRowListProcessor;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the concurrent row-processing subsystem to ingest a batch of shipments.
 */
public class CsvConcurrentProcessingTest {
    @Test
    @Timeout(60)
    public void collectsEveryShipmentWhenRowsAreProcessedConcurrently() {
        ObjectRowListProcessor collectedRows = new ObjectRowListProcessor();
        ConcurrentRowProcessor concurrentProcessor = new ConcurrentRowProcessor(collectedRows, 2);

        CsvParserSettings settings = new CsvParserSettings();
        settings.setHeaderExtractionEnabled(true);
        settings.setProcessor(concurrentProcessor);
        CsvParser parser = new CsvParser(settings);
        String shipmentFeed = "shipment_id,warehouse,packages\n"
                + "SHP-1001,BER-01,4\n"
                + "SHP-1002,AMS-02,2\n"
                + "SHP-1003,NYC-03,7\n"
                + "SHP-1004,SIN-04,1\n"
                + "SHP-1005,BER-01,3\n"
                + "SHP-1006,AMS-02,6\n"
                + "SHP-1007,NYC-03,5\n"
                + "SHP-1008,SIN-04,8\n";

        parser.parse(new StringReader(shipmentFeed));

        List<Object[]> rows = collectedRows.getRows();
        List<String> shipmentIds = new ArrayList<>();
        int packageCount = 0;
        for (Object[] row : rows) {
            shipmentIds.add((String) row[0]);
            packageCount += Integer.parseInt((String) row[2]);
        }
        shipmentIds.sort(Comparator.naturalOrder());

        assertEquals(List.of(
                "SHP-1001", "SHP-1002", "SHP-1003", "SHP-1004",
                "SHP-1005", "SHP-1006", "SHP-1007", "SHP-1008"), shipmentIds);
        assertEquals(36, packageCount);
    }
}
