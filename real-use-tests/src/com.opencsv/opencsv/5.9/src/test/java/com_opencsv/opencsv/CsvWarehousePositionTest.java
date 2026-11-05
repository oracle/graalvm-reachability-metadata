/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.bean.ColumnPositionMappingStrategy;
import com.opencsv.bean.ColumnPositionMappingStrategyBuilder;
import com.opencsv.bean.CsvBindByPosition;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises OpenCSV's positional bean mapping for importing a fixed-layout warehouse feed.
public class CsvWarehousePositionTest {
    @Test
    @Timeout(120)
    public void importsAndExportsFixedLayoutShipmentRows() throws Exception {
        String incoming = "O-17,SKU-RED,WH-A,3\n"
                + "O-23,SKU-BLUE,WH-B,1\n";
        ColumnPositionMappingStrategy<ShipmentLine> mappingStrategy = mappingStrategy();

        List<ShipmentLine> shipments = new CsvToBeanBuilder<ShipmentLine>(new StringReader(incoming))
                .withMappingStrategy(mappingStrategy)
                .build()
                .parse();

        assertEquals(2, shipments.size());
        assertEquals("O-17", shipments.get(0).getOrderId());
        assertEquals("SKU-RED", shipments.get(0).getSku());
        assertEquals("WH-A", shipments.get(0).getWarehouse());
        assertEquals(3, shipments.get(0).getQuantity());
        assertEquals("WH-B", shipments.get(1).getWarehouse());

        StringWriter exported = new StringWriter();
        StatefulBeanToCsv<ShipmentLine> writer = new StatefulBeanToCsvBuilder<ShipmentLine>(exported)
                .withMappingStrategy(mappingStrategy())
                .withLineEnd("\n")
                .build();
        writer.write(shipments);

        assertEquals("\"O-17\",\"SKU-RED\",\"WH-A\",\"3\"\n"
                + "\"O-23\",\"SKU-BLUE\",\"WH-B\",\"1\"\n", exported.toString());
    }

    private static ColumnPositionMappingStrategy<ShipmentLine> mappingStrategy() {
        ColumnPositionMappingStrategy<ShipmentLine> strategy =
                new ColumnPositionMappingStrategyBuilder<ShipmentLine>().build();
        strategy.setType(ShipmentLine.class);
        return strategy;
    }

    public static class ShipmentLine {
        @CsvBindByPosition(position = 0, required = true)
        private String orderId;

        @CsvBindByPosition(position = 1, required = true)
        private String sku;

        @CsvBindByPosition(position = 2, required = true)
        private String warehouse;

        @CsvBindByPosition(position = 3, required = true)
        private int quantity;

        public ShipmentLine() {
        }

        public String getOrderId() {
            return orderId;
        }

        public String getSku() {
            return sku;
        }

        public String getWarehouse() {
            return warehouse;
        }

        public int getQuantity() {
            return quantity;
        }
    }
}
