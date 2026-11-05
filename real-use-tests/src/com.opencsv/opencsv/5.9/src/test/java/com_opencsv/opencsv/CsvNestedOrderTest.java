/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvRecurse;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises OpenCSV's recursive bean mapping for importing and exporting order shipping details.
public class CsvNestedOrderTest {
    @Test
    @Timeout(120)
    public void mapsNestedShippingAddressAndExportsFulfillmentRows() throws Exception {
        String incoming = "order_id,customer,street,city,postal_code\n"
                + "O-17,Ada Lovelace,1 Analytical Engine Way,London,N1 9GU\n"
                + "O-23,Grace Hopper,101 Navy Street,Arlington,22201\n";

        List<Order> orders = new CsvToBeanBuilder<Order>(new StringReader(incoming))
                .withType(Order.class)
                .build()
                .parse();

        assertEquals(2, orders.size());
        assertEquals("O-17", orders.get(0).getOrderId());
        assertEquals("Ada Lovelace", orders.get(0).getCustomer());
        assertEquals("1 Analytical Engine Way", orders.get(0).getShipping().getStreet());
        assertEquals("London", orders.get(0).getShipping().getCity());
        assertEquals("N1 9GU", orders.get(0).getShipping().getPostalCode());
        assertEquals("22201", orders.get(1).getShipping().getPostalCode());

        StringWriter exported = new StringWriter();
        StatefulBeanToCsv<Order> writer = new StatefulBeanToCsvBuilder<Order>(exported)
                .withLineEnd("\n")
                .build();
        writer.write(orders);

        assertEquals("\"CITY\",\"CUSTOMER\",\"ORDER_ID\",\"POSTAL_CODE\",\"STREET\"\n"
                + "\"London\",\"Ada Lovelace\",\"O-17\",\"N1 9GU\",\"1 Analytical Engine Way\"\n"
                + "\"Arlington\",\"Grace Hopper\",\"O-23\",\"22201\",\"101 Navy Street\"\n",
                exported.toString());
    }

    public static class Order {
        @CsvBindByName(column = "order_id", required = true)
        private String orderId;

        @CsvBindByName(column = "customer", required = true)
        private String customer;

        @CsvRecurse
        private ShippingAddress shipping;

        public Order() {
        }

        public String getOrderId() {
            return orderId;
        }

        public String getCustomer() {
            return customer;
        }

        public ShippingAddress getShipping() {
            return shipping;
        }
    }

    public static class ShippingAddress {
        @CsvBindByName(column = "street", required = true)
        private String street;

        @CsvBindByName(column = "city", required = true)
        private String city;

        @CsvBindByName(column = "postal_code", required = true)
        private String postalCode;

        public ShippingAddress() {
        }

        public String getStreet() {
            return street;
        }

        public String getCity() {
            return city;
        }

        public String getPostalCode() {
            return postalCode;
        }
    }
}
