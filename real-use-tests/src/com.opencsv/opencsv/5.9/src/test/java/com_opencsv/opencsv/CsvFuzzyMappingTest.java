/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.FuzzyMappingStrategy;
import com.opencsv.bean.FuzzyMappingStrategyBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises OpenCSV's fuzzy bean mapping for importing a carrier shipment feed with near-matching headers.
public class CsvFuzzyMappingTest {
    @Test
    @Timeout(120)
    public void mapsCarrierHeadersToShipmentFields() {
        String incoming = "customer_name,order_nuber,total_cents\n"
                + "Ada Lovelace,ORD-17,12500\n"
                + "Grace Hopper,ORD-23,7800\n";

        FuzzyMappingStrategy<Shipment> mappingStrategy =
                new FuzzyMappingStrategyBuilder<Shipment>().build();
        mappingStrategy.setType(Shipment.class);

        List<Shipment> shipments = new CsvToBeanBuilder<Shipment>(new StringReader(incoming))
                .withMappingStrategy(mappingStrategy)
                .build()
                .parse();

        assertEquals(2, shipments.size());
        assertEquals("Ada Lovelace", shipments.get(0).getCustomerName());
        assertEquals("ORD-17", shipments.get(0).getOrderNumber());
        assertEquals(12500, shipments.get(0).getTotalCents());
        assertEquals("Grace Hopper", shipments.get(1).getCustomerName());
        assertEquals("ORD-23", shipments.get(1).getOrderNumber());
        assertEquals(7800, shipments.get(1).getTotalCents());
    }

    public static class Shipment {
        private String customerName;
        private String orderNumber;
        private int totalCents;

        public Shipment() {
        }

        public String getCustomerName() {
            return customerName;
        }

        public String getOrderNumber() {
            return orderNumber;
        }

        public int getTotalCents() {
            return totalCents;
        }
    }
}
