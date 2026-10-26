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

/// Exercises changelog baseline synchronization to bring an existing customer database under migration management.
@Timeout(60)
public class ExistingDatabaseBaselineTest {
    @TempDir
    Path changelogDirectory;

    @Test
    public void adoptsAnExistingDatabaseWithoutReplayingItsBaselineAndAppliesTheNextRelease() throws Exception {
        String baseline = """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                    <changeSet id="create-customer-profile" author="customer-service">
                        <createTable tableName="customer_profile">
                            <column name="id" type="int">
                                <constraints primaryKey="true" nullable="false"/>
                            </column>
                            <column name="plan" type="varchar(30)">
                                <constraints nullable="false"/>
                            </column>
                        </createTable>
                    </changeSet>
                    <changeSet id="seed-customer-profile" author="customer-service">
                        <insert tableName="customer_profile">
                            <column name="id" valueNumeric="42"/>
                            <column name="plan" value="BASIC"/>
                        </insert>
                    </changeSet>
                </databaseChangeLog>
                """;
        Path changelog = changelogDirectory.resolve("customers.xml");
        Files.writeString(changelog, baseline);

        // A long-running application has already provisioned the schema and upgraded this customer's plan.
        try (Connection application = DriverManager.getConnection("jdbc:h2:mem:existing_customer_baseline", "sa", "");
             Statement sql = application.createStatement()) {
            sql.execute("CREATE TABLE customer_profile (id INT PRIMARY KEY, plan VARCHAR(30) NOT NULL)");
            sql.execute("INSERT INTO customer_profile VALUES (42, 'PREMIUM')");
            try (Connection deployment = DriverManager.getConnection("jdbc:h2:mem:existing_customer_baseline", "sa", "");
                 DirectoryResourceAccessor resources = new DirectoryResourceAccessor(changelogDirectory)) {
                Database database = DatabaseFactory.getInstance()
                        .findCorrectDatabaseImplementation(new JdbcConnection(deployment));
                try (Liquibase liquibase = new Liquibase("customers.xml", resources, database)) {
                    Contexts contexts = new Contexts();
                    LabelExpression labels = new LabelExpression();
                    assertEquals(2, liquibase.listUnrunChangeSets(contexts, labels).size());
                    liquibase.changeLogSync(contexts, labels);
                    assertTrue(liquibase.listUnrunChangeSets(contexts, labels).isEmpty(),
                            "Adopting the baseline must leave no historical migrations pending");
                }
            }
            try (ResultSet rows = sql.executeQuery("SELECT id, plan FROM customer_profile")) {
                assertTrue(rows.next());
                assertEquals(42, rows.getInt("id"));
                assertEquals("PREMIUM", rows.getString("plan"),
                        "Baseline synchronization must preserve the customer's current plan, not replay seed data");
                assertFalse(rows.next());
            }

            Files.writeString(changelog, baseline.replace("</databaseChangeLog>", """
                    <changeSet id="add-email-preference" author="customer-service">
                        <addColumn tableName="customer_profile">
                            <column name="email_opt_in" type="boolean" defaultValueBoolean="false">
                                <constraints nullable="false"/>
                            </column>
                        </addColumn>
                    </changeSet>
                </databaseChangeLog>
                """));
            try (Connection deployment = DriverManager.getConnection("jdbc:h2:mem:existing_customer_baseline", "sa", "");
                 DirectoryResourceAccessor resources = new DirectoryResourceAccessor(changelogDirectory)) {
                Database database = DatabaseFactory.getInstance()
                        .findCorrectDatabaseImplementation(new JdbcConnection(deployment));
                try (Liquibase liquibase = new Liquibase("customers.xml", resources, database)) {
                    Contexts contexts = new Contexts();
                    LabelExpression labels = new LabelExpression();
                    assertEquals(1, liquibase.listUnrunChangeSets(contexts, labels).size(),
                            "Only the post-baseline release should need deployment");
                    liquibase.update(contexts, labels);
                    liquibase.update(contexts, labels);
                    assertTrue(liquibase.listUnrunChangeSets(contexts, labels).isEmpty());
                }
            }
            try (ResultSet rows = sql.executeQuery("SELECT id, plan, email_opt_in FROM customer_profile")) {
                assertTrue(rows.next());
                assertEquals(42, rows.getInt("id"));
                assertEquals("PREMIUM", rows.getString("plan"));
                assertFalse(rows.getBoolean("email_opt_in"),
                        "Existing customers must not be opted into email by a schema upgrade");
                assertFalse(rows.wasNull(), "The new preference must have a concrete default for existing customers");
                assertFalse(rows.next(), "Neither adoption nor repeated deployment may duplicate customer records");
            }
        }
    }
}
