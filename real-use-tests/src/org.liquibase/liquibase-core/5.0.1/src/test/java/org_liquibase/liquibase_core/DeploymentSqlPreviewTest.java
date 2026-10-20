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
import org.h2.tools.RunScript;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises SQL deployment-script generation for a release reviewed and applied by a database administrator.
@Timeout(60)
public class DeploymentSqlPreviewTest {
    @TempDir
    Path changelogDirectory;

    @Test
    public void previewsAReleaseWithoutApplyingItAndTracksTheManuallyAppliedScript() throws Exception {
        Files.writeString(changelogDirectory.resolve("notifications.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                    <changeSet id="create-notification-channel" author="notification-service">
                        <createTable tableName="notification_channel">
                            <column name="channel_code" type="varchar(40)">
                                <constraints primaryKey="true" nullable="false"/>
                            </column>
                            <column name="enabled" type="boolean">
                                <constraints nullable="false"/>
                            </column>
                        </createTable>
                    </changeSet>
                    <changeSet id="enable-email-notifications" author="notification-service">
                        <insert tableName="notification_channel">
                            <column name="channel_code" value="EMAIL"/>
                            <column name="enabled" valueBoolean="true"/>
                        </insert>
                    </changeSet>
                </databaseChangeLog>
                """);

        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:deployment_sql_preview", "sa", "");
             DirectoryResourceAccessor resources = new DirectoryResourceAccessor(changelogDirectory)) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase("notifications.xml", resources, database)) {
                Contexts contexts = new Contexts();
                LabelExpression labels = new LabelExpression();
                StringWriter reviewArtifact = new StringWriter();
                liquibase.update(contexts, labels, reviewArtifact);
                String deploymentSql = reviewArtifact.toString();
                String normalizedSql = deploymentSql.toUpperCase(Locale.ROOT);
                assertTrue(normalizedSql.contains("CREATE TABLE PUBLIC.NOTIFICATION_CHANNEL"),
                        "The review artifact must contain the application schema change");
                assertTrue(normalizedSql.contains("INSERT INTO PUBLIC.NOTIFICATION_CHANNEL"),
                        "The review artifact must include the release's reference data");
                try (ResultSet tables = connection.getMetaData()
                        .getTables(null, "PUBLIC", "NOTIFICATION_CHANNEL", null)) {
                    assertFalse(tables.next(), "Generating SQL must not deploy the application table");
                }

                // The administrator executes the reviewed artifact rather than running a Liquibase update.
                RunScript.execute(connection, new StringReader(deploymentSql));
                connection.commit();
                assertEmailChannel(connection);
                assertTrue(liquibase.listUnrunChangeSets(contexts, labels).isEmpty(),
                        "The generated script must record the migrations as applied");

                liquibase.update(contexts, labels);
                assertEmailChannel(connection);
            }
        }
    }

    private static void assertEmailChannel(Connection connection) throws Exception {
        try (Statement sql = connection.createStatement();
             ResultSet rows = sql.executeQuery("SELECT channel_code, enabled FROM notification_channel")) {
            assertTrue(rows.next());
            assertEquals("EMAIL", rows.getString("channel_code"));
            assertTrue(rows.getBoolean("enabled"));
            assertFalse(rows.next(), "A later application startup must not insert the email channel again");
        }
    }
}
