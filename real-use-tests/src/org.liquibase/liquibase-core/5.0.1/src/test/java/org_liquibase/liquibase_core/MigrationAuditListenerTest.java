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
import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.changelog.visitor.AbstractChangeExecListener;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.DirectoryResourceAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises migration execution listeners to emit a deployment audit trail for upgrades and recovery.
@Timeout(60)
public class MigrationAuditListenerTest {
    @TempDir
    Path changelogDirectory;

    @Test
    public void auditsOnlyExecutedMigrationsAndRecordsRecovery() throws Exception {
        Files.writeString(changelogDirectory.resolve("delivery.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                    <changeSet id="delivery-queue" author="delivery-service">
                        <createTable tableName="delivery_queue">
                            <column name="id" type="int">
                                <constraints primaryKey="true" nullable="false"/>
                            </column>
                            <column name="destination" type="varchar(100)"/>
                        </createTable>
                    </changeSet>
                    <changeSet id="delivery-priority" author="delivery-service">
                        <addColumn tableName="delivery_queue">
                            <column name="priority" type="varchar(20)" defaultValue="NORMAL">
                                <constraints nullable="false"/>
                            </column>
                        </addColumn>
                    </changeSet>
                </databaseChangeLog>
                """);

        StringWriter audit = new StringWriter();
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:migration_audit", "sa", "");
             DirectoryResourceAccessor resources = new DirectoryResourceAccessor(changelogDirectory);
             PrintWriter auditOutput = new PrintWriter(audit)) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase("delivery.xml", resources, database);
                 Statement sql = connection.createStatement()) {
                liquibase.setChangeExecListener(new DeploymentAudit(auditOutput));
                Contexts contexts = new Contexts();
                LabelExpression labels = new LabelExpression();
                liquibase.update(contexts, labels);
                List<String> applied = List.of(
                        "APPLIED delivery-queue EXECUTED",
                        "APPLIED delivery-priority EXECUTED");
                assertEquals(applied, audit.toString().lines().toList(),
                        "The deployment audit must identify each successfully applied migration in order");

                sql.executeUpdate("INSERT INTO delivery_queue (id, destination) VALUES (17, 'warehouse-west')");
                connection.commit();
                try (ResultSet rows = sql.executeQuery("SELECT priority FROM delivery_queue WHERE id = 17")) {
                    assertTrue(rows.next());
                    assertEquals("NORMAL", rows.getString("priority"));
                    assertFalse(rows.next());
                }

                liquibase.update(contexts, labels);
                assertEquals(applied, audit.toString().lines().toList(),
                        "Restarting an up-to-date application must not emit duplicate migration audit entries");

                liquibase.rollback(1, contexts, labels);
                assertEquals(List.of(
                        "APPLIED delivery-queue EXECUTED",
                        "APPLIED delivery-priority EXECUTED",
                        "REVERTED delivery-priority"), audit.toString().lines().toList(),
                        "Recovery must append the reverted migration without rewriting the deployment audit");
                try (ResultSet columns = connection.getMetaData()
                        .getColumns(null, "PUBLIC", "DELIVERY_QUEUE", "PRIORITY")) {
                    assertFalse(columns.next(), "The recovery audit must correspond to the restored schema");
                }
                try (ResultSet rows = sql.executeQuery("SELECT id, destination FROM delivery_queue")) {
                    assertTrue(rows.next());
                    assertEquals(17, rows.getInt("id"));
                    assertEquals("warehouse-west", rows.getString("destination"));
                    assertFalse(rows.next(), "Recovery must preserve the application's queued deliveries");
                }
            }
        }
    }

    /// Application-owned listener writes migration outcomes to the deployment audit sink.
    public static class DeploymentAudit extends AbstractChangeExecListener {
        private final PrintWriter output;

        public DeploymentAudit(PrintWriter output) {
            this.output = output;
        }

        @Override
        public void ran(ChangeSet changeSet, DatabaseChangeLog changeLog, Database database,
                        ChangeSet.ExecType execType) {
            output.println("APPLIED " + changeSet.getId() + " " + execType);
            output.flush();
        }

        @Override
        public void rolledBack(ChangeSet changeSet, DatabaseChangeLog changeLog, Database database) {
            output.println("REVERTED " + changeSet.getId());
            output.flush();
        }
    }
}
