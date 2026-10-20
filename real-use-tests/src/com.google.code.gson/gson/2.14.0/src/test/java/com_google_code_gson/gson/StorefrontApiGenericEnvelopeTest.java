/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Generic type resolution: binding parameterized application classes through `TypeToken`
 * instances built at runtime, type variables inherited from generic superclasses, generic
 * records, generic arrays, and wildcard-typed fields.
 *
 * A storefront client receives every API answer wrapped in one `ApiResponse<T>` envelope whose
 * payload is often a `Page<T>`. Its helpers build the full parameterized type from the element
 * class at runtime, some endpoints map to concrete subclasses that fix the type argument, and a
 * few payloads use generic arrays and wildcard collections. The tests assert on the objects the
 * client reads back and on the JSON it sends.
 */
public class StorefrontApiGenericEnvelopeTest {

    private static final Gson GSON = new Gson();

    private static final String ORDER_PAGE_RESPONSE = """
            {
              "requestId": "req-8841",
              "data": {
                "number": 2,
                "size": 2,
                "items": [
                  {"id": "o-101", "total": {"currency": "EUR", "cents": 4599}, "skus": ["kb-1", "ms-2"]},
                  {"id": "o-102", "total": {"currency": "EUR", "cents": 1250}, "skus": ["cb-9"]}
                ],
                "highlight": {"id": "o-101", "total": {"currency": "EUR", "cents": 4599}, "skus": ["kb-1", "ms-2"]}
              },
              "errors": []
            }
            """;

    @Test
    void readsAPagedEnvelopeThroughATypeBuiltFromTheElementClass() {
        ApiResponse<Page<Order>> response = StorefrontClient.readPage(GSON, ORDER_PAGE_RESPONSE, Order.class);

        assertEquals("req-8841", response.requestId());
        assertEquals(List.of(), response.errors());
        Page<Order> page = response.data();
        assertEquals(2, page.number);
        Order first = page.items.get(0);
        assertEquals(new Order("o-101", new Money("EUR", 4599), List.of("kb-1", "ms-2")), first);
        assertEquals(1250, page.items.get(1).total().cents());
        assertEquals(first, page.highlight, "a field typed by the bare type variable binds to Order");

        Type pageOfOrders = TypeToken.getParameterized(Page.class, Order.class).getType();
        Type envelope = TypeToken.getParameterized(ApiResponse.class, pageOfOrders).getType();
        assertEquals(new TypeToken<ApiResponse<Page<Order>>>() { }.getType().getTypeName(),
                envelope.getTypeName(), "a runtime-built type names the one an anonymous TypeToken captures");

        JsonObject resent = JsonParser.parseString(GSON.toJson(response, envelope)).getAsJsonObject();
        assertEquals(JsonParser.parseString(ORDER_PAGE_RESPONSE), resent);
    }

    @Test
    void reusesOneEnvelopeHelperForDifferentPayloadTypes() {
        String customers = """
                {"requestId": "req-17", "data": [{"id": "c-1", "name": "Ada"}, {"id": "c-2", "name": "Linus"}],
                 "errors": [{"code": "PARTIAL", "message": "1 customer hidden"}]}
                """;
        String count = """
                {"requestId": "req-18", "data": 42, "errors": []}
                """;

        ApiResponse<List<Customer>> list = StorefrontClient.readList(GSON, customers, Customer.class);
        ApiResponse<Long> total = StorefrontClient.read(GSON, count, Long.class);

        assertEquals(List.of(new Customer("c-1", "Ada"), new Customer("c-2", "Linus")), list.data());
        assertEquals(List.of(new ApiError("PARTIAL", "1 customer hidden")), list.errors());
        assertEquals(42L, total.data());
        assertInstanceOf(Long.class, total.data(), "the type argument decides the number type");
    }

    @Test
    void resolvesTypeVariablesInheritedFromAGenericSuperclass() {
        String cursorPage = """
                {"cursor": "eyJvIjoxMDN9", "number": 1, "size": 1,
                 "items": [{"id": "o-103", "total": {"currency": "USD", "cents": 999}, "skus": []}],
                 "highlight": null}
                """;

        OrderPage page = GSON.fromJson(cursorPage, OrderPage.class);

        assertEquals("eyJvIjoxMDN9", page.cursor);
        assertEquals(1, page.items.size());
        Order order = page.items.get(0);
        assertEquals(new Money("USD", 999), order.total());
        assertEquals(List.of(), order.skus());
        assertEquals(null, page.highlight);

        JsonObject written = GSON.toJsonTree(page).getAsJsonObject();
        assertEquals("eyJvIjoxMDN9", written.get("cursor").getAsString());
        assertEquals("o-103", written.getAsJsonArray("items").get(0).getAsJsonObject().get("id").getAsString());
    }

