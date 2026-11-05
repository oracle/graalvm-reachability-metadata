/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.processor.ConvertEmptyOrBlankStringsToDefault;
import com.opencsv.bean.processor.PreAssignmentProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises OpenCSV's bean pre-assignment processing for normalizing optional delivery notes.
public class CsvBeanPreprocessingTest {
    @Test
    @Timeout(120)
    public void importsManifestRowsWithAnApplicationDefaultForMissingNotes() {
        String incoming = "shipment_id,carrier,delivery_note\n"
                + "S-17,Northwind Parcel,Leave at loading dock\n"
                + "S-23,Coastal Freight,   \n"
                + "S-31,Northwind Parcel,Call receiving desk\n";

        List<ShipmentManifest> manifests = new CsvToBeanBuilder<ShipmentManifest>(
                new StringReader(incoming))
                .withType(ShipmentManifest.class)
                .build()
                .parse();

        assertEquals(3, manifests.size());
        assertEquals("S-17", manifests.get(0).getShipmentId());
        assertEquals("Northwind Parcel", manifests.get(0).getCarrier());
        assertEquals("Leave at loading dock", manifests.get(0).getDeliveryNote());
        assertEquals("not-provided", manifests.get(1).getDeliveryNote());
        assertEquals("Call receiving desk", manifests.get(2).getDeliveryNote());
    }

    public static class ShipmentManifest {
        @CsvBindByName(column = "shipment_id", required = true)
        private String shipmentId;

        @CsvBindByName(column = "carrier", required = true)
        private String carrier;

        @PreAssignmentProcessor(
                processor = ConvertEmptyOrBlankStringsToDefault.class,
                paramString = "not-provided")
        @CsvBindByName(column = "delivery_note", required = false)
        private String deliveryNote;

        public ShipmentManifest() {
        }

        public String getShipmentId() {
            return shipmentId;
        }

        public String getCarrier() {
            return carrier;
        }

        public String getDeliveryNote() {
            return deliveryNote;
        }
    }
}
