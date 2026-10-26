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
import org.jooq.ResultQuery;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.conf.Settings;
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
 * Exercises jOOQ's SQL parser subsystem to run a saved PostgreSQL inventory report against an H2 database.
 */
public class SavedInventoryReportTest {
    private static final Table<Record> STOCK = DSL.table(DSL.name("report_stock"));
    private static final Field<String> SKU = DSL.field(DSL.name("sku"), String.class);
    private static final Field<String> LABEL = DSL.field(DSL.name("label"), String.class);
    private static final Field<BigDecimal> PRICE = DSL.field(DSL.name("price"), BigDecimal.class);
    private static final Field<Integer> QUANTITY = DSL.field(DSL.name("quantity"), Integer.class);

    @Test
    @Timeout(60)
    public void translatesSavedSqlAndReusesItWithNewReportParameters() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:saved_inventory_report", "sa", "")) {
            DSLContext database = DSL.using(connection, SQLDialect.H2,
                    new Settings().withParseDialect(SQLDialect.POSTGRES));
            database.createTable(STOCK)
                    .column(SKU, SQLDataType.VARCHAR(40).nullable(false))
                    .column(LABEL, SQLDataType.VARCHAR(80).nullable(false))
                    .column(PRICE, SQLDataType.DECIMAL(10, 2).nullable(false))
                    .column(QUANTITY, SQLDataType.INTEGER.nullable(false))
                    .primaryKey(SKU)
                    .execute();
            database.insertInto(STOCK, SKU, LABEL, PRICE, QUANTITY)
                    .values("N-2", "Pocket NOTEBOOK", new BigDecimal("2.50"), 8)
                    .values("P-1", "Blue Pen", new BigDecimal("1.25"), 12)
                    .values("N-0", "Notebook discontinued", new BigDecimal("9.00"), 0)
                    .values("N-1", "Ruled Notebook", new BigDecimal("4.50"), 3)
                    .values("N-3", "Notebook deluxe", new BigDecimal("15.00"), 1)
                    .execute();

            // Report definitions arrive as SQL, not as Java DSL expressions. Parse the source dialect
            // so PostgreSQL casts and case-insensitive matching are rendered for the connection dialect.
            String savedReport = """
                    select "sku", "label",
                           ("price" * "quantity")::numeric(12, 2) as "inventory_value"
                    from "report_stock"
                    where "label" ilike ? and "quantity" >= ?
                    order by "inventory_value" desc, "sku" asc
                    """;
            ResultQuery<?> report = database.parser().parseResultQuery(savedReport, "%notebook%", 2);

            assertEquals(List.of(
                    new StockValue("N-2", "Pocket NOTEBOOK", new BigDecimal("20.00")),
                    new StockValue("N-1", "Ruled Notebook", new BigDecimal("13.50"))), run(report));

            // Rebinding a saved report must replace the previous search without treating input as SQL.
            report.bind(1, "%pen%");
            report.bind(2, 1);
            assertEquals(List.of(new StockValue("P-1", "Blue Pen", new BigDecimal("15.00"))), run(report));

            report.bind(1, "%notebook%' OR 1=1 --");
            assertEquals(List.of(), run(report));

            report.bind(1, "%notebook%");
            report.bind(2, 8);
            assertEquals(List.of(new StockValue("N-2", "Pocket NOTEBOOK", new BigDecimal("20.00"))), run(report));
        }
    }

    private static List<StockValue> run(ResultQuery<?> report) {
        return report.fetch(row -> new StockValue(row.get("sku", String.class),
                row.get("label", String.class), row.get("inventory_value", BigDecimal.class)));
    }

    private record StockValue(String sku, String label, BigDecimal inventoryValue) {
    }
}
