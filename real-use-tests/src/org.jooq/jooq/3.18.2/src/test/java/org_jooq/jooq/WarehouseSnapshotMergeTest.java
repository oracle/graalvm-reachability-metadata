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
import org.jooq.Records;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises jOOQ's SQL MERGE subsystem to reconcile versioned warehouse stock snapshots.
 */
public class WarehouseSnapshotMergeTest {
    private static final Table<Record> STOCK = DSL.table(DSL.name("snapshot_stock"));
    private static final Field<String> WAREHOUSE = DSL.field(DSL.name("warehouse"), String.class);
    private static final Field<String> SKU = DSL.field(DSL.name("sku"), String.class);
    private static final Field<Integer> QUANTITY = DSL.field(DSL.name("quantity"), Integer.class);
    private static final Field<Integer> REVISION = DSL.field(DSL.name("revision"), Integer.class);

    @Test
    @Timeout(60)
    public void mergesNewerStockWithoutOverwritingNewerRevisionsOrOtherWarehouses() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:warehouse_snapshot_merge", "sa", "")) {
            DSLContext database = DSL.using(connection, SQLDialect.H2);
            database.createTable(STOCK)
                    .column(WAREHOUSE, SQLDataType.VARCHAR(40).nullable(false))
                    .column(SKU, SQLDataType.VARCHAR(40).nullable(false))
                    .column(QUANTITY, SQLDataType.INTEGER.nullable(false))
                    .column(REVISION, SQLDataType.INTEGER.nullable(false))
                    .primaryKey(WAREHOUSE, SKU)
                    .execute();
            database.insertInto(STOCK, WAREHOUSE, SKU, QUANTITY, REVISION)
                    .values("north", "notebook", 8, 2)
                    .values("north", "pen", 9, 4)
                    .values("north", "binder", 5, 1)
                    .values("south", "notebook", 20, 1)
                    .execute();

            // The warehouse feed is a partial snapshot: absent SKUs must remain in stock.
            Table<?> snapshot = DSL.values(
                    DSL.row("north", "notebook", 12, 3),
                    DSL.row("north", "pen", 100, 3),
                    DSL.row("north", "folder", 6, 3))
                    .as("incoming", "warehouse", "sku", "quantity", "revision");

            assertEquals(2, mergeSnapshot(database, snapshot));
            List<StockLevel> expected = List.of(
                    new StockLevel("north", "binder", 5, 1),
                    new StockLevel("north", "folder", 6, 3),
                    new StockLevel("north", "notebook", 12, 3),
                    new StockLevel("north", "pen", 9, 4),
                    new StockLevel("south", "notebook", 20, 1));
            assertEquals(expected, stock(database));

            // Re-delivery of the same feed inserts no duplicates and changes no equal revisions.
            assertEquals(0, mergeSnapshot(database, snapshot));
            assertEquals(expected, stock(database));

            // Zero is a real stock level, not an absent value: a later revision can sell out a SKU.
            Table<?> correction = DSL.values(DSL.row("north", "notebook", 0, 5))
                    .as("incoming", "warehouse", "sku", "quantity", "revision");
            assertEquals(1, mergeSnapshot(database, correction));
            assertEquals(List.of(
                    new StockLevel("north", "binder", 5, 1),
                    new StockLevel("north", "folder", 6, 3),
                    new StockLevel("north", "notebook", 0, 5),
                    new StockLevel("north", "pen", 9, 4),
                    new StockLevel("south", "notebook", 20, 1)), stock(database));
        }
    }

    private static int mergeSnapshot(DSLContext database, Table<?> snapshot) {
        Field<String> sourceWarehouse = snapshot.field("warehouse", String.class);
        Field<String> sourceSku = snapshot.field("sku", String.class);
        Field<Integer> sourceQuantity = snapshot.field("quantity", Integer.class);
        Field<Integer> sourceRevision = snapshot.field("revision", Integer.class);
        Field<String> targetWarehouse = DSL.field(DSL.name("target", "warehouse"), String.class);
        Field<String> targetSku = DSL.field(DSL.name("target", "sku"), String.class);
        Field<Integer> targetRevision = DSL.field(DSL.name("target", "revision"), Integer.class);

        return database.mergeInto(STOCK.as("target"))
                .using(snapshot)
                .on(targetWarehouse.eq(sourceWarehouse).and(targetSku.eq(sourceSku)))
                .whenMatchedAnd(sourceRevision.gt(targetRevision))
                .thenUpdate()
                .set(QUANTITY, sourceQuantity)
                .set(REVISION, sourceRevision)
                .whenNotMatchedThenInsert(WAREHOUSE, SKU, QUANTITY, REVISION)
                .values(sourceWarehouse, sourceSku, sourceQuantity, sourceRevision)
                .execute();
    }

    private static List<StockLevel> stock(DSLContext database) {
        return database.select(WAREHOUSE, SKU, QUANTITY, REVISION).from(STOCK)
                .orderBy(WAREHOUSE, SKU).fetch(Records.mapping(StockLevel::new));
    }

    private record StockLevel(String warehouse, String sku, int quantity, int revision) {
    }
}
