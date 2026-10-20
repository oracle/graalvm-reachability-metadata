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
import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.DirectoryResourceAccessor;
import liquibase.resource.ResourceAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises custom task changes to backfill tenant-qualified login keys using application-specific Java logic.
@Timeout(60)
public class CustomAccountBackfillTest {
    @TempDir
    Path changelogDirectory;

    @Test
    public void runsAConfiguredApplicationBackfillOnceDuringASchemaUpgrade() throws Exception {
        Files.writeString(changelogDirectory.resolve("account-keys.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                    <changeSet id="backfill-login-keys" author="identity-service">
                        <addColumn tableName="member_account">
                            <column name="login_key" type="varchar(250)"/>
                        </addColumn>
                        <customChange
                            class="org_liquibase.liquibase_core.CustomAccountBackfillTest$AccountKeyBackfill">
                            <param name="tenant" value="north"/>
                        </customChange>
                        <addNotNullConstraint tableName="member_account" columnName="login_key"
                            columnDataType="varchar(250)"/>
                        <addUniqueConstraint tableName="member_account" columnNames="login_key"
                            constraintName="member_login_key_unique"/>
                    </changeSet>
                </databaseChangeLog>
                """);

        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:custom_account_backfill", "sa", "");
             DirectoryResourceAccessor resources = new DirectoryResourceAccessor(changelogDirectory);
             Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE member_account (id INT PRIMARY KEY, email VARCHAR(200) NOT NULL)");
            sql.execute("INSERT INTO member_account VALUES (1, ' Alice@Example.COM '), (2, 'BOB@Example.com')");
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase("account-keys.xml", resources, database)) {
                Contexts contexts = new Contexts();
                LabelExpression labels = new LabelExpression();
                liquibase.update(contexts, labels);
                Map<Integer, String> expectedKeys = Map.of(
                        1, "north:alice@example.com",
                        2, "north:bob@example.com");
                assertEquals(expectedKeys, readLoginKeys(connection),
                        "The configured custom task must normalize legacy addresses and qualify them by tenant");
                assertTrue(liquibase.listUnrunChangeSets(contexts, labels).isEmpty());

                // Contact details can change later; the migrated login identifier must remain stable.
                assertEquals(1, sql.executeUpdate("UPDATE member_account SET email = 'new@example.com' WHERE id = 1"));
                connection.commit();
                liquibase.update(contexts, labels);
                assertEquals(expectedKeys, readLoginKeys(connection),
                        "Redeployment must not rerun the custom backfill over a member's later profile edits");
            }
        }
    }

    private static Map<Integer, String> readLoginKeys(Connection connection) throws SQLException {
        Map<Integer, String> keys = new TreeMap<>();
        try (Statement sql = connection.createStatement();
             ResultSet rows = sql.executeQuery("SELECT id, login_key FROM member_account")) {
            while (rows.next()) {
                keys.put(rows.getInt("id"), rows.getString("login_key"));
            }
        }
        return keys;
    }

    /// Application-owned migration loaded and configured through Liquibase's customChange extension API.
    public static class AccountKeyBackfill implements CustomTaskChange {
        private String tenant;

        public void setTenant(String tenant) {
            this.tenant = tenant;
        }

        @Override
        public void execute(Database database) throws CustomChangeException {
            Connection connection = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
            try (Statement sql = connection.createStatement();
                 ResultSet rows = sql.executeQuery("SELECT id, email FROM member_account");
                 PreparedStatement update = connection.prepareStatement(
                         "UPDATE member_account SET login_key = ? WHERE id = ?")) {
                while (rows.next()) {
                    String email = rows.getString("email").strip().toLowerCase(Locale.ROOT);
                    update.setString(1, tenant + ":" + email);
                    update.setInt(2, rows.getInt("id"));
                    update.addBatch();
                }
                update.executeBatch();
            } catch (SQLException exception) {
                throw new CustomChangeException("Could not backfill member login keys", exception);
            }
        }

        @Override
        public ValidationErrors validate(Database database) {
            ValidationErrors errors = new ValidationErrors();
            if (tenant == null || tenant.isBlank()) {
                errors.addError("A tenant is required for member login keys");
            }
            return errors;
        }

        @Override
        public String getConfirmationMessage() {
            return "Backfilled member login keys for tenant " + tenant;
        }

        @Override
        public void setUp() {
            // This migration needs only the database connection supplied by Liquibase.
        }

        @Override
        public void setFileOpener(ResourceAccessor resources) {
            // No external resources are needed for the account-key transformation.
        }
    }
}
