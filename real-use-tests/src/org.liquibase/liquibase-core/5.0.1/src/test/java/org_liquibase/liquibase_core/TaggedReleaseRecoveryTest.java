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

/// Exercises release tagging to deploy and recover named application releases without losing existing customer data.
@Timeout(60)
public class TaggedReleaseRecoveryTest {
    @TempDir
    Path changelogDirectory;

    @Test
    public void deploysToATagAndRecoversThatReleaseBeforeRetryingTheUpgrade() throws Exception {
        Files.writeString(changelogDirectory.resolve("accounts.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                    <changeSet id="create-account" author="account-service">
                        <createTable tableName="customer_account">
                            <column name="id" type="int">
                                <constraints primaryKey="true" nullable="false"/>
                            </column>
                            <column name="display_name" type="varchar(100)">
                                <constraints nullable="false"/>
                            </column>
                        </createTable>
                    </changeSet>
                    <changeSet id="stable-release" author="account-service">
                        <tagDatabase tag="accounts-stable" keepTagOnRollback="true"/>
                    </changeSet>
                    <changeSet id="account-tier" author="account-service">
                        <addColumn tableName="customer_account">
                            <column name="tier" type="varchar(20)" defaultValue="STANDARD">
                                <constraints nullable="false"/>
                            </column>
                        </addColumn>
                    </changeSet>
                    <changeSet id="account-notes" author="account-service">
                        <createTable tableName="account_note">
                            <column name="id" type="int">
                                <constraints primaryKey="true" nullable="false"/>
                            </column>
                            <column name="body" type="varchar(200)"/>
                        </createTable>
                    </changeSet>
                </databaseChangeLog>
                """);

        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:tagged_release_recovery", "sa", "");
             DirectoryResourceAccessor resources = new DirectoryResourceAccessor(changelogDirectory)) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase("accounts.xml", resources, database);
                 Statement sql = connection.createStatement()) {
                Contexts contexts = new Contexts();
                LabelExpression labels = new LabelExpression();
                liquibase.update("accounts-stable", contexts, labels);
                assertTrue(liquibase.tagExists("accounts-stable"));
                assertEquals(2, liquibase.listUnrunChangeSets(contexts, labels).size(),
                        "A tag-targeted deployment must leave the later release pending");
                assertStableSchema(connection);

                // Customer data is created by the running application, not by a seed migration.
                sql.executeUpdate("INSERT INTO customer_account VALUES (42, 'Northern Books')");
                connection.commit();
                liquibase.update(contexts, labels);
                assertUpgradedAccount(sql);

                liquibase.rollback("accounts-stable", contexts, labels);
                assertTrue(liquibase.tagExists("accounts-stable"), "Recovery must retain the stable release marker");
                assertStableSchema(connection);
                assertEquals(2, liquibase.listUnrunChangeSets(contexts, labels).size(),
                        "Both changes after the tag must be eligible for a later deployment");
                try (ResultSet rows = sql.executeQuery("SELECT id, display_name FROM customer_account")) {
                    assertTrue(rows.next(), "Release recovery must preserve customers created after the tag");
                    assertEquals(42, rows.getInt("id"));
                    assertEquals("Northern Books", rows.getString("display_name"));
                    assertFalse(rows.next());
                }

                liquibase.update(contexts, labels);
                assertUpgradedAccount(sql);
                assertTrue(liquibase.listUnrunChangeSets(contexts, labels).isEmpty(),
                        "The recovered database must support deploying the next release again");
            }
        }
    }

    private static void assertStableSchema(Connection connection) throws Exception {
        try (ResultSet columns = connection.getMetaData()
                .getColumns(null, "PUBLIC", "CUSTOMER_ACCOUNT", "TIER")) {
            assertFalse(columns.next(), "The stable release must not contain the next release's tier column");
        }
        try (ResultSet tables = connection.getMetaData().getTables(null, "PUBLIC", "ACCOUNT_NOTE", null)) {
            assertFalse(tables.next(), "The stable release must not contain the next release's notes table");
        }
    }

    private static void assertUpgradedAccount(Statement sql) throws Exception {
        try (ResultSet rows = sql.executeQuery("SELECT id, display_name, tier FROM customer_account")) {
            assertTrue(rows.next());
            assertEquals(42, rows.getInt("id"));
            assertEquals("Northern Books", rows.getString("display_name"));
            assertEquals("STANDARD", rows.getString("tier"));
            assertFalse(rows.next(), "Retrying a release must not duplicate existing customer accounts");
        }
        try (ResultSet rows = sql.executeQuery("SELECT COUNT(*) FROM account_note")) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1), "The new notes table must be available to the upgraded application");
        }
    }
}
