/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jooq.jooq;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record3;
import org.jooq.Records;
import org.jooq.ResultQuery;
import org.jooq.SQLDialect;
import org.jooq.SelectSeekStep2;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises jOOQ's keyset pagination subsystem to browse a changing product catalog without repeating products.
 */
public class CatalogKeysetPaginationTest {
    private static final Table<Record> PRODUCTS = DSL.table(DSL.name("browse_product"));
    private static final Field<Integer> ID = DSL.field(DSL.name("product_id"), Integer.class);
    private static final Field<String> LABEL = DSL.field(DSL.name("label"), String.class);
    private static final Field<String> CATEGORY = DSL.field(DSL.name("category"), String.class);
    private static final Field<BigDecimal> PRICE = DSL.field(DSL.name("price"), BigDecimal.class);

    @Test
    @Timeout(60)
    public void continuesPastPriceTiesAndDeletedCursorsWhileEarlierProductsAreAdded() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:catalog_keyset", "sa", "")) {
            DSLContext database = DSL.using(connection, SQLDialect.H2);
            database.createTable(PRODUCTS)
                    .column(ID, SQLDataType.INTEGER.nullable(false))
                    .column(LABEL, SQLDataType.VARCHAR(80).nullable(false))
                    .column(CATEGORY, SQLDataType.VARCHAR(40).nullable(false))
                    .column(PRICE, SQLDataType.DECIMAL(10, 2).nullable(false))
                    .primaryKey(ID)
                    .execute();
            database.insertInto(PRODUCTS, ID, LABEL, CATEGORY, PRICE)
                    .values(50, "Folder", "stationery", new BigDecimal("4.00"))
                    .values(30, "Pencil", "stationery", new BigDecimal("2.50"))
                    .values(90, "Spoon", "kitchen", new BigDecimal("1.00"))
                    .values(10, "Eraser", "stationery", new BigDecimal("2.50"))
                    .values(40, "Notebook", "stationery", new BigDecimal("4.00"))
                    .values(20, "Pen", "stationery", new BigDecimal("2.50"))
                    .execute();

            List<Product> first = page(database, null, 2);
            assertEquals(List.of(
                    new Product(10, "Eraser", new BigDecimal("2.50")),
                    new Product(20, "Pen", new BigDecimal("2.50"))), first);
            Product cursor = first.get(first.size() - 1);

            // Changes before the cursor must not shift subsequent pages as an offset would.
            database.insertInto(PRODUCTS, ID, LABEL, CATEGORY, PRICE)
                    .values(5, "Paper clip", "stationery", new BigDecimal("1.00"))
                    .values(15, "Sharpener", "stationery", new BigDecimal("2.50"))
                    .values(25, "Cup", "kitchen", new BigDecimal("2.50"))
                    .execute();
            assertEquals(1, database.deleteFrom(PRODUCTS).where(ID.eq(cursor.id())).execute());

            // The cursor carries sorting values; the row it described need not still exist.
            List<Product> second = page(database, cursor, 2);
            assertEquals(List.of(
                    new Product(30, "Pencil", new BigDecimal("2.50")),
                    new Product(40, "Notebook", new BigDecimal("4.00"))), second);
            List<Product> last = page(database, second.get(second.size() - 1), 2);
            assertEquals(List.of(new Product(50, "Folder", new BigDecimal("4.00"))), last);
            assertEquals(List.of(), page(database, last.get(0), 2));

            // A fresh browse sees new products that were intentionally behind the old cursor.
            assertEquals(List.of(5, 10, 15, 30, 40, 50), page(database, null, 10).stream()
                    .map(Product::id).toList());
        }
    }

    private static List<Product> page(DSLContext database, Product cursor, int pageSize) {
        SelectSeekStep2<Record3<Integer, String, BigDecimal>, BigDecimal, Integer> sorted = database
                .select(ID, LABEL, PRICE)
                .from(PRODUCTS)
                .where(CATEGORY.eq("stationery"))
                .orderBy(PRICE.asc(), ID.asc());
        ResultQuery<Record3<Integer, String, BigDecimal>> query = cursor == null
                ? sorted.limit(pageSize) : sorted.seekAfter(cursor.price(), cursor.id()).limit(pageSize);
        return query.fetch(Records.mapping(Product::new));
    }

    private record Product(int id, String label, BigDecimal price) {
    }
}
