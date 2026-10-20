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
import liquibase.lockservice.DatabaseChangeLogLock;
import liquibase.lockservice.LockService;
import liquibase.lockservice.LockServiceFactory;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises database changelog locking to coordinate deployment sessions before an application upgrade.
@Timeout(60)
public class DeploymentLockHandoffTest {
    @TempDir
    Path changelogDirectory;

    @Test
    public void defersACompetingDeploymentUntilTheActiveSessionReleasesItsLock() throws Exception {
        Files.writeString(changelogDirectory.resolve("delivery.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                    <changeSet id="enable-delivery-pickup" author="delivery-service">
                        <createTable tableName="delivery_option">
                            <column name="code" type="varchar(30)">
                                <constraints primaryKey="true" nullable="false"/>
                            </column>
                        </createTable>
                        <insert tableName="delivery_option">
                            <column name="code" value="STORE_PICKUP"/>
                        </insert>
                    </changeSet>
                </databaseChangeLog>
                """);

        try (Connection activeConnection = DriverManager.getConnection("jdbc:h2:mem:deployment_lock_handoff", "sa", "");
             Connection queuedConnection = DriverManager.getConnection("jdbc:h2:mem:deployment_lock_handoff", "sa", "");
             Database activeDatabase = DatabaseFactory.getInstance()
                     .findCorrectDatabaseImplementation(new JdbcConnection(activeConnection));
             DirectoryResourceAccessor resources = new DirectoryResourceAccessor(changelogDirectory)) {
            Database queuedDatabase = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(queuedConnection));
            LockServiceFactory locks = LockServiceFactory.getInstance();
            LockService activeSession = locks.getLockService(activeDatabase);
            LockService queuedSession = locks.getLockService(queuedDatabase);

            assertTrue(activeSession.acquireLock(), "The active deployment must reserve the shared database");
            try {
                queuedSession.init();
                DatabaseChangeLogLock[] heldLocks = queuedSession.listLocks();
                assertEquals(1, heldLocks.length, "The queued deployment must see the active deployment's lock");
                assertNotNull(heldLocks[0].getLockedBy(), "Operators must be able to identify the lock owner");
                assertFalse(heldLocks[0].getLockedBy().isBlank());
                assertFalse(queuedSession.acquireLock(),
                        "A competing deployment must defer rather than acquire an already held lock");
                try (ResultSet tables = queuedConnection.getMetaData()
                        .getTables(null, "PUBLIC", "DELIVERY_OPTION", null)) {
                    assertFalse(tables.next(), "The deferred deployment has not changed the application schema");
                }
            } finally {
                activeSession.releaseLock();
            }

            assertEquals(0, queuedSession.listLocks().length, "Releasing the lock must be visible across sessions");
            try (Liquibase deployment = new Liquibase("delivery.xml", resources, queuedDatabase)) {
                deployment.update(new Contexts(), new LabelExpression());
                try (Statement sql = queuedConnection.createStatement();
                     ResultSet rows = sql.executeQuery("SELECT code FROM delivery_option")) {
                    assertTrue(rows.next(), "The deferred upgrade can now provision the pickup delivery option");
                    assertEquals("STORE_PICKUP", rows.getString("code"));
                    assertFalse(rows.next());
                }
                assertEquals(0, queuedSession.listLocks().length,
                        "A completed upgrade must leave the database available to the next deployment");
            }
        }
    }
}
