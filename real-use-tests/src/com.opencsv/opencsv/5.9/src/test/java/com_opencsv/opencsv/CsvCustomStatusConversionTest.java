/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.bean.AbstractBeanField;
import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvCustomBindByName;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises OpenCSV's custom bean-field conversion for importing carrier status codes.
public class CsvCustomStatusConversionTest {
    @Test
    @Timeout(120)
    public void convertsCarrierCodesIntoApplicationStatuses() {
        String incoming = "order_id,carrier,status_code\n"
                + "O-17,Northwind Parcel,D\n"
                + "O-23,Northwind Parcel,H\n"
                + "O-31,Coastal Freight,D\n";

        List<ShipmentStatus> statuses = new CsvToBeanBuilder<ShipmentStatus>(new StringReader(incoming))
                .withType(ShipmentStatus.class)
                .build()
                .parse();

        assertEquals(3, statuses.size());
        assertEquals("O-17", statuses.get(0).getOrderId());
        assertEquals("Northwind Parcel", statuses.get(0).getCarrier());
        assertEquals(DeliveryStage.DISPATCHED, statuses.get(0).getStage());
        assertEquals(DeliveryStage.HELD, statuses.get(1).getStage());
        assertEquals(DeliveryStage.DISPATCHED, statuses.get(2).getStage());
    }

    public static class ShipmentStatus {
        @CsvBindByName(column = "order_id", required = true)
        private String orderId;

        @CsvBindByName(column = "carrier", required = true)
        private String carrier;

        @CsvCustomBindByName(column = "status_code", required = true, converter = DeliveryStageField.class)
        private DeliveryStage stage;

        public ShipmentStatus() {
        }

        public String getOrderId() {
            return orderId;
        }

        public String getCarrier() {
            return carrier;
        }

        public DeliveryStage getStage() {
            return stage;
        }
    }

    public enum DeliveryStage {
        DISPATCHED,
        HELD
    }

    public static class DeliveryStageField extends AbstractBeanField<DeliveryStage, String> {
        private static final Map<String, DeliveryStage> CODES = Map.of(
                "D", DeliveryStage.DISPATCHED,
                "H", DeliveryStage.HELD);

        @Override
        protected Object convert(String value) throws CsvDataTypeMismatchException {
            DeliveryStage stage = CODES.get(value);
            if (stage == null) {
                throw new CsvDataTypeMismatchException(value, DeliveryStage.class,
                        "Unknown carrier status code");
            }
            return stage;
        }
    }
}
