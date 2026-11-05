/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_univocity.univocity_parsers;

import com.univocity.parsers.common.processor.ObjectRowListProcessor;
import com.univocity.parsers.conversions.Conversions;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the field-conversion subsystem to normalize and type a fulfillment feed.
 */
public class CsvFulfillmentConversionTest {
    @Test
    @Timeout(60)
    public void normalizesFulfillmentRowsForOrderProcessing() {
        ObjectRowListProcessor processor = new ObjectRowListProcessor();
        processor.convertFields(Conversions.trim()).set("sku", "status", "quantity");
        processor.convertFields(Conversions.toUpperCase()).set("status");
        processor.convertFields(Conversions.toInteger()).set("quantity");

        CsvParserSettings settings = new CsvParserSettings();
        settings.setHeaderExtractionEnabled(true);
        settings.setProcessor(processor);
        CsvParser parser = new CsvParser(settings);

        String fulfillmentFeed = "sku,status,quantity\n"
                + " SKU-1042" + " " + ", shipped" + " " + ", 18 \n"
                + " SKU-1043" + " " + ", backorder" + " " + ", 7 \n";
        parser.parse(new StringReader(fulfillmentFeed));

        List<Object[]> rows = processor.getRows();
        assertEquals(2, rows.size());
        assertEquals("SKU-1042", rows.get(0)[0]);
        assertEquals("SHIPPED", rows.get(0)[1]);
        assertEquals(18, rows.get(0)[2]);
        assertEquals("SKU-1043", rows.get(1)[0]);
        assertEquals("BACKORDER", rows.get(1)[1]);
        assertEquals(7, rows.get(1)[2]);
    }
}
