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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises transactional data migrations to import legacy payment receipts atomically and retry after a conflict.
@Timeout(60)
public class TransactionalReceiptMigrationTest {
    @TempDir
    Path changelogDirectory;

    @Test
    public void rollsBackAllReceiptChangesOnConflictAndRetriesAfterTheIdentifierIsRepaired() throws Exception {
        Files.writeString(changelogDirectory.resolve("receipts.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                    <changeSet id="import-legacy-receipts" author="payment-service" runInTransaction="true">
                        <update tableName="invoice">
                            <column name="status" value="PAID"/>
                            <where>status = 'LEGACY_PAID'</where>
                        </update>
                        <insert tableName="payment_receipt">
                            <column name="receipt_id" valueNumeric="11"/>
                            <column name="invoice_id" valueNumeric="101"/>
                        </insert>
                        <insert tableName="payment_receipt">
                            <column name="receipt_id" valueNumeric="12"/>
                            <column name="invoice_id" valueNumeric="102"/>
                        </insert>
                    </changeSet>
                </databaseChangeLog>
                """);

        // Keep the application database alive while independent deployment sessions succeed or fail.
        try (Connection application = DriverManager.getConnection("jdbc:h2:mem:transactional_receipts", "sa", "");
             Statement sql = application.createStatement()) {
            sql.execute("CREATE TABLE invoice (id INT PRIMARY KEY, status VARCHAR(20) NOT NULL)");
            sql.execute("""
                    CREATE TABLE payment_receipt (
                        receipt_id INT PRIMARY KEY,
                        invoice_id INT NOT NULL UNIQUE REFERENCES invoice(id)
                    )
                    """);
            sql.execute("INSERT INTO invoice VALUES (101, 'LEGACY_PAID'), (102, 'LEGACY_PAID'), (999, 'PAID')");
            sql.execute("INSERT INTO payment_receipt VALUES (12, 999)");

            assertThrows(LiquibaseException.class, this::deployReceipts,
                    "An existing receipt identifier must reject the import rather than overwrite a payment");
            assertEquals(Map.of(101, "LEGACY_PAID", 102, "LEGACY_PAID", 999, "PAID"), readInvoices(application),
                    "Failure in the last insert must roll back the earlier invoice status updates");
            assertEquals(Map.of(12, 999), readReceipts(application),
                    "The first receipt insert must also roll back, preserving the pre-existing receipt");

            // An operator moves the conflicting receipt out of the legacy identifier range without losing it.
            assertEquals(1, sql.executeUpdate("UPDATE payment_receipt SET receipt_id = 99 WHERE receipt_id = 12"));
            deployReceipts();
            Map<Integer, String> expectedInvoices = Map.of(101, "PAID", 102, "PAID", 999, "PAID");
            Map<Integer, Integer> expectedReceipts = Map.of(11, 101, 12, 102, 99, 999);
            assertEquals(expectedInvoices, readInvoices(application),
                    "A failed changeset must remain eligible for a complete retry");
            assertEquals(expectedReceipts, readReceipts(application),
                    "Retry must import both legacy receipts and preserve the repaired existing payment");

            deployReceipts();
            assertEquals(expectedInvoices, readInvoices(application));
            assertEquals(expectedReceipts, readReceipts(application),
                    "The successful retry must be recorded so later startups do not repeat the import");
        }
    }

    private void deployReceipts() throws Exception {
        try (Connection deployment = DriverManager.getConnection("jdbc:h2:mem:transactional_receipts", "sa", "");
             DirectoryResourceAccessor resources = new DirectoryResourceAccessor(changelogDirectory)) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(deployment));
            try (Liquibase liquibase = new Liquibase("receipts.xml", resources, database)) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
        }
    }

    private static Map<Integer, String> readInvoices(Connection application) throws SQLException {
        Map<Integer, String> invoices = new TreeMap<>();
        try (Statement sql = application.createStatement();
             ResultSet rows = sql.executeQuery("SELECT id, status FROM invoice")) {
            while (rows.next()) {
                invoices.put(rows.getInt("id"), rows.getString("status"));
            }
        }
        return invoices;
    }

    private static Map<Integer, Integer> readReceipts(Connection application) throws SQLException {
        Map<Integer, Integer> receipts = new TreeMap<>();
        try (Statement sql = application.createStatement();
             ResultSet rows = sql.executeQuery("SELECT receipt_id, invoice_id FROM payment_receipt")) {
            while (rows.next()) {
                receipts.put(rows.getInt("receipt_id"), rows.getInt("invoice_id"));
            }
        }
        return receipts;
    }
}
