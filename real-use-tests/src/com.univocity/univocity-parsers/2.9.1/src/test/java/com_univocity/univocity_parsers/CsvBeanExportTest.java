/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_univocity.univocity_parsers;

import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.processor.BeanWriterProcessor;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the CSV bean-writing subsystem to export an order report from domain objects.
 */
public class CsvBeanExportTest {
    @Test
    @Timeout(60)
    public void exportsAnnotatedOrdersWithHeadersAndQuotedDescriptions() {
        StringWriter output = new StringWriter();
        CsvWriterSettings settings = new CsvWriterSettings();
        settings.setRowWriterProcessor(new BeanWriterProcessor<>(Order.class));
        settings.setQuoteAllFields(true);
        CsvWriter writer = new CsvWriter(output, settings);

        writer.writeHeaders();
        writer.processRecord(new Order("ORD-1042", "Ada Lovelace", "Priority, overnight", 2));
        writer.processRecord(new Order("ORD-1043", "Grace Hopper", "Compiler workshop", 1));
        writer.close();

        assertEquals(List.of(
                "\"order_id\",\"customer\",\"description\",\"units\"",
                "\"ORD-1042\",\"Ada Lovelace\",\"Priority, overnight\",\"2\"",
                "\"ORD-1043\",\"Grace Hopper\",\"Compiler workshop\",\"1\""),
                output.toString().lines().toList());
    }

    public static class Order {
        @Parsed(field = "order_id")
        private String orderId;

        @Parsed(field = "customer")
        private String customer;

        @Parsed(field = "description")
        private String description;

        @Parsed(field = "units")
        private int units;

        public Order(String orderId, String customer, String description, int units) {
            this.orderId = orderId;
            this.customer = customer;
            this.description = description;
            this.units = units;
        }

        public String getOrderId() {
            return orderId;
        }

        public String getCustomer() {
            return customer;
        }

        public String getDescription() {
            return description;
        }

        public int getUnits() {
            return units;
        }
    }
}
