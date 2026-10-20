/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.diff.DiffGeneratorFactory;
import liquibase.diff.DiffResult;
import liquibase.diff.compare.CompareControl;
import liquibase.structure.core.Column;
import liquibase.structure.core.Table;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises schema snapshot comparison to detect database drift before an application deployment.
@Timeout(60)
public class SchemaDriftDetectionTest {
    @Test
    public void detectsAnUnapprovedTableAndMissingColumnAndVerifiesTheirRepair() throws Exception {
        try (Connection referenceConnection = DriverManager.getConnection("jdbc:h2:mem:drift_reference", "sa", "");
             Connection deployedConnection = DriverManager.getConnection("jdbc:h2:mem:drift_deployed", "sa", "");
             Statement referenceSql = referenceConnection.createStatement();
             Statement deployedSql = deployedConnection.createStatement();
             Database reference = DatabaseFactory.getInstance()
                     .findCorrectDatabaseImplementation(new JdbcConnection(referenceConnection));
             Database deployed = DatabaseFactory.getInstance()
                     .findCorrectDatabaseImplementation(new JdbcConnection(deployedConnection))) {
            String customerTable = """
                    CREATE TABLE customer (
                        id INT PRIMARY KEY,
                        email VARCHAR(200) NOT NULL
                    )
                    """;
            referenceSql.execute(customerTable);
            deployedSql.execute(customerTable);

            CompareControl comparison = new CompareControl(new HashSet<>(Set.of(Table.class, Column.class)));
            DiffGeneratorFactory diffs = DiffGeneratorFactory.getInstance();
            assertTrue(diffs.compare(reference, deployed, comparison).areEqual(),
                    "A freshly provisioned database should match the approved schema");

            deployedSql.execute("ALTER TABLE customer DROP COLUMN email");
            deployedSql.execute("CREATE TABLE legacy_customer_export (id INT)");

            DiffResult drift = diffs.compare(reference, deployed, comparison);
            assertFalse(drift.areEqual(), "Unapproved schema changes must block deployment");
            Set<String> missingColumns = drift.getMissingObjects(Column.class).stream()
                    .map(column -> column.getRelation().getName() + "." + column.getName())
                    .collect(Collectors.toSet());
            assertEquals(Set.of("CUSTOMER.EMAIL"), missingColumns);
            Set<String> unexpectedTables = drift.getUnexpectedObjects(Table.class).stream()
                    .map(Table::getName)
                    .collect(Collectors.toSet());
            assertEquals(Set.of("LEGACY_CUSTOMER_EXPORT"), unexpectedTables);
            assertTrue(drift.getMissingObjects(Table.class).isEmpty(),
                    "The customer table still exists; only its email column is missing");

            deployedSql.execute("ALTER TABLE customer ADD email VARCHAR(200) NOT NULL");
            deployedSql.execute("DROP TABLE legacy_customer_export");
            assertTrue(diffs.compare(reference, deployed, comparison).areEqual(),
                    "Repairing the reported drift should restore deployment readiness");
        }
    }
}
