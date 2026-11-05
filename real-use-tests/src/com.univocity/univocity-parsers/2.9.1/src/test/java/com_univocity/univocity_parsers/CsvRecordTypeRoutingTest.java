/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_univocity.univocity_parsers;

import com.univocity.parsers.common.processor.InputValueSwitch;
import com.univocity.parsers.common.processor.ObjectRowListProcessor;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the record-type routing subsystem to separate orders and returns from an operations feed.
 */
public class CsvRecordTypeRoutingTest {
    @Test
    @Timeout(60)
    public void routesRowsToProcessorsSelectedByRecordType() {
        ObjectRowListProcessor orderProcessor = new ObjectRowListProcessor();
        ObjectRowListProcessor returnProcessor = new ObjectRowListProcessor();
        InputValueSwitch processorSwitch = new InputValueSwitch("record_type");
        processorSwitch.addSwitchForValue("ORDER", orderProcessor);
        processorSwitch.addSwitchForValue("RETURN", returnProcessor);

        CsvParserSettings settings = new CsvParserSettings();
        settings.setHeaderExtractionEnabled(true);
        settings.setProcessor(processorSwitch);
        CsvParser parser = new CsvParser(settings);
        String operationsFeed = "record_type,reference,sku,quantity\n"
                + "ORDER,ORD-1042,SKU-17,2\n"
                + "RETURN,RET-1043,SKU-23,1\n"
                + "ORDER,ORD-1044,SKU-41,4\n";

        parser.parse(new StringReader(operationsFeed));

        List<Object[]> orders = orderProcessor.getRows();
        List<Object[]> returns = returnProcessor.getRows();
        assertEquals(2, orders.size());
        assertEquals(1, returns.size());
        assertEquals("ORDER", orders.get(0)[0]);
        assertEquals("ORD-1042", orders.get(0)[1]);
        assertEquals("SKU-41", orders.get(1)[2]);
        assertEquals("RETURN", returns.get(0)[0]);
        assertEquals("RET-1043", returns.get(0)[1]);
        assertEquals("1", returns.get(0)[3]);
    }
}
