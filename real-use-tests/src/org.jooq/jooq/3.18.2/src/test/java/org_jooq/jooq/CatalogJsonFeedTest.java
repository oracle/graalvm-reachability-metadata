/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jooq.jooq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONFormat;
import org.jooq.JSONFormat.RecordFormat;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises jOOQ's JSON result-serialization subsystem to publish a tenant-scoped storefront catalog feed.
 */
public class CatalogJsonFeedTest {
    private static final Table<Record> PRODUCTS = DSL.table(DSL.name("feed_product"));
    private static final Field<String> TENANT = DSL.field(DSL.name("tenant"), String.class);
    private static final Field<String> SKU = DSL.field(DSL.name("sku"), String.class);
    private static final Field<String> TITLE = DSL.field(DSL.name("title"), String.class);
    private static final Field<BigDecimal> PRICE = DSL.field(DSL.name("price"), BigDecimal.class);
    private static final Field<Boolean> IN_STOCK = DSL.field(DSL.name("in_stock"), Boolean.class);
    private static final Field<String> NOTE = DSL.field(DSL.name("note"), String.class);
    private static final Field<Boolean> PUBLISHED = DSL.field(DSL.name("published"), Boolean.class);

    @Test
    @Timeout(60)
    public void exportsOnlyPublishedTenantProductsWithJsonTypesAndEscaping() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:catalog_json_feed", "sa", "")) {
            DSLContext database = DSL.using(connection, SQLDialect.H2);
            database.createTable(PRODUCTS)
                    .column(TENANT, SQLDataType.VARCHAR(40).nullable(false))
                    .column(SKU, SQLDataType.VARCHAR(40).nullable(false))
                    .column(TITLE, SQLDataType.VARCHAR(120).nullable(false))
                    .column(PRICE, SQLDataType.DECIMAL(10, 2).nullable(false))
                    .column(IN_STOCK, SQLDataType.BOOLEAN.nullable(false))
                    .column(NOTE, SQLDataType.VARCHAR(120))
                    .column(PUBLISHED, SQLDataType.BOOLEAN.nullable(false))
                    .primaryKey(TENANT, SKU)
                    .execute();
            database.insertInto(PRODUCTS, TENANT, SKU, TITLE, PRICE, IN_STOCK, NOTE, PUBLISHED)
                    .values("north", "P-2", "Pen refill", new BigDecimal("1.25"), false, null, true)
                    .values("south", "P-1", "Private southern listing", new BigDecimal("99.00"),
                            true, "Not for the north storefront", true)
                    .values("north", "P-1", "Desk \"organiser\"\nLarge", new BigDecimal("12.50"),
                            true, "Collect in store", true)
                    .values("north", "D-1", "Unannounced product", new BigDecimal("50.00"),
                            true, null, false)
                    .execute();

            // The HTTP layer can return jOOQ's JSON directly; Jackson is only the consuming client.
            ObjectMapper client = new ObjectMapper();
            JsonNode expected = client.readTree("""
                    [
                      {"sku":"P-1","title":"Desk \\"organiser\\"\\nLarge","price":12.50,
                       "inStock":true,"note":"Collect in store"},
                      {"sku":"P-2","title":"Pen refill","price":1.25,"inStock":false,"note":null}
                    ]
                    """);
            assertEquals(expected, client.readTree(catalogFeed(database, "north")));
            assertEquals(client.readTree("[]"), client.readTree(catalogFeed(database, "unknown")));
            assertEquals(client.readTree("""
                    [{"sku":"P-1","title":"Private southern listing","price":99.00,
                      "inStock":true,"note":"Not for the north storefront"}]
                    """), client.readTree(catalogFeed(database, "south")));

            // Withdrawing a listing affects the next feed without exposing unpublished rows.
            assertEquals(1, database.update(PRODUCTS).set(PUBLISHED, false)
                    .where(TENANT.eq("north").and(SKU.eq("P-1"))).execute());
            assertEquals(client.readTree("""
                    [{"sku":"P-2","title":"Pen refill","price":1.25,"inStock":false,"note":null}]
                    """), client.readTree(catalogFeed(database, "north")));
        }
    }

    private static String catalogFeed(DSLContext database, String tenant) {
        // Export named JSON objects, not jOOQ's metadata envelope or positional record arrays.
        JSONFormat format = new JSONFormat().header(false).recordFormat(RecordFormat.OBJECT);
        return database.select(SKU, TITLE, PRICE, IN_STOCK.as("inStock"), NOTE)
                .from(PRODUCTS)
                .where(TENANT.eq(tenant).and(PUBLISHED.isTrue()))
                .orderBy(SKU)
                .fetch()
                .formatJSON(format);
    }
}
