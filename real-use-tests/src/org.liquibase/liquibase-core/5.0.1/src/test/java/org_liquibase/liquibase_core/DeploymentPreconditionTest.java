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
import liquibase.exception.LiquibaseException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises changelog preconditions to guard a destructive deployment until background jobs finish.
@Timeout(60)
public class DeploymentPreconditionTest {
    @TempDir
    Path changelogDirectory;

    @Test
    public void blocksColumnRemovalWhileAJobIsRunningAndAllowsRetryAfterCompletion() throws Exception {
        Files.writeString(changelogDirectory.resolve("retire-payload.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                    <changeSet id="retire-legacy-payload" author="export-service">
                        <preConditions onFail="HALT" onError="HALT">
                            <sqlCheck expectedResult="0">
                                SELECT COUNT(*) FROM export_job WHERE status = 'RUNNING'
                            </sqlCheck>
                        </preConditions>
                        <dropColumn tableName="export_job" columnName="legacy_payload"/>
                    </changeSet>
                </databaseChangeLog>
                """);

        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:deployment_precondition", "sa", "");
             DirectoryResourceAccessor resources = new DirectoryResourceAccessor(changelogDirectory);
             Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE export_job (id INT PRIMARY KEY, status VARCHAR(20), legacy_payload VARCHAR(100))");
            sql.execute("INSERT INTO export_job VALUES (42, 'RUNNING', 'customer-export')");
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase("retire-payload.xml", resources, database)) {
                Contexts contexts = new Contexts();
                LabelExpression labels = new LabelExpression();
                assertThrows(LiquibaseException.class, () -> liquibase.update(contexts, labels));
                try (ResultSet rows = sql.executeQuery("SELECT status, legacy_payload FROM export_job WHERE id = 42")) {
                    assertTrue(rows.next(), "A blocked deployment must preserve the running job");
                    assertEquals("RUNNING", rows.getString("status"));
                    assertEquals("customer-export", rows.getString("legacy_payload"));
                    assertFalse(rows.next());
                }
                assertAppliedChangeCount(sql, 0);

                assertEquals(1, sql.executeUpdate("UPDATE export_job SET status = 'COMPLETE' WHERE id = 42"));
                liquibase.update(contexts, labels);
                try (ResultSet columns = connection.getMetaData()
                        .getColumns(null, "PUBLIC", "EXPORT_JOB", "LEGACY_PAYLOAD")) {
                    assertFalse(columns.next(), "The completed job no longer prevents column retirement");
                }
                try (ResultSet rows = sql.executeQuery("SELECT id, status FROM export_job")) {
                    assertTrue(rows.next());
                    assertEquals(42, rows.getInt("id"));
                    assertEquals("COMPLETE", rows.getString("status"));
                    assertFalse(rows.next(), "Retiring the payload must preserve the job record");
                }
                liquibase.update(contexts, labels);
                assertAppliedChangeCount(sql, 1);
            }
        }
    }

    private static void assertAppliedChangeCount(Statement sql, int expected) throws Exception {
        try (ResultSet rows = sql.executeQuery("SELECT COUNT(*) FROM DATABASECHANGELOG")) {
            assertTrue(rows.next());
            assertEquals(expected, rows.getInt(1), "Only a successful migration may be recorded as applied");
        }
    }
}
