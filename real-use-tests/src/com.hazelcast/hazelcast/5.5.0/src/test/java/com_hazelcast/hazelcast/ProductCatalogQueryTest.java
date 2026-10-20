/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_hazelcast.hazelcast;

import com.hazelcast.aggregation.Aggregators;
import com.hazelcast.config.Config;
import com.hazelcast.config.IndexConfig;
import com.hazelcast.config.IndexType;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.projection.Projections;
import com.hazelcast.query.PagingPredicate;
import com.hazelcast.query.Predicate;
import com.hazelcast.query.Predicates;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises distributed IMap queries: predicates, indexes, paging, projections, and aggregations.
 *
 * An embedded member holds a shop's product catalog in a map indexed by price and category.
 * The storefront filters products with predicate builders and with the predicate SQL dialect,
 * pages through active products ordered by price, projects product names, aggregates stock and
 * prices, and removes discontinued products by predicate. Each query reads the attributes of
 * the stored domain objects through their getters, and the map's statistics show that the
 * indexes served the queries.
 */
class ProductCatalogQueryTest {

    private static final String CATALOG_MAP = "products";

    private HazelcastInstance member;

    private IMap<String, Product> products;

    @BeforeEach
    void startCatalog() {
        Config config = new Config();
        config.setClusterName("catalog-" + UUID.randomUUID());
        config.setProperty("hazelcast.phone.home.enabled", "false");
        config.getNetworkConfig().setPort(5701).setPortAutoIncrement(true);
        JoinConfig join = config.getNetworkConfig().getJoin();
        join.getMulticastConfig().setEnabled(false);
        join.getTcpIpConfig().setEnabled(false);
        join.getAutoDetectionConfig().setEnabled(false);
        config.addMapConfig(new MapConfig(CATALOG_MAP)
                .addIndexConfig(new IndexConfig(IndexType.SORTED, "priceCents"))
                .addIndexConfig(new IndexConfig(IndexType.HASH, "category")));
        member = Hazelcast.newHazelcastInstance(config);

        products = member.getMap(CATALOG_MAP);
        products.put("LAMP", new Product("Desk Lamp", "lighting", 4500, 12, true));
        products.put("BULB", new Product("LED Bulb", "lighting", 900, 0, true));
        products.put("SCONCE", new Product("Wall Sconce", "lighting", 7200, 3, true));
        products.put("DESK", new Product("Oak Desk", "furniture", 18000, 2, true));
        products.put("CHAIR", new Product("Office Chair", "furniture", 7900, 0, false));
        products.put("RUG", new Product("Wool Rug", "textiles", 12000, 5, true));
    }

    @AfterEach
    void stopCatalog() {
        if (member != null) {
            member.shutdown();
        }
    }

    @Test
    void storefrontFiltersPagesAndAggregatesProducts() {
        Predicate<String, Product> cheapLighting = Predicates.and(
                Predicates.equal("category", "lighting"), Predicates.lessThan("priceCents", 5000));
        assertThat(names(products.values(cheapLighting))).containsExactlyInAnyOrder("Desk Lamp", "LED Bulb");

        assertThat(products.keySet(Predicates.sql("active = true AND stock > 0")))
                .containsExactlyInAnyOrder("LAMP", "SCONCE", "DESK", "RUG");
        assertThat(products.keySet(Predicates.between("priceCents", 4000, 8000)))
                .containsExactlyInAnyOrder("LAMP", "SCONCE", "CHAIR");
        assertThat(products.keySet(Predicates.ilike("name", "%LAMP%"))).containsExactly("LAMP");
        assertThat(products.keySet(Predicates.in("category", "furniture", "textiles")))
                .containsExactlyInAnyOrder("DESK", "CHAIR", "RUG");

        PagingPredicate<String, Product> activeByPrice = Predicates.pagingPredicate(
                Predicates.equal("active", true), new ByPrice(), 2);
        assertThat(names(products.values(activeByPrice))).containsExactly("LED Bulb", "Desk Lamp");
        activeByPrice.nextPage();
        assertThat(names(products.values(activeByPrice))).containsExactly("Wall Sconce", "Wool Rug");
        activeByPrice.nextPage();
        assertThat(names(products.values(activeByPrice))).containsExactly("Oak Desk");

        Collection<String> furniture = products.project(Projections.singleAttribute("name"),
                Predicates.equal("category", "furniture"));
        assertThat(furniture).containsExactlyInAnyOrder("Oak Desk", "Office Chair");

        assertThat(products.aggregate(Aggregators.integerSum("stock"))).isEqualTo(22L);
        assertThat(products.aggregate(Aggregators.count(), Predicates.equal("stock", 0))).isEqualTo(2L);
        assertThat(products.aggregate(Aggregators.<Map.Entry<String, Product>, String>distinct("category")))
                .containsExactlyInAnyOrder("lighting", "furniture", "textiles");
        assertThat(products.aggregate(Aggregators.integerMax("priceCents"), Predicates.equal("category", "lighting")))
                .isEqualTo(7200);

        products.put("BULB", new Product("LED Bulb", "lighting", 900, 40, true));
        assertThat(products.keySet(Predicates.sql("active = true AND stock > 0")))
                .containsExactlyInAnyOrder("LAMP", "BULB", "SCONCE", "DESK", "RUG");

        products.removeAll(Predicates.equal("active", false));
        assertThat(products.keySet()).containsExactlyInAnyOrder("LAMP", "BULB", "SCONCE", "DESK", "RUG");
        assertThat(products.keySet(Predicates.equal("category", "furniture"))).containsExactly("DESK");

        assertThat(products.getLocalMapStats().getIndexedQueryCount()).isPositive();
    }

    private static List<String> names(Collection<Product> found) {
        return found.stream().map(Product::getName).toList();
    }

    /**
     * A product as the catalog stores it, with prices in cents.
     */
    public static final class Product implements Serializable {

        private final String name;

        private final String category;

        private final int priceCents;

        private final int stock;

        private final boolean active;

        Product(String name, String category, int priceCents, int stock, boolean active) {
            this.name = name;
            this.category = category;
            this.priceCents = priceCents;
            this.stock = stock;
            this.active = active;
        }

        public String getName() {
            return name;
        }

        public String getCategory() {
            return category;
        }

        public int getPriceCents() {
            return priceCents;
        }

        public int getStock() {
            return stock;
        }

        public boolean isActive() {
            return active;
        }
    }

    /**
     * Orders catalog entries from the cheapest product to the most expensive one.
     */
    static final class ByPrice implements Comparator<Map.Entry<String, Product>>, Serializable {

        @Override
        public int compare(Map.Entry<String, Product> first, Map.Entry<String, Product> second) {
            return Integer.compare(first.getValue().getPriceCents(), second.getValue().getPriceCents());
        }
    }
}
