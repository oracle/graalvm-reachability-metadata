/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_univocity.univocity_parsers;

import com.univocity.parsers.common.ParsingContext;
import com.univocity.parsers.common.processor.MasterDetailListProcessor;
import com.univocity.parsers.common.processor.MasterDetailRecord;
import com.univocity.parsers.common.processor.ObjectRowListProcessor;
import com.univocity.parsers.common.processor.RowPlacement;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the master-detail processing subsystem to import orders with their line items.
 */
public class OrderMasterDetailImportTest {
    @Test
    @Timeout(60)
    public void groupsOrderRowsWithFollowingLineItems() {
        ObjectRowListProcessor detailProcessor = new ObjectRowListProcessor();
        MasterDetailListProcessor orderProcessor = new MasterDetailListProcessor(
                RowPlacement.TOP, detailProcessor) {
            @Override
            protected boolean isMasterRecord(String[] row, ParsingContext context) {
                return "ORDER".equals(row[0]);
            }
        };

        CsvParserSettings settings = new CsvParserSettings();
        settings.setHeaderExtractionEnabled(true);
        settings.setProcessor(orderProcessor);
        CsvParser parser = new CsvParser(settings);
        String orderFeed = "record_type,order_id,customer,sku,quantity\n"
                + "ORDER,ORD-1042,Ada Lovelace,,\n"
                + "ITEM,ORD-1042,,SKU-17,2\n"
                + "ITEM,ORD-1042,,SKU-23,1\n"
                + "ORDER,ORD-1043,Grace Hopper,,\n"
                + "ITEM,ORD-1043,,SKU-41,4\n";

        parser.parse(new StringReader(orderFeed));

        List<MasterDetailRecord> orders = orderProcessor.getRecords();
        assertEquals(2, orders.size());
        assertEquals("ORDER", orders.get(0).getMasterRow()[0]);
        assertEquals("ORD-1042", orders.get(0).getMasterRow()[1]);
        assertEquals("Ada Lovelace", orders.get(0).getMasterRow()[2]);
        assertEquals(2, orders.get(0).getDetailRows().size());
        assertEquals("SKU-17", orders.get(0).getDetailRows().get(0)[3]);
        assertEquals("2", orders.get(0).getDetailRows().get(0)[4]);
        assertEquals("SKU-23", orders.get(0).getDetailRows().get(1)[3]);
        assertEquals("ORDER", orders.get(1).getMasterRow()[0]);
        assertEquals("ORD-1043", orders.get(1).getMasterRow()[1]);
        assertEquals(1, orders.get(1).getDetailRows().size());
        assertEquals("SKU-41", orders.get(1).getDetailRows().get(0)[3]);
    }
}
