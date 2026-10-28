/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises Gson's generic object data binding to import a supplier order page and prepare an invoice.
public class SupplierOrderBindingTest {

    @Test
    @Timeout(60)
    public void importsTypedOrdersAndExportsAnInvoicedOrder() {
        Gson gson = new Gson();
        Type pageType = new TypeToken<Page<Order>>() { }.getType();
        Page<Order> page = gson.fromJson("""
                {
                  "entries": [
                    {
                      "orderId": "PO-184",
                      "customer": "Corner Shop",
                      "invoiced": false,
                      "lines": [
                        {"sku": "notebook", "quantity": 3, "unitPrice": 4.25},
                        {"sku": "pencil", "quantity": 5, "unitPrice": 0.80}
                      ],
                      "supplierExtension": {"warehouse": "north"}
                    }
                  ],
                  "nextCursor": "page-two"
                }
                """, pageType);

        assertEquals("page-two", page.nextCursor);
        assertEquals(1, page.entries.size());
        Order order = page.entries.get(0);
        assertEquals("PO-184", order.orderId);
        assertEquals("Corner Shop", order.customer);
        assertFalse(order.invoiced);
        assertEquals(2, order.lines.size());
        assertEquals("notebook", order.lines.get(0).sku);
        assertEquals(3, order.lines.get(0).quantity);
        assertEquals(new BigDecimal("4.25"), order.lines.get(0).unitPrice);
        assertEquals("pencil", order.lines.get(1).sku);

        BigDecimal total = BigDecimal.ZERO;
        for (OrderLine line : order.lines) {
            total = total.add(line.unitPrice.multiply(BigDecimal.valueOf(line.quantity)));
        }
        assertEquals(new BigDecimal("16.75"), total);
        order.invoiceTotal = total;
        order.invoiced = true;

        JsonObject exported = new JsonParser().parse(gson.toJson(order)).getAsJsonObject();
        assertEquals(new JsonParser().parse("""
                {
                  "orderId": "PO-184",
                  "customer": "Corner Shop",
                  "invoiced": true,
                  "invoiceTotal": 16.75,
                  "lines": [
                    {"sku": "notebook", "quantity": 3, "unitPrice": 4.25},
                    {"sku": "pencil", "quantity": 5, "unitPrice": 0.80}
                  ]
                }
                """), exported);
        assertNotNull(exported.get("invoiceTotal"));
        assertEquals("16.75", exported.get("invoiceTotal").getAsString());
    }

    public static class Page<T> {
        private List<T> entries;
        private String nextCursor;
    }

    public static class Order {
        private String orderId;
        private String customer;
        private List<OrderLine> lines;
        private boolean invoiced;
        private BigDecimal invoiceTotal;
    }

    public static class OrderLine {
        private String sku;
        private int quantity;
        private BigDecimal unitPrice;
    }
}