    @Test
    void bindsGenericArraysAndMapsOfTypeVariablesInATwoParameterClass() {
        String snapshot = """
                {"buckets": {"vip": [{"id": "c-7", "name": "Grace"}], "new": [{"id": "c-9", "name": "Ken"}]},
                 "latest": [{"id": "c-9", "name": "Ken"}, {"id": "c-7", "name": "Grace"}]}
                """;
        Type type = new TypeToken<Segments<String, Customer>>() { }.getType();

        Segments<String, Customer> segments = GSON.fromJson(snapshot, type);

        assertEquals(List.of("vip", "new"), List.copyOf(segments.buckets.keySet()));
        assertEquals(new Customer("c-7", "Grace"), segments.buckets.get("vip").get(0));
        assertInstanceOf(Customer[].class, segments.latest, "a V[] field becomes a typed array");
        assertArrayEquals(new Customer[] {new Customer("c-9", "Ken"), new Customer("c-7", "Grace")},
                segments.latest);

        Customer[] topLevel = GSON.fromJson("""
                [{"id": "c-1", "name": "Ada"}]
                """, TypeToken.getArray(Customer.class).getType());
        assertArrayEquals(new Customer[] {new Customer("c-1", "Ada")}, topLevel);
    }

    @Test
    void bindsWildcardCollectionsToTheirUpperBound() {
        String catalog = """
                {"title": "Autumn picks",
                 "featured": [{"sku": "kb-1", "priceCents": 3999}, {"sku": "ms-2", "priceCents": 600}],
                 "stockBySku": {"kb-1": 12, "ms-2": 0}}
                """;

        Catalog parsed = GSON.fromJson(catalog, Catalog.class);

        assertEquals("Autumn picks", parsed.title);
        assertEquals(List.of(new Product("kb-1", 3999), new Product("ms-2", 600)), parsed.featured);
        Product first = parsed.featured.get(0);
        assertEquals(3999, first.priceCents());
        assertEquals(List.of("kb-1", "ms-2"), List.copyOf(parsed.stockBySku.keySet()));
        assertEquals(12, parsed.stockBySku.get("kb-1").intValue());
        assertEquals(0L, parsed.stockBySku.get("ms-2").longValue());
    }

    @Test
    void showsWhatHappensWhenTheTypeArgumentIsLost() {
        @SuppressWarnings("rawtypes")
        Page raw = GSON.fromJson(GSON.toJson(StorefrontClient.readPage(GSON, ORDER_PAGE_RESPONSE, Order.class)
                .data()), Page.class);

        Object element = raw.items.get(0);
        assertInstanceOf(Map.class, element, "without a type argument elements stay generic maps");
        assertEquals("o-101", ((Map<?, ?>) element).get("id"));
        assertNotEquals(Order.class, element.getClass());

        IllegalArgumentException captured = assertThrows(IllegalArgumentException.class,
                StorefrontClient::listTypeCapturingTheTypeVariable);
        assertTrue(captured.getMessage().contains("type variable"), captured.getMessage());
    }

    /// Helpers an application writes once to read every endpoint's envelope.
    static final class StorefrontClient {

        private StorefrontClient() {
        }

        static <T> ApiResponse<T> read(Gson gson, String json, Class<T> payload) {
            TypeToken<?> type = TypeToken.getParameterized(ApiResponse.class, payload);
            @SuppressWarnings("unchecked")
            ApiResponse<T> response = (ApiResponse<T>) gson.fromJson(json, type);
            return response;
        }

        static <T> ApiResponse<List<T>> readList(Gson gson, String json, Class<T> element) {
            Type list = TypeToken.getParameterized(List.class, element).getType();
            return gson.fromJson(json, TypeToken.getParameterized(ApiResponse.class, list).getType());
        }

        static <T> ApiResponse<Page<T>> readPage(Gson gson, String json, Class<T> element) {
            Type page = TypeToken.getParameterized(Page.class, element).getType();
            return gson.fromJson(json, TypeToken.getParameterized(ApiResponse.class, page).getType());
        }

        /// The tempting but wrong shortcut: `T` is erased, so Gson refuses to capture it.
        static <T> Type listTypeCapturingTheTypeVariable() {
            return new TypeToken<List<T>>() { }.getType();
        }
    }

    record Money(String currency, long cents) {
    }

    record Order(String id, Money total, List<String> skus) {
    }

    record Customer(String id, String name) {
    }

    record ApiError(String code, String message) {
    }

    record Product(String sku, int priceCents) {
    }

    record ApiResponse<T>(String requestId, T data, List<ApiError> errors) {
    }

    static class Page<T> {
        int number;
        int size;
        List<T> items;
        T highlight;
    }

    static final class OrderPage extends Page<Order> {
        String cursor;
    }

    static final class Segments<K, V> {
        Map<K, List<V>> buckets;
        V[] latest;
    }

    static final class Catalog {
        String title;
        List<? extends Product> featured;
        Map<String, ? extends Number> stockBySku;
    }
}
