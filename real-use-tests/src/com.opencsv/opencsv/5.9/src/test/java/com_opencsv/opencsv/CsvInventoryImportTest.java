/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises OpenCSV's annotated bean mapping for importing and exporting inventory rows.
public class CsvInventoryImportTest {
    @Test
    @Timeout(120)
    public void mapsInventoryRowsIntoDomainBeansAndBackToCsv() throws Exception {
        String incoming = """
                sku,description,quantity
                A-17,Mechanical keyboard,12
                B-04,USB-C cable,35
                """;

        List<InventoryLine> inventory = new CsvToBeanBuilder<InventoryLine>(new StringReader(incoming))
                .withType(InventoryLine.class)
                .withIgnoreLeadingWhiteSpace(true)
                .build()
                .parse();

        assertEquals(2, inventory.size());
        assertEquals("A-17", inventory.get(0).getSku());
        assertEquals("Mechanical keyboard", inventory.get(0).getDescription());
        assertEquals(12, inventory.get(0).getQuantity());
        assertEquals(35, inventory.get(1).getQuantity());

        StringWriter exported = new StringWriter();
        StatefulBeanToCsv<InventoryLine> writer = new StatefulBeanToCsvBuilder<InventoryLine>(exported)
                .withLineEnd("\n")
                .build();
        writer.write(inventory);

        assertEquals("\"DESCRIPTION\",\"QUANTITY\",\"SKU\"\n"
                + "\"Mechanical keyboard\",\"12\",\"A-17\"\n"
                + "\"USB-C cable\",\"35\",\"B-04\"\n", exported.toString());
    }

    public static class InventoryLine {
        @CsvBindByName(column = "sku", required = true)
        private String sku;

        @CsvBindByName(column = "description", required = true)
        private String description;

        @CsvBindByName(column = "quantity", required = true)
        private int quantity;

        public InventoryLine() {
        }

        public String getSku() {
            return sku;
        }

        public String getDescription() {
            return description;
        }

        public int getQuantity() {
            return quantity;
        }
    }
}
