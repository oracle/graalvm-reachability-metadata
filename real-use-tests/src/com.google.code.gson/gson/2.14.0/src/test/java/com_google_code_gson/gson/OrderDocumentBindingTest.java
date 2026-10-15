/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Reflective data binding of an application's domain model to and from JSON documents.
 *
 * An order service stores its orders as a JSON file with snake_case keys and reads them
 * back into nested domain objects, generic collections, and enums.
 */
public class OrderDocumentBindingTest {

    private static final Type ORDER_LIST = new TypeToken<List<Order>>() { }.getType();

    private final Gson gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .create();

    @Test
    void storesOrdersAndReadsThemBackFromFile(@TempDir Path dir) throws IOException {
        List<Order> orders = List.of(sampleOrder("A-1001", Status.SHIPPED), sampleOrder("A-1002", Status.PENDING));
        Path file = dir.resolve("orders.json");

        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            gson.toJson(orders, ORDER_LIST, writer);
        }
        List<Order> restored;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            restored = gson.fromJson(reader, ORDER_LIST);
        }

        assertEquals(2, restored.size());
        Order first = restored.get(0);
        assertEquals("A-1001", first.orderId);
        assertEquals(Status.SHIPPED, first.status);
        assertEquals("Ada Lovelace", first.customer.fullName);
        assertEquals("10115", first.customer.shippingAddress.postalCode);
        assertEquals(List.of("KB-01", "MS-02"), first.lineItems.stream().map(item -> item.sku).toList());
        assertEquals(new BigDecimal("49.90"), first.lineItems.get(0).unitPrice);
        assertEquals(Map.of("channel", "web", "priority", "high"), first.tags);
        assertEquals(new BigDecimal("129.80"), first.total());
        assertEquals(Status.PENDING, restored.get(1).status);
    }

    @Test
    void writesDocumentWithNamingPolicyAndSerializedNames() {
        String json = gson.toJson(sampleOrder("A-2001", Status.CANCELLED));

        JsonObject document = gson.fromJson(json, JsonObject.class);
        assertEquals("A-2001", document.get("order_id").getAsString());
        assertEquals("cancelled", document.get("status").getAsString());
        JsonObject customer = document.getAsJsonObject("customer");
        assertEquals("Ada Lovelace", customer.get("full_name").getAsString());
        assertEquals("10115", customer.getAsJsonObject("shipping_address").get("postal_code").getAsString());
        JsonObject secondItem = document.getAsJsonArray("line_items").get(1).getAsJsonObject();
        assertEquals(new BigDecimal("15.00"), secondItem.get("unit_price").getAsBigDecimal());
        assertFalse(document.has("internalNote"));
        assertFalse(document.has("internal_note"));
    }

    @Test
    void readsDocumentProducedByAnotherService() {
        String json = """
                {
                  "order_id": "B-77",
                  "status": "shipped",
                  "customer": {
                    "full_name": "Grace Hopper",
                    "email": "grace@example.com",
                    "shipping_address": {"street": "1 Navy Way", "city": "Arlington", "postal_code": "22201"}
                  },
                  "line_items": [
                    {"sku": "CB-10", "quantity": 3, "unit_price": "2.50"}
                  ],
                  "tags": {"channel": "partner"},
                  "unknown_field": {"ignored": true}
                }
                """;

        Order order = gson.fromJson(json, Order.class);

        assertEquals("B-77", order.orderId);
        assertEquals(Status.SHIPPED, order.status);
        assertEquals("grace@example.com", order.customer.email);
        assertEquals("Arlington", order.customer.shippingAddress.city);
        assertEquals(1, order.lineItems.size());
        assertEquals("CB-10", order.lineItems.get(0).sku);
        assertEquals(3, order.lineItems.get(0).quantity);
        assertEquals(Map.of("channel", "partner"), order.tags);
        assertEquals(new BigDecimal("7.50"), order.total());
        assertNull(order.internalNote);
    }

    private static Order sampleOrder(String orderId, Status status) {
        Address address = new Address();
        address.street = "12 Analytical Lane";
        address.city = "Berlin";
        address.postalCode = "10115";

        Customer customer = new Customer();
        customer.fullName = "Ada Lovelace";
        customer.email = "ada@example.com";
        customer.shippingAddress = address;

        Order order = new Order();
        order.orderId = orderId;
        order.status = status;
        order.customer = customer;
        order.lineItems = new ArrayList<>();
        order.lineItems.add(new LineItem("KB-01", 2, new BigDecimal("49.90")));
        order.lineItems.add(new LineItem("MS-02", 2, new BigDecimal("15.00")));
        order.tags = new LinkedHashMap<>();
        order.tags.put("channel", "web");
        order.tags.put("priority", "high");
        order.internalNote = "do not export";
        return order;
    }

    enum Status {
        @SerializedName("pending")
        PENDING,
        @SerializedName("shipped")
        SHIPPED,
        @SerializedName("cancelled")
        CANCELLED
    }

    static final class Order {
        String orderId;
        Status status;
        Customer customer;
        List<LineItem> lineItems;
        Map<String, String> tags;
        transient String internalNote;

        BigDecimal total() {
            return lineItems.stream()
                    .map(item -> item.unitPrice.multiply(BigDecimal.valueOf(item.quantity)))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    static final class Customer {
        String fullName;
        String email;
        Address shippingAddress;
    }

    static final class Address {
        String street;
        String city;
        String postalCode;
    }

    static final class LineItem {
        String sku;
        int quantity;
        BigDecimal unitPrice;

        LineItem() {
        }

        LineItem(String sku, int quantity, BigDecimal unitPrice) {
            this.sku = sku;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }
    }
}
