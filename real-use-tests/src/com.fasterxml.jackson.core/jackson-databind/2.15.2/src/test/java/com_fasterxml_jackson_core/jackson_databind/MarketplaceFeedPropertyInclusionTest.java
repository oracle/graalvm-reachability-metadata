/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Property inclusion rules: `@JsonInclude` with `NON_NULL`, `NON_ABSENT`, `NON_EMPTY`,
 * `NON_DEFAULT` and `CUSTOM` value and content filters, class-level `NON_DEFAULT` against the
 * bean's default instance, mapper-wide `defaultPropertyInclusion` and per-type config overrides.
 *
 * A marketplace pushes product listings to a partner feed that bills by payload size, so the
 * exporter leaves out every field that carries no information: unset values, empty
 * collections, untouched defaults and the "price on request" sentinel.
 */
public class MarketplaceFeedPropertyInclusionTest {

    private final ObjectMapper mapper = JsonMapper.builder()
            .addModule(new Jdk8Module())
            .build();

    @Test
    void omitsUnsetEmptyAndDefaultListingFields() throws Exception {
        Listing sparse = new Listing();
        sparse.sku = "SKU-100";
        sparse.title = "Trail running shoe";
        sparse.attributes.put("color", "teal");
        sparse.attributes.put("size", null);
        sparse.price = Listing.PRICE_ON_REQUEST;

        JsonNode tree = mapper.valueToTree(sparse);

        assertEquals(List.of("sku", "title", "attributes"), fieldNames(tree));
        assertEquals("teal", tree.path("attributes").path("color").asText());
        assertFalse(tree.path("attributes").has("size"));
    }

    @Test
    void writesEveryFieldThatCarriesInformation() throws Exception {
        Listing full = new Listing();
        full.sku = "SKU-200";
        full.title = "Rain jacket";
        full.subtitle = "Packable shell";
        full.promoCode = Optional.of("AUTUMN10");
        full.tags.add("outdoor");
        full.attributes.put("color", "red");
        full.stockReserved = 3;
        full.price = new BigDecimal("129.90");

        JsonNode tree = mapper.valueToTree(full);

        assertEquals(List.of("sku", "title", "subtitle", "promoCode", "tags", "attributes",
                "stockReserved", "price"), fieldNames(tree));
        assertEquals("AUTUMN10", tree.get("promoCode").asText());
        assertEquals(3, tree.get("stockReserved").asInt());
        assertEquals(0, new BigDecimal("129.90").compareTo(tree.get("price").decimalValue()));

        Listing back = mapper.treeToValue(tree, Listing.class);
        assertEquals(Optional.of("AUTUMN10"), back.promoCode);
        assertEquals(List.of("outdoor"), back.tags);
        assertEquals(0, new BigDecimal("129.90").compareTo(back.price));
    }

    @Test
    void dropsBlankPromoCodeThroughOptionalContentInclusion() throws Exception {
        Listing listing = new Listing();
        listing.sku = "SKU-300";
        listing.title = "Water bottle";
        listing.promoCode = Optional.of("");
        listing.price = new BigDecimal("12.00");

        JsonNode tree = mapper.valueToTree(listing);

        assertFalse(tree.has("promoCode"));
        assertTrue(tree.has("price"));
    }

    @Test
    void omittedFieldsReadBackAsDefaults() throws Exception {
        Listing back = mapper.readValue("""
                {"sku":"SKU-400","title":"Headlamp"}
                """, Listing.class);

        assertEquals("SKU-400", back.sku);
        assertEquals(null, back.subtitle);
        assertEquals(Optional.empty(), back.promoCode);
        assertTrue(back.tags.isEmpty());
        assertEquals(0, back.stockReserved);
        assertEquals(Listing.PRICE_ON_REQUEST, back.price);
    }

    @Test
    void shippingPolicyWritesOnlyValuesThatDifferFromItsDefaults() throws Exception {
        assertEquals("{}", mapper.writeValueAsString(new ShippingPolicy()));

        ShippingPolicy express = new ShippingPolicy();
        express.carrier = "COURIER";
        express.handlingDays = 2;
        express.freeAbove = 0;

        JsonNode tree = mapper.valueToTree(express);

        assertEquals(List.of("carrier", "freeAbove"), fieldNames(tree));
        assertEquals("COURIER", tree.get("carrier").asText());
        assertEquals(0, tree.get("freeAbove").asInt());
    }

