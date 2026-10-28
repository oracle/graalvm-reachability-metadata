/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jooq.jooq;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Loader;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises jOOQ's CSV loader subsystem to import a supplier catalog with rejected-row diagnostics.
 */
public class CatalogCsvImportTest {
    private static final Table<Record> PRODUCTS = DSL.table(DSL.name("catalog_product"));
    private static final Field<Integer> PRODUCT_ID = DSL.field(DSL.name("product_id"), Integer.class);
    private static final Field<String> LABEL = DSL.field(DSL.name("label"), String.class);
    private static final Field<BigDecimal> PRICE = DSL.field(DSL.name("price"), BigDecimal.class);
    private static final Field<String> CATEGORY = DSL.field(DSL.name("category"), String.class);

    @Test
    @Timeout(60)
    public void importsValidProductsAndReportsDuplicateSupplierRows() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:catalog_csv_import", "sa", "")) {
            DSLContext database = DSL.using(connection, SQLDialect.H2);
            database.createTable(PRODUCTS)
                    .column(PRODUCT_ID, SQLDataType.INTEGER.nullable(false))
                    .column(LABEL, SQLDataType.VARCHAR(80).nullable(false))
                    .column(PRICE, SQLDataType.DECIMAL(10, 2).nullable(false))
                    .column(CATEGORY, SQLDataType.VARCHAR(40))
                    .primaryKey(PRODUCT_ID)
                    .execute();

            String supplierCatalog = """
                    id,label,price,category
                    101,"Notebook, ruled",4.50,stationery
                    102,"Desk ""organiser""\",12.00,NULL
                    101,Duplicate listing,99.00,discard
                    103,Pen refill,1.25,stationery
                    """;

            // Keep loading after a rejected row so a supplier can correct just the reported entries.
            Loader<Record> imported = database.loadInto(PRODUCTS)
                    .onDuplicateKeyError()
                    .onErrorIgnore()
                    .loadCSV(supplierCatalog)
                    .fields(PRODUCT_ID, LABEL, PRICE, CATEGORY)
                    .ignoreRows(1)
                    .nullString("NULL")
                    .execute();

            assertEquals(4, imported.processed());
            assertEquals(3, imported.stored());
            assertEquals(1, imported.ignored());
            assertEquals(1, imported.errors().size());
            assertArrayEquals(new String[]{"101", "Duplicate listing", "99.00", "discard"},
                    imported.errors().get(0).row());

            List<Product> catalog = database.select(PRODUCT_ID, LABEL, PRICE, CATEGORY)
                    .from(PRODUCTS)
                    .orderBy(PRODUCT_ID)
                    .fetch(Records.mapping(Product::new));
            assertEquals(List.of(
                    new Product(101, "Notebook, ruled", new BigDecimal("4.50"), "stationery"),
                    new Product(102, "Desk \"organiser\"", new BigDecimal("12.00"), null),
                    new Product(103, "Pen refill", new BigDecimal("1.25"), "stationery")), catalog);
        }
    }

    private record Product(int id, String label, BigDecimal price, String category) {
    }
}
