/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jooq.jooq;

import org.jooq.BatchBindStep;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Records;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises jOOQ's prepared-batch subsystem to apply supplier price changes without overwriting newer prices.
 */
public class CatalogBatchPricingTest {
    private static final Table<Record> PRODUCTS = DSL.table(DSL.name("priced_product"));
    private static final Field<String> SKU = DSL.field(DSL.name("sku"), String.class);
    private static final Field<BigDecimal> PRICE = DSL.field(DSL.name("price"), BigDecimal.class);
    private static final Field<Boolean> ACTIVE = DSL.field(DSL.name("active"), Boolean.class);

    @Test
    @Timeout(60)
    public void appliesEligiblePricesAndReportsRejectedSupplierChanges() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:catalog_batch_pricing", "sa", "")) {
            DSLContext database = DSL.using(connection, SQLDialect.H2);
            database.createTable(PRODUCTS)
                    .column(SKU, SQLDataType.VARCHAR(40).nullable(false))
                    .column(PRICE, SQLDataType.DECIMAL(10, 2).nullable(false))
                    .column(ACTIVE, SQLDataType.BOOLEAN.nullable(false))
                    .primaryKey(SKU)
                    .execute();
            database.insertInto(PRODUCTS, SKU, PRICE, ACTIVE)
                    .values("notebook", new BigDecimal("4.50"), true)
                    .values("pen", new BigDecimal("1.25"), true)
                    .values("folder", new BigDecimal("3.75"), true)
                    .values("binder", new BigDecimal("8.00"), false)
                    .execute();

            List<PriceChange> supplierChanges = List.of(
                    new PriceChange("notebook", new BigDecimal("4.50"), new BigDecimal("4.95")),
                    new PriceChange("pen", new BigDecimal("1.25"), new BigDecimal("1.10")),
                    new PriceChange("folder", new BigDecimal("3.00"), new BigDecimal("3.25")),
                    new PriceChange("binder", new BigDecimal("8.00"), new BigDecimal("8.50")),
                    new PriceChange("unknown", new BigDecimal("2.00"), new BigDecimal("2.25")));

            int[] affected = applyPrices(database, supplierChanges);

            // Counts correspond to input rows, allowing the importer to report rejected changes.
            assertArrayEquals(new int[]{1, 1, 0, 0, 0}, affected);
            List<String> rejected = new ArrayList<>();
            for (int index = 0; index < affected.length; index++) {
                if (affected[index] == 0) {
                    rejected.add(supplierChanges.get(index).sku());
                }
            }
            assertEquals(List.of("folder", "binder", "unknown"), rejected);

            // The stale supplier price cannot overwrite a manual change; retired products stay unchanged.
            assertEquals(List.of(
                    new Product("binder", new BigDecimal("8.00"), false),
                    new Product("folder", new BigDecimal("3.75"), true),
                    new Product("notebook", new BigDecimal("4.95"), true),
                    new Product("pen", new BigDecimal("1.10"), true)),
                    database.select(SKU, PRICE, ACTIVE).from(PRODUCTS)
                            .orderBy(SKU).fetch(Records.mapping(Product::new)));
        }
    }

    private static int[] applyPrices(DSLContext database, List<PriceChange> changes) {
        // Reuse one prepared UPDATE with a separate bind set for each supplier row.
        BatchBindStep batch = database.batch(database.update(PRODUCTS)
                .set(PRICE, DSL.param("new_price", SQLDataType.DECIMAL(10, 2)))
                .where(SKU.eq(DSL.param("sku", SQLDataType.VARCHAR(40))))
                .and(PRICE.eq(DSL.param("expected_price", SQLDataType.DECIMAL(10, 2))))
                .and(ACTIVE.eq(DSL.inline(true))));
        for (PriceChange change : changes) {
            batch.bind(change.newPrice(), change.sku(), change.expectedPrice());
        }
        return batch.execute();
    }

    private record PriceChange(String sku, BigDecimal expectedPrice, BigDecimal newPrice) {
    }

    private record Product(String sku, BigDecimal price, boolean active) {
    }
}
