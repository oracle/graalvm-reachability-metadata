/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.querydsl.core.types.Projections;
import com.querydsl.sql.Configuration;
import com.querydsl.sql.H2Templates;
import com.querydsl.sql.RelationalPathBase;
import com.querydsl.sql.SQLQueryFactory;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.StringPath;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises Querydsl's bean projection subsystem to build catalog response objects.
public class BeanProjectionCatalogTest {
    @Test
    @Timeout(60)
    public void projectsCatalogRowsIntoApplicationResponseBeans() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:querydsl_bean_catalog;DB_CLOSE_DELAY=-1", "sa", "")) {
            createCatalogTable(connection);
            insertCatalogItems(connection);

            CatalogItems catalogItems = new CatalogItems();
            SQLQueryFactory queryFactory = new SQLQueryFactory(
                    new Configuration(new H2Templates()), () -> connection);
            List<CatalogItem> availableItems = queryFactory
                    .select(Projections.bean(
                            CatalogItem.class,
                            catalogItems.sku.as("sku"),
                            catalogItems.name.as("name"),
                            catalogItems.quantity.as("quantity")))
                    .from(catalogItems)
                    .where(catalogItems.quantity.gt(0))
                    .orderBy(catalogItems.sku.asc())
                    .fetch();

            assertEquals(2, availableItems.size());
            assertEquals("A-100", availableItems.get(0).getSku());
            assertEquals("Desk lamp", availableItems.get(0).getName());
            assertEquals(8, availableItems.get(0).getQuantity());
            assertEquals("C-300", availableItems.get(1).getSku());
            assertEquals("Notebook", availableItems.get(1).getName());
            assertEquals(3, availableItems.get(1).getQuantity());
        }
    }

    private static void createCatalogTable(Connection connection) throws SQLException {
        connection.createStatement().executeUpdate("""
                CREATE TABLE catalog_items (
                    sku VARCHAR(20) PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    quantity INTEGER NOT NULL
                )
                """);
    }

    private static void insertCatalogItems(Connection connection) throws SQLException {
        connection.createStatement().executeUpdate("""
                INSERT INTO catalog_items (sku, name, quantity) VALUES
                    ('A-100', 'Desk lamp', 8),
                    ('B-200', 'Empty binder', 0),
                    ('C-300', 'Notebook', 3)
                """);
    }

    private static final class CatalogItems extends RelationalPathBase<CatalogItem> {
        private final StringPath sku = createString("sku");
        private final StringPath name = createString("name");
        private final NumberPath<Integer> quantity = createNumber("quantity", Integer.class);

        private CatalogItems() {
            super(CatalogItem.class, "catalogItem", null, "catalog_items");
        }
    }

    public static final class CatalogItem {
        private String sku;
        private String name;
        private int quantity;

        public CatalogItem() {
        }

        public String getSku() {
            return sku;
        }

        public void setSku(String sku) {
            this.sku = sku;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }
}
