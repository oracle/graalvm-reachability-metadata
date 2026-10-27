/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.DirectoryResourceAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises formatted SQL changelog migrations for an inventory application's database upgrades.
@Timeout(60)
public class FormattedSqlInventoryMigrationTest {
    @TempDir
    Path changelogDirectory;

    @Test
    public void upgradesInventoryWithoutDuplicatingSeedDataAndRollsBackTheLatestChange() throws Exception {
        Files.writeString(changelogDirectory.resolve("inventory.sql"), """
                --liquibase formatted sql

                --changeset inventory:create-stock
                CREATE TABLE stock (sku VARCHAR(40) PRIMARY KEY, quantity INT NOT NULL);
                --rollback DROP TABLE stock;

                --changeset inventory:seed-stock
                INSERT INTO stock (sku, quantity) VALUES ('COFFEE-250', 12);
                --rollback DELETE FROM stock WHERE sku = 'COFFEE-250';

                --changeset inventory:add-reorder-level
                ALTER TABLE stock ADD reorder_level INT DEFAULT 5 NOT NULL;
                --rollback ALTER TABLE stock DROP COLUMN reorder_level;
                """);

        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:formatted_inventory", "sa", "");
             DirectoryResourceAccessor resources = new DirectoryResourceAccessor(changelogDirectory)) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase("inventory.sql", resources, database)) {
                Contexts contexts = new Contexts();
                LabelExpression labels = new LabelExpression();
                liquibase.update(contexts, labels);
                assertInventory(connection);

                liquibase.update(contexts, labels);
                assertInventory(connection);

                liquibase.rollback(1, contexts, labels);
                try (Statement statement = connection.createStatement();
                     ResultSet rows = statement.executeQuery("SELECT sku, quantity FROM stock")) {
                    assertTrue(rows.next());
                    assertEquals("COFFEE-250", rows.getString("sku"));
                    assertEquals(12, rows.getInt("quantity"));
                    assertFalse(rows.next());
                }
                try (ResultSet columns = connection.getMetaData()
                        .getColumns(null, "PUBLIC", "STOCK", "REORDER_LEVEL")) {
                    assertFalse(columns.next(), "Rollback must remove the new column without removing stock");
                }
            }
        }
    }

    private static void assertInventory(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT sku, quantity, reorder_level FROM stock")) {
            assertTrue(rows.next());
            assertEquals("COFFEE-250", rows.getString("sku"));
            assertEquals(12, rows.getInt("quantity"));
            assertEquals(5, rows.getInt("reorder_level"));
            assertFalse(rows.next(), "Repeated startup must not insert duplicate seed data");
        }
    }
}
