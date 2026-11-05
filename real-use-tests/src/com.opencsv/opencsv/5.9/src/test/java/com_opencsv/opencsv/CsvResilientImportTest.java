/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.exceptions.CsvException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises OpenCSV's bean exception queue for importing a batch while reporting malformed rows.
public class CsvResilientImportTest {
    @Test
    @Timeout(120)
    public void importsValidOrdersAndReportsMalformedRows() {
        String incoming = "order_id,sku,quantity\n"
                + "O-17,SKU-RED,3\n"
                + "O-23,SKU-BLUE,not-a-number\n"
                + "O-31,SKU-GREEN,8\n";

        CsvToBean<OrderLine> importer = new CsvToBeanBuilder<OrderLine>(new StringReader(incoming))
                .withType(OrderLine.class)
                .withThrowExceptions(false)
                .build();
        List<OrderLine> imported = importer.parse();
        List<CsvException> rejected = importer.getCapturedExceptions();

        assertEquals(2, imported.size());
        assertEquals("O-17", imported.get(0).getOrderId());
        assertEquals("SKU-RED", imported.get(0).getSku());
        assertEquals(3, imported.get(0).getQuantity());
        assertEquals("O-31", imported.get(1).getOrderId());
        assertEquals(8, imported.get(1).getQuantity());

        assertEquals(1, rejected.size());
        assertEquals(3, rejected.get(0).getLineNumber());
        assertArrayEquals(new String[]{"O-23", "SKU-BLUE", "not-a-number"}, rejected.get(0).getLine());
    }

    public static class OrderLine {
        @CsvBindByName(column = "order_id", required = true)
        private String orderId;

        @CsvBindByName(column = "sku", required = true)
        private String sku;

        @CsvBindByName(column = "quantity", required = true)
        private int quantity;

        public OrderLine() {
        }

        public String getOrderId() {
            return orderId;
        }

        public String getSku() {
            return sku;
        }

        public int getQuantity() {
            return quantity;
        }
    }
}
