/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.TypeFactory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Generic type resolution: binding parameterized envelopes through `TypeReference` and
 * `JavaType` instances built with `TypeFactory`, type variables bound by subclasses, abstract
 * type mappings with `@JsonDeserialize(as = ..., contentAs = ...)` and `SimpleModule`, and
 * `convertValue` conversions between maps, trees and typed objects.
 *
 * A storefront talks to a catalog API whose every response is a generic `ApiResponse<T>`
 * envelope. It resolves the payload type at the call site, pages through product listings,
 * groups stock by warehouse, converts loosely typed settings into typed objects, and writes a
 * typed batch back with an explicit root type.
 */
public class CatalogApiGenericTypesTest {

    private static final String PRODUCT_PAGE = """
            {
              "status": "ok",
              "payload": {
                "number": 2,
                "total": 41,
                "items": [
                  {"sku": "KB-104", "name": "Mechanical keyboard", "price": 89.90,
                   "tags": ["input", "office"]},
                  {"sku": "MS-220", "name": "Wireless mouse", "price": 24.50, "tags": ["input"]}
                ]
              }
            }
            """;

    private static final String STOCK_BY_WAREHOUSE = """
            {
              "status": "ok",
              "payload": {
                "berlin": [{"sku": "KB-104", "quantity": 12}, {"sku": "MS-220", "quantity": 3}],
                "lyon": [{"sku": "KB-104", "quantity": 0}]
              }
            }
            """;

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    void bindsEnvelopeThroughTypeReference() throws Exception {
        ApiResponse<Page<Product>> response = mapper.readValue(PRODUCT_PAGE,
                new TypeReference<ApiResponse<Page<Product>>>() {
                });

        assertEquals("ok", response.status);
        Page<Product> page = response.payload;
        assertEquals(2, page.number);
        assertEquals(41, page.total);
        assertEquals(2, page.items.size());
        Product keyboard = page.items.get(0);
        assertInstanceOf(Product.class, keyboard);
        assertEquals(new BigDecimal("89.90"), keyboard.price);
        assertEquals(List.of("input", "office"), keyboard.tags);
    }

    @Test
    void bindsEnvelopeThroughConstructedJavaTypes() throws Exception {
        TypeFactory types = mapper.getTypeFactory();
        JavaType stockLevels = types.constructCollectionType(List.class, StockLevel.class);
        JavaType byWarehouse = types.constructMapType(LinkedHashMap.class,
                types.constructType(String.class), stockLevels);
        JavaType envelope = types.constructParametricType(ApiResponse.class, byWarehouse);

        assertEquals(
                "com_fasterxml_jackson_core.jackson_databind.CatalogApiGenericTypesTest$ApiResponse<java.util"
                        + ".LinkedHashMap<java.lang.String,java.util.List<com_fasterxml_jackson_core.jackson_databind"
                        + ".CatalogApiGenericTypesTest$StockLevel>>>",
                envelope.toCanonical());

        ApiResponse<LinkedHashMap<String, List<StockLevel>>> response = mapper.readValue(STOCK_BY_WAREHOUSE,
                envelope);

        LinkedHashMap<String, List<StockLevel>> stock = response.payload;
        assertEquals(List.of("berlin", "lyon"), new ArrayList<>(stock.keySet()));
        assertEquals(12, stock.get("berlin").get(0).quantity);
        assertEquals("MS-220", stock.get("berlin").get(1).sku);
        assertEquals(0, stock.get("lyon").get(0).quantity);

        JavaType reparsed = types.constructFromCanonical(envelope.toCanonical());
        assertEquals(envelope, reparsed);
    }

    @Test
    void resolvesTypeVariableBoundBySubclass() throws Exception {
        JsonNode payload = mapper.readTree(PRODUCT_PAGE).get("payload");

        ProductPage page = mapper.treeToValue(payload, ProductPage.class);

        assertEquals(2, page.items.size());
        assertInstanceOf(Product.class, page.items.get(1));
        assertEquals("Wireless mouse", page.items.get(1).name);
        JavaType itemType = mapper.constructType(ProductPage.class).findTypeParameters(Page.class)[0];
        assertEquals(Product.class, itemType.getRawClass());
    }

    @Test
    void mapsAbstractDeclaredTypesToConcreteImplementations() throws Exception {
        String json = """
                {"name": "Spring promotion",
                 "discount": {"percent": 15},
                 "skus": ["MS-220", "KB-104", "MS-220"],
                 "audience": ["loyalty", "newsletter"]}
                """;
        ObjectMapper unmapped = JsonMapper.builder().build();
        assertThrows(InvalidDefinitionException.class, () -> unmapped.readValue(json, Campaign.class));

        SimpleModule pricing = new SimpleModule("pricing")
                .addAbstractTypeMapping(Discount.class, PercentDiscount.class);
        ObjectMapper campaigns = JsonMapper.builder().addModule(pricing).build();

        Campaign campaign = campaigns.readValue(json, Campaign.class);

        PercentDiscount discount = assertInstanceOf(PercentDiscount.class, campaign.discount);
        assertEquals(new BigDecimal("85.00"), discount.apply(new BigDecimal("100.00")));
        TreeSet<String> skus = assertInstanceOf(TreeSet.class, campaign.skus);
        assertEquals(List.of("KB-104", "MS-220"), new ArrayList<>(skus));
        assertEquals(2, campaign.audience.size());
        assertInstanceOf(Segment.class, campaign.audience.get(0));
        assertEquals("newsletter", campaign.audience.get(1).toString());
    }