    @Test
    void stockMapDropsUncountedBinsThroughContentFilter() throws Exception {
        WarehouseStock stock = new WarehouseStock();
        stock.bins.put("AMS-1", 12);
        stock.bins.put("AMS-2", WarehouseStock.UNCOUNTED);
        stock.bins.put("BER-1", 0);
        stock.locations.put("AMS", List.of("AMS-1", "AMS-2"));
        stock.locations.put("BER", List.of());

        JsonNode tree = mapper.valueToTree(stock);

        assertEquals(List.of("AMS-1", "BER-1"), fieldNames(tree.get("bins")));
        assertEquals(List.of("AMS"), fieldNames(tree.get("locations")));
    }

    @Test
    void mapperWideInclusionAndTypeOverridesApplyToUnannotatedTypes() throws Exception {
        ObjectMapper compact = JsonMapper.builder()
                .addModule(new Jdk8Module())
                .defaultPropertyInclusion(JsonInclude.Value.construct(
                        JsonInclude.Include.NON_ABSENT, JsonInclude.Include.NON_NULL))
                .withConfigOverride(String.class, override -> override.setInclude(
                        JsonInclude.Value.construct(JsonInclude.Include.NON_EMPTY, null)))
                .build();

        Seller seller = new Seller();
        seller.id = "S-7";
        seller.displayName = "";
        seller.vatNumber = null;
        seller.website = Optional.empty();
        seller.categories = new ArrayList<>();
        seller.ratings.put("shipping", 4.8);
        seller.ratings.put("returns", null);

        JsonNode tree = compact.valueToTree(seller);

        assertEquals(List.of("id", "categories", "ratings"), fieldNames(tree));
        assertTrue(tree.get("categories").isArray());
        assertEquals(List.of("shipping"), fieldNames(tree.get("ratings")));

        JsonNode verbose = mapper.valueToTree(seller);
        assertEquals(List.of("id", "displayName", "vatNumber", "website", "categories", "ratings"),
                fieldNames(verbose));
        assertTrue(verbose.get("vatNumber").isNull());
        assertTrue(verbose.get("website").isNull());
        assertTrue(verbose.get("ratings").get("returns").isNull());
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    /** A product listing as the partner feed receives it. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Listing {

        static final BigDecimal PRICE_ON_REQUEST = new BigDecimal("-1");

        public String sku;

        public String title;

        public String subtitle;

        @JsonInclude(value = JsonInclude.Include.NON_ABSENT, content = JsonInclude.Include.NON_EMPTY)
        public Optional<String> promoCode = Optional.empty();

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public List<String> tags = new ArrayList<>();

        @JsonInclude(value = JsonInclude.Include.NON_EMPTY, content = JsonInclude.Include.NON_NULL)
        public Map<String, String> attributes = new LinkedHashMap<>();

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        public int stockReserved;

        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = PriceOnRequest.class)
        public BigDecimal price = PRICE_ON_REQUEST;
    }

    /** Excludes the "price on request" sentinel: `equals` returning true means "leave out". */
    public static class PriceOnRequest {

        @Override
        public boolean equals(Object value) {
            return value instanceof BigDecimal amount && amount.signum() < 0;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }

    /** Shipping terms whose untouched defaults the feed already assumes. */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public static class ShippingPolicy {

        public String carrier = "POST";

        public int handlingDays = 2;

        public int freeAbove = 50;
    }

    /** Per-bin stock counts and the bins each warehouse location holds. */
    public static class WarehouseStock {

        static final int UNCOUNTED = -1;

        @JsonInclude(content = JsonInclude.Include.CUSTOM, contentFilter = Uncounted.class)
        public Map<String, Integer> bins = new LinkedHashMap<>();

        @JsonInclude(content = JsonInclude.Include.NON_EMPTY)
        public Map<String, List<String>> locations = new LinkedHashMap<>();
    }

    /** Excludes bins whose stock has not been counted yet. */
    public static class Uncounted {

        @Override
        public boolean equals(Object value) {
            return value instanceof Integer count && count == WarehouseStock.UNCOUNTED;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }

    /** A seller profile with no inclusion annotations of its own. */
    public static class Seller {

        public String id;

        public String displayName;

        public String vatNumber;

        public Optional<String> website;

        public List<String> categories;

        public Map<String, Double> ratings = new LinkedHashMap<>();
    }
}
