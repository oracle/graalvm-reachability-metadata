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
import liquibase.exception.CommandExecutionException;
import liquibase.exception.LiquibaseException;
import liquibase.exception.ValidationFailedException;
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
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises changelog checksum validation to prevent deployments from rewriting applied migration history.
@Timeout(60)
public class ChangelogChecksumValidationTest {
    @TempDir
    Path changelogDirectory;

    @Test
    public void rejectsAnEditedMigrationAndAcceptsAnAppendOnlyRelease() throws Exception {
        String original = """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                    <changeSet id="initial-shipping-service" author="shipping-service">
                        <createTable tableName="shipping_service">
                            <column name="code" type="varchar(30)">
                                <constraints primaryKey="true" nullable="false"/>
                            </column>
                            <column name="delivery_days" type="int">
                                <constraints nullable="false"/>
                            </column>
                        </createTable>
                        <insert tableName="shipping_service">
                            <column name="code" value="STANDARD"/>
                            <column name="delivery_days" valueNumeric="5"/>
                        </insert>
                    </changeSet>
                </databaseChangeLog>
                """;
        String nextRelease = original.replace("</databaseChangeLog>", """
                    <changeSet id="add-express-shipping" author="shipping-service">
                        <insert tableName="shipping_service">
                            <column name="code" value="EXPRESS"/>
                            <column name="delivery_days" valueNumeric="1"/>
                        </insert>
                    </changeSet>
                </databaseChangeLog>
                """);
        Path changelog = changelogDirectory.resolve("shipping.xml");
        Files.writeString(changelog, original);

        // This application connection keeps the in-memory database alive across deployment sessions.
        try (Connection application = DriverManager.getConnection("jdbc:h2:mem:checksum_shipping", "sa", "")) {
            applyChangelog();
            Map<String, Integer> initialServices = Map.of("STANDARD", 5);
            assertEquals(initialServices, readServices(application));

            Files.writeString(changelog, nextRelease.replace("valueNumeric=\"5\"", "valueNumeric=\"9\""));
            try (Connection deployment = DriverManager.getConnection("jdbc:h2:mem:checksum_shipping", "sa", "");
                 DirectoryResourceAccessor resources = new DirectoryResourceAccessor(changelogDirectory)) {
                Database database = DatabaseFactory.getInstance()
                        .findCorrectDatabaseImplementation(new JdbcConnection(deployment));
                try (Liquibase liquibase = new Liquibase("shipping.xml", resources, database)) {
                    CommandExecutionException failure = assertThrows(CommandExecutionException.class,
                            liquibase::validate, "A release must not silently edit an already applied migration");
                    assertInstanceOf(ValidationFailedException.class, failure.getCause());
                    assertThrows(LiquibaseException.class,
                            () -> liquibase.update(new Contexts(), new LabelExpression()));
                }
            }
            assertEquals(initialServices, readServices(application),
                    "Checksum failure must block the pending express-service migration too");

            Files.writeString(changelog, nextRelease);
            applyChangelog();
            Map<String, Integer> releasedServices = Map.of("STANDARD", 5, "EXPRESS", 1);
            assertEquals(releasedServices, readServices(application),
                    "Restoring migration history permits the new release without changing existing service data");
            applyChangelog();
            assertEquals(releasedServices, readServices(application),
                    "The corrected release remains safe to deploy again");
        }
    }

    private void applyChangelog() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:checksum_shipping", "sa", "");
             DirectoryResourceAccessor resources = new DirectoryResourceAccessor(changelogDirectory)) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase("shipping.xml", resources, database)) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
        }
    }

    private static Map<String, Integer> readServices(Connection connection) throws Exception {
        Map<String, Integer> services = new TreeMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT code, delivery_days FROM shipping_service")) {
            while (rows.next()) {
                services.put(rows.getString("code"), rows.getInt("delivery_days"));
            }
        }
        return services;
    }
}
