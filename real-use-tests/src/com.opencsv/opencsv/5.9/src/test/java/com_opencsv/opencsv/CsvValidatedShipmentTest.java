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
import com.opencsv.bean.validators.MustMatchRegexExpression;
import com.opencsv.bean.validators.PreAssignmentValidator;
import com.opencsv.exceptions.CsvException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises OpenCSV's bean-field validation for accepting shipment records with valid identifiers.
public class CsvValidatedShipmentTest {
    @Test
    @Timeout(120)
    public void importsValidShipmentsAndCapturesInvalidIdentifiers() {
        String incoming = "shipment_id,carrier,tracking_code\n"
                + "S-17,Northwind Parcel,NP123456\n"
                + "S-23,Coastal Freight,not-a-tracking-code\n"
                + "S-31,Northwind Parcel,NP654321\n";

        CsvToBean<Shipment> importer = new CsvToBeanBuilder<Shipment>(new StringReader(incoming))
                .withType(Shipment.class)
                .withThrowExceptions(false)
                .build();
        List<Shipment> shipments = importer.parse();
        List<CsvException> rejected = importer.getCapturedExceptions();

        assertEquals(2, shipments.size());
        assertEquals("S-17", shipments.get(0).getShipmentId());
        assertEquals("NP123456", shipments.get(0).getTrackingCode());
        assertEquals("S-31", shipments.get(1).getShipmentId());
        assertEquals("NP654321", shipments.get(1).getTrackingCode());
        assertEquals(1, rejected.size());
        assertTrue(rejected.get(0).getMessage().contains("did not match"),
                rejected.get(0).getMessage());
    }

    public static class Shipment {
        @CsvBindByName(column = "shipment_id", required = true)
        private String shipmentId;

        @CsvBindByName(column = "carrier", required = true)
        private String carrier;

        @PreAssignmentValidator(
                validator = MustMatchRegexExpression.class,
                paramString = "NP[0-9]{6}")
        @CsvBindByName(column = "tracking_code", required = true)
        private String trackingCode;

        public Shipment() {
        }

        public String getShipmentId() {
            return shipmentId;
        }

        public String getTrackingCode() {
            return trackingCode;
        }
    }
}
