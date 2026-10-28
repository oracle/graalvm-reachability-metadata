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
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.conf.MappedSchema;
import org.jooq.conf.RenderMapping;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises jOOQ's runtime schema-mapping subsystem to route inventory operations to isolated tenant schemas.
 */
public class TenantInventoryRoutingTest {
    private static final Table<Record> INVENTORY = DSL.table(DSL.name("application", "tenant_stock"));
    private static final Field<String> SKU = DSL.field(DSL.name("sku"), String.class);
    private static final Field<Integer> AVAILABLE = DSL.field(DSL.name("available"), Integer.class);

    @Test
    @Timeout(60)
    public void reusesLogicalInventoryQueriesWithoutCrossingTenantBoundaries() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:tenant_inventory_routing", "sa", "")) {
            DSLContext administrator = DSL.using(connection, SQLDialect.H2);
            provision(administrator, "tenant_north", 10, 4);
            provision(administrator, "tenant_south", 90, 12);

            // Each tenant owns its settings; both use the same logical table and repository methods.
            DSLContext north = tenantDatabase(connection, "tenant_north");
            DSLContext south = tenantDatabase(connection, "tenant_south");
            assertEquals(Map.of("notebook", 10, "pen", 4), stock(north));
            assertEquals(Map.of("notebook", 90, "pen", 12), stock(south));

            assertEquals(1, reserve(north, "notebook", 3));
            assertEquals(Map.of("notebook", 90, "pen", 12), stock(south));
            assertEquals(1, reserve(south, "notebook", 20));
            assertEquals(Map.of("notebook", 7, "pen", 4), stock(north));
            assertEquals(Map.of("notebook", 70, "pen", 12), stock(south));

            // South has enough inventory, but that must not let an oversized north order succeed.
            assertEquals(0, reserve(north, "notebook", 8));
            assertEquals(1, reserve(north, "pen", 4));
            assertEquals(0, reserve(north, "pen", 1));

            // Verify the persisted physical tables independently of the mapped contexts.
            assertEquals(Map.of("notebook", 7, "pen", 0), administrator.select(SKU, AVAILABLE)
                    .from(DSL.table(DSL.name("tenant_north", "tenant_stock")))
                    .fetchMap(SKU, AVAILABLE));
            assertEquals(Map.of("notebook", 70, "pen", 12), administrator.select(SKU, AVAILABLE)
                    .from(DSL.table(DSL.name("tenant_south", "tenant_stock")))
                    .fetchMap(SKU, AVAILABLE));
            assertEquals(Map.of("notebook", 7, "pen", 0), stock(north));
            assertEquals(Map.of("notebook", 70, "pen", 12), stock(south));
        }
    }

    private static DSLContext tenantDatabase(Connection connection, String schema) {
        Settings settings = new Settings().withRenderMapping(new RenderMapping().withSchemata(
                new MappedSchema().withInput("application").withOutput(schema)));
        return DSL.using(connection, SQLDialect.H2, settings);
    }

    private static void provision(DSLContext database, String schema, int notebooks, int pens) {
        Table<Record> physicalStock = DSL.table(DSL.name(schema, "tenant_stock"));
        database.createSchema(schema).execute();
        database.createTable(physicalStock)
                .column(SKU, SQLDataType.VARCHAR(40).nullable(false))
                .column(AVAILABLE, SQLDataType.INTEGER.nullable(false))
                .primaryKey(SKU)
                .execute();
        database.insertInto(physicalStock, SKU, AVAILABLE)
                .values("notebook", notebooks)
                .values("pen", pens)
                .execute();
    }

    private static Map<String, Integer> stock(DSLContext database) {
        return database.select(SKU, AVAILABLE).from(INVENTORY).fetchMap(SKU, AVAILABLE);
    }

    private static int reserve(DSLContext database, String sku, int quantity) {
        return database.update(INVENTORY)
                .set(AVAILABLE, AVAILABLE.sub(quantity))
                .where(SKU.eq(sku).and(AVAILABLE.ge(quantity)))
                .execute();
    }
}