    @Test
    void convertsLooselyTypedValuesWithoutTextRoundTrip() {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("sku", "CB-007");
        settings.put("name", "USB-C cable");
        settings.put("price", "9.99");
        settings.put("tags", List.of("cable"));

        Product product = mapper.convertValue(settings, Product.class);
        assertEquals(new BigDecimal("9.99"), product.price);
        assertEquals(List.of("cable"), product.tags);

        Map<String, Object> flattened = mapper.convertValue(product, new TypeReference<Map<String, Object>>() {
        });
        assertEquals("USB-C cable", flattened.get("name"));
        assertEquals(List.of("cable"), flattened.get("tags"));

        Map<String, Integer> raw = Map.of("PRIMARY", 3, "BACKUP", 1);
        EnumMap<Channel, Integer> byChannel = mapper.convertValue(raw,
                mapper.getTypeFactory().constructMapType(EnumMap.class, Channel.class, Integer.class));
        assertEquals(3, byChannel.get(Channel.PRIMARY));
        assertEquals(1, byChannel.get(Channel.BACKUP));
        assertEquals(List.of(Channel.PRIMARY, Channel.BACKUP), new ArrayList<>(byChannel.keySet()));

        assertThrows(IllegalArgumentException.class,
                () -> mapper.convertValue(Map.of("quantity", "plenty"), StockLevel.class));
    }

    @Test
    void writesBatchWithExplicitRootType() throws Exception {
        StockLevel first = new StockLevel();
        first.sku = "KB-104";
        first.quantity = 5;
        AuditedStockLevel second = new AuditedStockLevel();
        second.sku = "MS-220";
        second.quantity = 7;
        second.auditor = "night-shift";
        List<StockLevel> batch = List.of(first, second);

        String asDeclared = mapper.writerFor(new TypeReference<List<StockLevel>>() {
        }).writeValueAsString(batch);
        String asRuntime = mapper.writeValueAsString(batch);

        assertEquals("""
                [{"sku":"KB-104","quantity":5},{"sku":"MS-220","quantity":7}]""", asDeclared);
        assertTrue(asRuntime.contains("\"auditor\":\"night-shift\""));

        StockLevel[] array = mapper.readValue(asDeclared, StockLevel[].class);
        assertEquals(2, array.length);
        assertEquals(7, array[1].quantity);
    }

    /** Generic envelope every catalog API response is wrapped in. */
    public static class ApiResponse<T> {
        public String status;
        public T payload;
    }

    /** One page of a paginated listing. */
    public static class Page<T> {
        public int number;
        public long total;
        public List<T> items;
    }

    /** Page whose item type is fixed by the subclass declaration. */
    public static class ProductPage extends Page<Product> {
    }

    /** Catalog product. */
    public static class Product {
        public String sku;
        public String name;
        public BigDecimal price;
        public List<String> tags;
    }

    /** Stock count of one product in one warehouse. */
    public static class StockLevel {
        public String sku;
        public int quantity;
    }

    /** Stock count carrying an extra field only its runtime type declares. */
    public static class AuditedStockLevel extends StockLevel {
        public String auditor;
    }

    /** Price reduction declared abstractly in the campaign model. */
    public interface Discount {
        BigDecimal apply(BigDecimal price);
    }

    /** Percentage price reduction the application maps `Discount` to. */
    public static class PercentDiscount implements Discount {
        public int percent;

        @Override
        public BigDecimal apply(BigDecimal price) {
            return price.multiply(BigDecimal.valueOf(100 - percent)).divide(BigDecimal.valueOf(100));
        }
    }

    /** Customer segment a campaign targets. */
    public interface Audience {
    }

    /** Named customer segment, built from its JSON string. */
    public static class Segment implements Audience {
        private final String name;

        public Segment(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** Marketing campaign whose fields are declared with abstract types. */
    public static class Campaign {
        public String name;
        public Discount discount;

        @JsonDeserialize(as = TreeSet.class)
        public SortedSet<String> skus;

        @JsonDeserialize(contentAs = Segment.class)
        public List<Audience> audience;
    }

    /** Fulfilment channel used as an `EnumMap` key. */
    public enum Channel {
        PRIMARY,
        BACKUP
    }
}
