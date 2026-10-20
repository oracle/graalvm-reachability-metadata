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
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises context and label filtering to stage production feature migrations without development data.
@Timeout(60)
public class EnvironmentFeatureRolloutTest {
    @TempDir
    Path changelogDirectory;

    @Test
    public void selectsProductionChangesAndEnablesAPremiumFeatureOnALaterDeployment() throws Exception {
        Files.writeString(changelogDirectory.resolve("settings.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                    <changeSet id="create-settings" author="deployment-service">
                        <createTable tableName="application_setting">
                            <column name="setting_key" type="varchar(80)">
                                <constraints primaryKey="true" nullable="false"/>
                            </column>
                            <column name="setting_value" type="varchar(200)">
                                <constraints nullable="false"/>
                            </column>
                        </createTable>
                    </changeSet>
                    <changeSet id="production-payments" author="deployment-service"
                        context="production" labels="base">
                        <insert tableName="application_setting">
                            <column name="setting_key" value="payment.gateway"/>
                            <column name="setting_value" value="live"/>
                        </insert>
                    </changeSet>
                    <changeSet id="development-payments" author="deployment-service"
                        context="development" labels="base">
                        <insert tableName="application_setting">
                            <column name="setting_key" value="payment.gateway"/>
                            <column name="setting_value" value="sandbox"/>
                        </insert>
                    </changeSet>
                    <changeSet id="development-fixtures" author="deployment-service"
                        context="development" labels="premium">
                        <insert tableName="application_setting">
                            <column name="setting_key" value="demo.account"/>
                            <column name="setting_value" value="demo-customer"/>
                        </insert>
                    </changeSet>
                    <changeSet id="premium-reporting" author="deployment-service"
                        context="production" labels="premium">
                        <insert tableName="application_setting">
                            <column name="setting_key" value="reporting.mode"/>
                            <column name="setting_value" value="advanced"/>
                        </insert>
                    </changeSet>
                </databaseChangeLog>
                """);

        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:environment_feature_rollout", "sa", "");
             DirectoryResourceAccessor resources = new DirectoryResourceAccessor(changelogDirectory)) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase("settings.xml", resources, database)) {
                Contexts production = new Contexts("production");
                liquibase.update(production, new LabelExpression("base"));
                assertEquals(Map.of("payment.gateway", "live"), readSettings(connection),
                        "The base deployment must exclude premium features and development settings");

                LabelExpression premiumRollout = new LabelExpression("base or premium");
                liquibase.update(production, premiumRollout);
                Map<String, String> expected = Map.of(
                        "payment.gateway", "live",
                        "reporting.mode", "advanced");
                assertEquals(expected, readSettings(connection),
                        "A later rollout must apply the previously filtered premium migration, but not demo data");

                liquibase.update(production, premiumRollout);
                assertEquals(expected, readSettings(connection),
                        "Restarting with the same deployment selection must not reinsert existing settings");
            }
        }
    }

    private static Map<String, String> readSettings(Connection connection) throws Exception {
        Map<String, String> settings = new TreeMap<>();
        try (Statement sql = connection.createStatement();
             ResultSet rows = sql.executeQuery("SELECT setting_key, setting_value FROM application_setting")) {
            while (rows.next()) {
                settings.put(rows.getString("setting_key"), rows.getString("setting_value"));
            }
        }
        return settings;
    }
}
