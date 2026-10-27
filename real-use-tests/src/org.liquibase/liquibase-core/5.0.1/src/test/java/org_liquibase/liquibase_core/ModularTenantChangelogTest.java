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

/// Exercises modular changelog discovery and parameter substitution to provision tenant-specific application tables.
@Timeout(60)
public class ModularTenantChangelogTest {
    @TempDir
    Path changelogDirectory;

    @Test
    public void discoversOrderedReleaseFilesAndResolvesEachTenantsDeploymentParameters() throws Exception {
        Path databaseDirectory = Files.createDirectory(changelogDirectory.resolve("db"));
        Path releases = Files.createDirectory(databaseDirectory.resolve("releases"));
        Files.writeString(databaseDirectory.resolve("root.xml"), changelog("""
                <includeAll path="releases" relativeToChangelogFile="true"/>
                """));

        // Write the dependent migration first: filenames, not filesystem creation order, define deployment order.
        Files.writeString(releases.resolve("020-subscription.xml"), changelog("""
                <changeSet id="subscription" author="billing-service">
                    <createTable tableName="${tenantPrefix}subscription">
                        <column name="account_id" type="int">
                            <constraints primaryKey="true" nullable="false"/>
                        </column>
                        <column name="plan" type="varchar(30)">
                            <constraints nullable="false"/>
                        </column>
                    </createTable>
                    <addForeignKeyConstraint baseTableName="${tenantPrefix}subscription"
                        baseColumnNames="account_id" referencedTableName="${tenantPrefix}account"
                        referencedColumnNames="id" constraintName="${tenantPrefix}subscription_account_fk"/>
                    <insert tableName="${tenantPrefix}subscription">
                        <column name="account_id" valueNumeric="7"/>
                        <column name="plan" value="STANDARD"/>
                    </insert>
                </changeSet>
                """));
        Files.writeString(releases.resolve("010-account.xml"), changelog("""
                <changeSet id="account" author="account-service">
                    <createTable tableName="${tenantPrefix}account">
                        <column name="id" type="int">
                            <constraints primaryKey="true" nullable="false"/>
                        </column>
                        <column name="display_name" type="varchar(100)">
                            <constraints nullable="false"/>
                        </column>
                    </createTable>
                    <insert tableName="${tenantPrefix}account">
                        <column name="id" valueNumeric="7"/>
                        <column name="display_name" value="${tenantName}"/>
                    </insert>
                </changeSet>
                """));

        deployTenant("north_", "Northern Books");
        deployTenant("south_", "Southern Coffee");
    }

    private void deployTenant(String tablePrefix, String tenantName) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:modular_" + tablePrefix, "sa", "");
             DirectoryResourceAccessor resources = new DirectoryResourceAccessor(changelogDirectory)) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase("db/root.xml", resources, database)) {
                liquibase.setChangeLogParameter("tenantPrefix", tablePrefix);
                liquibase.setChangeLogParameter("tenantName", tenantName);
                Contexts contexts = new Contexts();
                LabelExpression labels = new LabelExpression();
                liquibase.update(contexts, labels);
                assertTenantSubscription(connection, tablePrefix, tenantName);
                assertTrue(liquibase.listUnrunChangeSets(contexts, labels).isEmpty(),
                        "Every discovered release file must be applied");

                liquibase.update(contexts, labels);
                assertTenantSubscription(connection, tablePrefix, tenantName);
            }
        }
    }

    private static void assertTenantSubscription(Connection connection, String tablePrefix, String tenantName)
            throws Exception {
        String query = "SELECT a.id, a.display_name, s.plan FROM " + tablePrefix + "account a JOIN "
                + tablePrefix + "subscription s ON s.account_id = a.id";
        try (Statement sql = connection.createStatement();
             ResultSet rows = sql.executeQuery(query)) {
            assertTrue(rows.next(), "The account and billing modules must deploy together");
            assertEquals(7, rows.getInt("id"));
            assertEquals(tenantName, rows.getString("display_name"),
                    "Parameters must be resolved independently for each tenant deployment");
            assertEquals("STANDARD", rows.getString("plan"));
            assertFalse(rows.next(), "Repeated deployment must not duplicate the tenant's subscription");
        }
    }

    private static String changelog(String body) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                """ + body + "</databaseChangeLog>\n";
    }
}
