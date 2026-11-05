/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_univocity.univocity_parsers;

import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.annotations.Validate;
import com.univocity.parsers.common.DataValidationException;
import com.univocity.parsers.csv.CsvParserSettings;
import com.univocity.parsers.csv.CsvRoutines;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises the CSV field-validation subsystem to protect an order intake feed.
 */
public class CsvValidatedOrderImportTest {
    @Test
    @Timeout(60)
    public void importsValidOrdersAndRejectsInvalidOrderCodes() {
        CsvParserSettings settings = new CsvParserSettings();
        settings.setHeaderExtractionEnabled(true);
        CsvRoutines routines = new CsvRoutines(settings);
        String validOrders = "order_id,order_code,status\n"
                + "O-1001,AB-2048,PAID\n"
                + "O-1002,ZX-7310,PENDING\n";

        List<Order> orders = routines.parseAll(Order.class, new StringReader(validOrders));

        assertEquals(2, orders.size());
        assertEquals("AB-2048", orders.get(0).getOrderCode());
        assertEquals("PAID", orders.get(0).getStatus());
        assertEquals("ZX-7310", orders.get(1).getOrderCode());

        String invalidOrder = "order_id,order_code,status\n"
                + "O-1003,not-an-order,PAID\n";
        DataValidationException error = assertThrows(DataValidationException.class,
                () -> routines.parseAll(Order.class, new StringReader(invalidOrder)));

        assertEquals("order_code", error.getColumnName());
    }

    public static class Order {
        @Parsed(field = "order_id")
        private String orderId;

        @Parsed(field = "order_code")
        @Validate(matches = "[A-Z]{2}-[0-9]{4}")
        private String orderCode;

        @Parsed(field = "status")
        @Validate(oneOf = {"PAID", "PENDING"})
        private String status;

        public String getOrderId() {
            return orderId;
        }

        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        public String getOrderCode() {
            return orderCode;
        }

        public void setOrderCode(String orderCode) {
            this.orderCode = orderCode;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
